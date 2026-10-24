package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.DriveBackupDeletionState
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.GarmentTagMappingRecord
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.RobiaSettings
import com.gusanitolabs.robia.core.model.MainColorSyncRecord
import com.gusanitolabs.robia.core.model.SyncTombstoneRecord
import com.gusanitolabs.robia.core.model.TagCategory
import com.gusanitolabs.robia.core.model.TagCategorySyncRecord
import com.gusanitolabs.robia.core.model.TagSyncRecord
import com.gusanitolabs.robia.core.model.WARDROBE_SYNC_SCHEMA_VERSION
import com.gusanitolabs.robia.core.model.WardrobeSnapshotMetadata
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot
import com.gusanitolabs.robia.data.EmbeddingKey
import com.gusanitolabs.robia.data.EmbeddingRepository
import com.gusanitolabs.robia.data.PendingGarmentSyncWork
import com.gusanitolabs.robia.data.PendingMetadataSyncWork
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.data.WardrobeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/**
 * Durable Room/DataStore-backed sync outbox processor.
 *
 * Normal sync is local-first: once the local wardrobe contains user data, Room/DataStore remains
 * the source of truth and the processor uploads the local snapshot. Drive -> phone restore is reserved for
 * an explicit setup/restore request on a fresh local install; connecting Drive on a phone that already has
 * garments proposes a merge first. Work is run by [runOnce] from WorkManager ([SyncTrigger.Background]) or
 * directly after a local change ([SyncTrigger.Foreground]); both share [mutex].
 */
class WardrobeSyncOutboxProcessor(
    private val settingsRepository: SettingsRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val snapshotRepository: WardrobeSnapshotStore,
    private val driveRepository: DriveWardrobeRepository,
    private val restoreSyncLogRepository: RestoreSyncLogRepository = NoOpRestoreSyncLogRepository,
    private val syncWorkScheduler: SyncWorkScheduler = NoOpSyncWorkScheduler,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Vectors waiting for a Drive upload; null only in tests that do not model embeddings. */
    private val embeddingRepository: EmbeddingRepository? = null,
    /** The last measured Drive space; null in tests that do not model it. */
    private val driveUsageStore: DriveUsageStore? = null,
    /** Called after a restore imported the remote garments, so the backfill can fill vectors that the backup lacked. */
    private val onRestoreImported: () -> Unit = {},
    /** Monotonic milliseconds for the request rate guard; wall-clock changes must not stretch a deferral. */
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    /** Waits out a deferred request; injected so tests release it deterministically. */
    private val awaitDeferredRequest: suspend (delayMillis: Long) -> Unit = { delayMillis -> delay(delayMillis) },
) : WardrobeSyncGateway {
    private val mutex = Mutex()
    private val isProcessing = MutableStateFlow(false)
    private val hasGuardedPhotoRestoreIssues = MutableStateFlow(false)
    private val restoreProgress = MutableStateFlow<CloudRestoreProgress?>(null)
    private val lastCycle = MutableStateFlow<SyncCycleSummary?>(null)
    /** Vectors pending when the current cycle exported its snapshot; marked uploaded only after that snapshot is stored. */
    private var cycleExportedEmbeddingKeys: List<EmbeddingKey> = emptyList()
    private val pendingMergeSummary = MutableStateFlow<MergeSummary?>(null)
    /** Remote revision the pending merge proposal was built from; declining it opens the conflict at this revision. */
    private var pendingMergeRemoteRevision = 0L
    private val authBlocked = MutableStateFlow(false)
    private val mutableState = MutableStateFlow(WardrobeSyncState.notConfigured())
    private var scheduledWorkJob: Job? = null
    private var hasAttemptedFreshInstallRestore = false
    private var restoreAttemptCounter = 0
    // Per-cycle outcomes that the cycle result cannot carry. Written and read only while holding [mutex].
    private var cycleStorageFull = false
    private var cyclePhotoUploadFailureIds: Set<String> = emptySet()
    /** The Drive failure that ended this cycle's upload attempt, reported in [lastCycle]. Written and read only while holding [mutex]. */
    private var cycleDriveFailure: Throwable? = null
    /** The reason of the Blocked result that ended the current cycle, for the sync-ended log line. */
    private var cycleBlockedReason: DriveSyncDisabledReason? = null
    /** Local work the phone had before this cycle claimed anything (rows, vectors, cloud deletion). Written and read only while holding [mutex]. */
    private var cyclePendingLocalWork = false
    /** The merge decision this cycle reached, logged with the cycle. Written and read only while holding [mutex]. */
    private var cycleMergeDecision: MergeDecision? = null
    private var lastLoggedSettings: RobiaSettings? = null
    /** Serialises [requestSync]; held only for the decision and the enqueue, never across a cycle. */
    private val syncRequestMutex = Mutex()
    /** [monotonicMillis] when this process last enqueued a WorkManager request; null before the first one. Guarded by [syncRequestMutex]. */
    private var lastSyncRequestAt: Long? = null
    /** The one deferred request that later requests merge into. Guarded by [syncRequestMutex]. */
    private var deferredSyncRequest: Job? = null

    override val state: Flow<WardrobeSyncState> = mutableState
    override val restoreSyncLogText: Flow<String> = restoreSyncLogRepository.text

    init {
        scope.launch(dispatcher) {
            wardrobeRepository.observeGuardedPhotoRestoreCount().collect { count ->
                hasGuardedPhotoRestoreIssues.value = count > 0
            }
        }
        scope.launch(dispatcher) {
            mutex.withLock {
                // Held under the mutex, so no claim of this process is live: a Running row now was left by a killed process.
                // photo_restore_guarded rows are excluded by the query: they have their own recovery below.
                wardrobeRepository.recoverStaleRunningSyncWork(System.currentTimeMillis())
                // Only guarded claims older than the photo retry timeout are dead (a live retry is bounded by that timeout).
                wardrobeRepository.recoverStaleGuardedPhotoSyncWork(System.currentTimeMillis() - PHOTO_RESTORE_RETRY_TIMEOUT_MILLIS)
                // Work still pending from a killed process is handed to WorkManager on app start. A deferred request dies with
                // the process, so pending vectors are re-requested here too. Only a Drive that can run it gets the request.
                val status = settingsRepository.settings.first().driveSyncConnectionStatus
                val hasPendingWork = wardrobeRepository.pendingGarmentSyncWork().isNotEmpty() ||
                    wardrobeRepository.pendingMetadataSyncWork().isNotEmpty() ||
                    embeddingRepository?.pendingUploadKeys()?.isNotEmpty() == true
                if (hasPendingWork && status in INIT_REQUEST_STATUSES) {
                    // A failing init request must not stop the state pipeline below from starting.
                    logRequestFailures("init") { requestSync("init") }
                }
            }
            combine(
                settingsRepository.settings,
                wardrobeRepository.observePendingGarmentSyncCount(),
                wardrobeRepository.observeGarmentSyncAttentionCount(),
                wardrobeRepository.observePendingMetadataSyncCount(),
                wardrobeRepository.observeMetadataSyncAttentionCount(),
                isProcessing,
                hasGuardedPhotoRestoreIssues,
                pendingMergeSummary,
                settingsRepository.syncConflict,
                authBlocked,
                wardrobeRepository.observeGarmentNeedsUserActionCount(DRIVE_FULL_REASON),
                wardrobeRepository.observeGarmentNeedsUserActionCount(PHOTO_UPLOAD_FAILED_REASON),
                lastCycle,
                driveUsageStore?.latest ?: flowOf(null),
            ) { values ->
                val settings = values[0] as RobiaSettings
                val pendingCount = values[1] as Int
                val attentionCount = values[2] as Int
                val metadataPendingCount = values[3] as Int
                val metadataAttentionCount = values[4] as Int
                val processing = values[5] as Boolean
                val photoAttention = values[6] as Boolean
                restoreSyncLogRepository.setEnabled(settings.developerModeEnabled)
                recordSettingsChanges(settings)
                WardrobeSyncStateInputs(
                    authorizedAccountEmail = settings.driveAuthorizedAccountEmail,
                    expectedAccountEmail = settings.driveBoundAccountEmail,
                    connectionStatus = settings.driveSyncConnectionStatus,
                    pendingOperationCount = pendingCount + metadataPendingCount,
                    attentionOperationCount = attentionCount + metadataAttentionCount,
                    isProcessing = processing,
                    needsPhotoRestoreAttention = photoAttention,
                    pendingMergeSummary = values[7] as MergeSummary?,
                    conflict = values[8] as Boolean,
                    authBlocked = values[9] as Boolean,
                    driveFullCount = values[10] as Int,
                    photoUploadFailedCount = values[11] as Int,
                    lastCycle = values[12] as SyncCycleSummary?,
                    driveUsage = values[13] as DriveUsage?,
                )
            }.combine(restoreProgress) { inputs, progress ->
                inputs.connectionStatus.toWardrobeSyncState(inputs, progress)
            }.collect { nextState ->
                mutableState.value = nextState
                scheduleWorkFor(nextState)
            }
        }
    }

    private fun scheduleWorkFor(nextState: WardrobeSyncState) {
        // Periodic safety net only while Drive is connected; cancelled on disconnect or backup deletion.
        when (nextState.connectionStatus) {
            DriveSyncConnectionStatus.Connected,
            DriveSyncConnectionStatus.Syncing,
            DriveSyncConnectionStatus.NeedsAttention -> syncWorkScheduler.ensurePeriodic()
            DriveSyncConnectionStatus.Disconnected,
            DriveSyncConnectionStatus.Disabled,
            DriveSyncConnectionStatus.NotConfigured -> syncWorkScheduler.cancelAll()
        }
        // Ordinary outbox work is driven by WorkManager and explicit requests, never by this flow.
        // Only the one-shot fresh-install restore is launched from here.
        if (nextState.connectionStatus == DriveSyncConnectionStatus.Connected &&
            nextState.pendingOperationCount == 0 &&
            !isProcessing.value
        ) {
            launchSyncWork { restoreFreshInstallOnceIfNeeded() }
        }
    }

    private fun launchSyncWork(block: suspend () -> Unit) {
        if (scheduledWorkJob?.isActive == true) return
        scheduledWorkJob = scope.launch(dispatcher) { block() }
    }

    private fun recordSettingsChanges(settings: RobiaSettings) {
        val previous = lastLoggedSettings
        lastLoggedSettings = settings
        if (previous == null) return
        if (previous.developerModeEnabled != settings.developerModeEnabled) {
            restoreSyncLogRepository.append(
                RestoreSyncLogEvent(
                    correlationId = "settings",
                    category = "settings",
                    phase = null,
                    status = null,
                    message = "developer_mode_enabled=${settings.developerModeEnabled}",
                ),
            )
        }
        if (previous.languagePreference != settings.languagePreference) {
            restoreSyncLogRepository.append(
                RestoreSyncLogEvent(
                    correlationId = "settings",
                    category = "settings",
                    phase = null,
                    status = null,
                    message = "language=${settings.languagePreference.name}",
                ),
            )
        }
        if (previous.driveSyncConnectionStatus != settings.driveSyncConnectionStatus) {
            restoreSyncLogRepository.append(
                RestoreSyncLogEvent(
                    correlationId = "settings",
                    category = "settings",
                    phase = null,
                    status = null,
                    message = "drive_status=${settings.driveSyncConnectionStatus.name}",
                ),
            )
        }
    }

    override suspend fun enqueue(operation: WardrobeSyncOperation) {
        withContext(dispatcher) {
            if (operation is WardrobeSyncOperation.DeleteCloudBackup) {
                // A purge on another account's Drive is never run: the user chooses the account first.
                if (!accountMismatchHeld()) deleteCloudBackup()
                return@withContext
            }
            when (settingsRepository.settings.first().driveSyncConnectionStatus) {
                DriveSyncConnectionStatus.Connected,
                DriveSyncConnectionStatus.Syncing,
                DriveSyncConnectionStatus.NeedsAttention -> when (operation) {
                    is WardrobeSyncOperation.RetryRestoredPhoto -> retryRestoredPhoto(operation.garmentId)
                    is WardrobeSyncOperation.RetryAttention -> retryAttention(operation.kind)
                    else -> {
                        // WorkManager owns backoff and retries; running now keeps the foreground responsive.
                        val reason = "enqueue:${operation::class.simpleName}"
                        val forceSnapshot = operation.affectedGarmentIds().isEmpty()
                        val forceImport = operation is WardrobeSyncOperation.ImportFullSnapshot
                        // A forced operation keeps its background retry even when its inline run fails with nothing pending (H01 B).
                        requestSync(reason, forced = forceSnapshot || forceImport)
                        runCycle(
                            reason = reason,
                            trigger = SyncTrigger.Foreground,
                            forceSnapshot = forceSnapshot,
                            forceImport = forceImport,
                        )
                    }
                }
                // Rows stay Queued (pending). Sign-in and setup needs are shown as attention, not as failures.
                DriveSyncConnectionStatus.Disconnected,
                DriveSyncConnectionStatus.Disabled,
                DriveSyncConnectionStatus.NotConfigured -> Unit
            }
        }
    }

    /** Entry point for WorkManager ([SyncTrigger.Background]) and foreground callers. [reason] is logged with the run. */
    suspend fun runOnce(trigger: SyncTrigger, reason: String = "direct"): SyncRunResult = withContext(dispatcher) {
        retryGuardedPhotos()
        runCycle(reason = reason, trigger = trigger, forceSnapshot = false, forceImport = false)
    }

    /** Embedding backfill entry point. Non-suspending: the request is decided on [scope]. */
    fun requestSyncInBackground(reason: String) {
        scope.launch(dispatcher) { logRequestFailures(reason) { requestSync(reason) } }
    }

    /**
     * The only place that asks WorkManager for a run. Rate guard: [shouldRequestSync] drops a request when nothing is pending,
     * and merges requests inside [MIN_SYNC_REQUEST_INTERVAL_MILLIS] into one deferred request. No real write loses its request.
     */
    private suspend fun requestSync(reason: String, forced: Boolean = false) {
        syncRequestMutex.withLock {
            val now = monotonicMillis()
            val pendingEmbeddings = embeddingRepository?.pendingUploadKeys()?.size ?: 0
            val decision = shouldRequestSync(
                pendingGarments = wardrobeRepository.pendingGarmentSyncWork().size,
                pendingMetadata = wardrobeRepository.pendingMetadataSyncWork().size,
                pendingEmbeddings = pendingEmbeddings,
                lastRequestAt = lastSyncRequestAt,
                now = now,
                minIntervalMs = MIN_SYNC_REQUEST_INTERVAL_MILLIS,
                deferredWaiting = deferredSyncRequest?.isActive == true,
                forced = forced,
            )
            when (decision) {
                SyncRequestDecision.Drop -> logRequest("sync request dropped reason=$reason nothing pending")
                SyncRequestDecision.Enqueue -> enqueueRequest(reason, now)
                SyncRequestDecision.Coalesce -> logRequest("sync request merged reason=$reason into deferred run")
                is SyncRequestDecision.Defer -> deferRequest(reason, decision.delayMillis)
            }
        }
    }

    /** Caller holds [syncRequestMutex]. One deferred request: it starts after the interval and later requests merge into it. */
    private fun deferRequest(reason: String, delayMillis: Long) {
        logRequest("sync request deferred reason=$reason delay_ms=$delayMillis")
        deferredSyncRequest = scope.launch(dispatcher) {
            logRequestFailures("deferred:$reason") {
                awaitDeferredRequest(delayMillis)
                syncRequestMutex.withLock {
                    deferredSyncRequest = null
                    enqueueRequest("deferred:$reason", monotonicMillis())
                }
            }
        }
    }

    /** Runs one request body on [scope]: a failure is logged, because nothing awaits it. Cancellation still propagates. */
    private suspend fun logRequestFailures(reason: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            logRequest("sync request failed reason=$reason error=${throwable.javaClass.simpleName}")
        }
    }

    /** Caller holds [syncRequestMutex]. */
    private fun enqueueRequest(reason: String, now: Long) {
        lastSyncRequestAt = now
        logRequest("sync requested reason=$reason")
        syncWorkScheduler.requestSync(reason)
    }

    private fun logRequest(message: String) {
        restoreSyncLogRepository.append(
            RestoreSyncLogEvent(
                correlationId = "request",
                category = "sync",
                phase = null,
                status = null,
                message = message,
            ),
        )
    }

    /** Guarded photos are retried on every run. Transient download failures do not count toward the attempt cap. */
    private suspend fun retryGuardedPhotos() {
        wardrobeRepository.guardedPhotoRetryCandidateIds().forEach { garmentId -> retryRestoredPhoto(garmentId) }
    }

    override suspend fun clearRestoreSyncLog() {
        withContext(dispatcher) { restoreSyncLogRepository.clear() }
    }

    override suspend fun useBackup() {
        withContext(dispatcher) {
            mutex.withLock { runResolution("use_backup") { useBackupLocked() } }
        }
    }

    override suspend fun keepThisPhone() {
        withContext(dispatcher) {
            val accepted = mutex.withLock { runResolution("keep_this_phone") { acceptRemoteRevisionAsKnown() } }
            if (accepted == SyncRunResult.Success) {
                runCycle(reason = "keep_this_phone", trigger = SyncTrigger.Foreground, forceSnapshot = true, forceImport = false)
            }
        }
    }

    override suspend fun confirmMerge() {
        withContext(dispatcher) {
            runCycle(reason = "merge_confirmed", trigger = SyncTrigger.Foreground, forceSnapshot = true, forceImport = true, mergeConfirmed = true)
        }
    }

    override suspend fun cancelMerge() {
        // The phone keeps its data. A declined merge is persisted as a conflict, so no later run (or restart) uploads over the newer backup.
        pendingMergeSummary.value = null
        settingsRepository.setSyncConflict(true, pendingMergeRemoteRevision)
    }

    override suspend fun keepWithoutPhoto(garmentId: String) {
        withContext(dispatcher) {
            if (settingsRepository.settings.first().driveSyncConnectionStatus !in setOf(
                    DriveSyncConnectionStatus.Connected,
                    DriveSyncConnectionStatus.NeedsAttention,
                )
            ) {
                return@withContext
            }
            // Only the chosen garment's guard is cleared; the next snapshot upload drops its photo record without deleting any blob.
            val kept = mutex.withLock { wardrobeRepository.keepGarmentWithoutRestoredPhoto(garmentId) }
            if (kept) runCycle(reason = "keep_without_photo", trigger = SyncTrigger.Foreground, forceSnapshot = true, forceImport = false)
        }
    }

    override suspend fun refreshDriveUsage() {
        if (settingsRepository.settings.first().driveSyncConnectionStatus !in setOf(
                DriveSyncConnectionStatus.Connected,
                DriveSyncConnectionStatus.NeedsAttention,
            )
        ) {
            return
        }
        // A failed listing keeps the last measured value on screen; the row shows its own update time.
        withContext(dispatcher) { driveRepository.refreshDriveUsage() }
    }

    override suspend fun useThisDriveAccount() {
        withContext(dispatcher) {
            val settings = settingsRepository.settings.first()
            // Only a connected phone can merge: a rebind while disconnected would change the account with nothing to merge.
            if (settings.driveSyncConnectionStatus !in setOf(
                    DriveSyncConnectionStatus.Connected,
                    DriveSyncConnectionStatus.NeedsAttention,
                )
            ) {
                return@withContext
            }
            val authorized = settings.driveAuthorizedAccountEmail ?: return@withContext
            // The user's choice rebinds the backup; the merge-on-connect flow then proposes what to keep, never an overwrite.
            settingsRepository.setDriveBoundAccountEmail(authorized)
            enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 0L))
        }
    }

    override suspend fun disconnectDrive() {
        withContext(dispatcher) { settingsRepository.setDriveSyncConnectionStatus(DriveSyncConnectionStatus.Disconnected) }
    }

    override suspend fun checkPhotos(): DriveSyncResult<PhotoIntegrityReport> = withContext(dispatcher) {
        driveRepository.checkPhotos(snapshotRepository.exportSnapshot())
    }

    /** True while the signed-in Drive account is not the one the backup belongs to: no upload, restore or purge may run. */
    private suspend fun accountMismatchHeld(): Boolean = settingsRepository.settings.first().let { settings ->
        accountsConflict(settings.driveAuthorizedAccountEmail, settings.driveBoundAccountEmail)
    }

    /** Explicit Retry from the attention button only; automatic cycles never release these rows. */
    private suspend fun retryAttention(kind: SyncAttentionKind) {
        val reasonCode = when (kind) {
            SyncAttentionKind.DriveFull -> DRIVE_FULL_REASON
            SyncAttentionKind.PhotoUploadFailed -> PHOTO_UPLOAD_FAILED_REASON
            else -> return
        }
        val released = mutex.withLock { wardrobeRepository.releaseNeedsUserActionToQueued(reasonCode) }
        if (released == 0) return
        val reason = "retry_attention:${kind.name}"
        requestSync(reason)
        runCycle(reason = reason, trigger = SyncTrigger.Foreground, forceSnapshot = false, forceImport = false)
    }

    private suspend fun deleteCloudBackup() = mutex.withLock {
        // Persist the safety boundary before any network I/O. Normal upload/restore shares this mutex.
        settingsRepository.pauseSyncForDriveBackupDeletion()
        try {
            val terminalState = when (val result = driveRepository.deleteBackup()) {
                is DriveSyncResult.Success -> {
                    if (result.value.remainingFileCount == 0) {
                        DriveBackupDeletionState.Complete
                    } else {
                        DriveBackupDeletionState.NeedsAttention
                    }
                }
                is DriveSyncResult.Blocked, is DriveSyncResult.Failure -> DriveBackupDeletionState.NeedsAttention
            }
            // The terminal result must survive cancellation after remote I/O has finished.
            withContext(NonCancellable) {
                settingsRepository.setDriveBackupDeletionState(terminalState)
            }
        } catch (cancellation: CancellationException) {
            // Keep sync paused and make interruption visible rather than resuming ambiguously.
            withContext(NonCancellable) {
                settingsRepository.setDriveBackupDeletionState(DriveBackupDeletionState.NeedsAttention)
            }
            throw cancellation
        }
        // Disabled intentionally remains until the user explicitly chooses Backup again.
    }

    /** Does not toggle global processing/progress: only the selected garment becomes Running. */
    private suspend fun retryRestoredPhoto(garmentId: String) {
        if (accountMismatchHeld()) return
        mutex.withLock {
            val retryCorrelationId = UUID.randomUUID().toString().take(8)
            restoreSyncLogRepository.append(
                RestoreSyncLogEvent(
                    correlationId = retryCorrelationId,
                    category = "photo_restore_retry",
                    phase = null,
                    status = null,
                    message = "retry requested",
                    garmentId = garmentId,
                ),
            )
            if (settingsRepository.settings.first().driveSyncConnectionStatus !in setOf(
                    DriveSyncConnectionStatus.Connected,
                    DriveSyncConnectionStatus.NeedsAttention,
                )
            ) {
                restoreSyncLogRepository.append(
                    RestoreSyncLogEvent(
                        correlationId = retryCorrelationId,
                        category = "photo_restore_retry",
                        level = "warn",
                        phase = null,
                        status = null,
                        message = "retry rejected reason=drive_not_connected",
                        garmentId = garmentId,
                    ),
                )
                return@withLock
            }
            // A durable Drive-side garment deletion must win over any restored-photo retry.
            if (wardrobeRepository.hasPendingCloudDeletion()) {
                restoreSyncLogRepository.append(
                    RestoreSyncLogEvent(
                        correlationId = retryCorrelationId,
                        category = "photo_restore_retry",
                        level = "warn",
                        phase = null,
                        status = null,
                        message = "retry rejected reason=cloud_deletion_pending",
                        garmentId = garmentId,
                    ),
                )
                return@withLock
            }
            val retryRevision = wardrobeRepository.claimGarmentPhotoRestoreRetry(garmentId) ?: run {
                val retryState = wardrobeRepository.observeItem(garmentId).first()
                val now = System.currentTimeMillis()
                val reason = when {
                    retryState == null -> "garment_not_found"
                    !retryState.photoRestoreState.guarded -> "photo_not_guarded"
                    retryState.syncStatus == GarmentSyncStatus.Running -> "duplicate_in_progress"
                    retryState.photoRestoreState.retryDeadlineEpochMillis?.let { it < now } == true -> "deadline_expired"
                    retryState.photoRestoreState.retryAfterEpochMillis?.let { it > now } == true -> "backoff"
                    else -> "attempt_exhausted_or_revision_changed"
                }
                restoreSyncLogRepository.append(
                    RestoreSyncLogEvent(
                        correlationId = retryCorrelationId,
                        category = "photo_restore_retry",
                        level = "warn",
                        phase = null,
                        status = null,
                        message = buildString {
                            append("retry rejected reason=").append(reason)
                            retryState?.photoRestoreState?.retryAfterEpochMillis?.let {
                                append(" retry_after_epoch_ms=").append(it)
                            }
                            retryState?.photoRestoreState?.retryDeadlineEpochMillis?.let {
                                append(" retry_deadline_epoch_ms=").append(it)
                            }
                        },
                        garmentId = garmentId,
                    ),
                )
                return@withLock
            }
            restoreSyncLogRepository.append(
                RestoreSyncLogEvent(
                    correlationId = retryCorrelationId,
                    category = "photo_restore_retry",
                    phase = CloudRestorePhase.Downloading,
                    status = CloudRestoreStatus.Running,
                    message = "retry started",
                    garmentId = garmentId,
                ),
            )

            try {
                when (val result = withTimeout(PHOTO_RESTORE_RETRY_TIMEOUT_MILLIS) {
                    driveRepository.retryRestoredPhoto(garmentId, DriveRestoreProgressListener { progress ->
                        restoreSyncLogRepository.append(
                            RestoreSyncLogEvent(
                                correlationId = retryCorrelationId,
                                category = progress.safeCategory ?: "photo_restore_retry",
                                phase = CloudRestorePhase.Downloading,
                                status = CloudRestoreStatus.Running,
                                message = "retry photo restore progress",
                                garmentId = progress.garmentId,
                                itemIndex = progress.completedItems,
                                itemTotal = progress.totalItems,
                                bytesCompleted = progress.completedBytes,
                                bytesTotal = progress.totalBytes,
                            ),
                        )
                    })
                }) {
                    is DriveSyncResult.Success -> {
                        recordPhotoRetryDiagnosticEvents(
                            correlationId = retryCorrelationId,
                            garmentId = garmentId,
                            events = result.value.restoreDiagnosticEvents,
                        )
                        val applyResult = snapshotRepository.applyRestoredPhoto(result.value, retryRevision)
                        restoreSyncLogRepository.append(
                            RestoreSyncLogEvent(
                                correlationId = retryCorrelationId,
                                category = "photo_restore_apply",
                                phase = CloudRestorePhase.Applying,
                                status = CloudRestoreStatus.Failed.takeUnless { applyResult.applied },
                                message = applyResult.toDiagnosticEvent(),
                                garmentId = garmentId,
                                blobPath = applyResult.blobPath,
                            ),
                        )
                        if (!applyResult.applied) {
                            wardrobeRepository.markGarmentPhotoRestoreFailed(
                                garmentId,
                                "$MISSING_RESTORED_PHOTO_MESSAGE Drive photo download completed but could not be saved locally.",
                            )
                        }
                    }
                    is DriveSyncResult.Blocked -> {
                        restoreSyncLogRepository.append(
                            RestoreSyncLogEvent(
                                correlationId = retryCorrelationId,
                                category = "photo_restore_retry",
                                level = "warn",
                                phase = CloudRestorePhase.Downloading,
                                status = CloudRestoreStatus.Failed,
                                message = "retry blocked reason=${result.reason.name}",
                                garmentId = garmentId,
                            ),
                        )
                        wardrobeRepository.markGarmentPhotoRestoreFailed(
                            garmentId,
                            "$MISSING_RESTORED_PHOTO_MESSAGE ${result.message}",
                        )
                    }
                    is DriveSyncResult.Failure -> {
                        restoreSyncLogRepository.append(
                            RestoreSyncLogEvent(
                                correlationId = retryCorrelationId,
                                category = "photo_restore_retry",
                                level = "error",
                                phase = CloudRestorePhase.Downloading,
                                status = CloudRestoreStatus.Failed,
                                message = "retry failed",
                                garmentId = garmentId,
                                exceptionClass = result.throwable::class.java.simpleName,
                                exceptionMessage = result.throwable.message,
                            ),
                        )
                        val message = "$MISSING_RESTORED_PHOTO_MESSAGE ${result.throwable.message ?: "Drive photo download failed."}"
                        if (result.throwable is IOException) {
                            wardrobeRepository.markGarmentPhotoRestoreTransient(garmentId, message)
                        } else {
                            wardrobeRepository.markGarmentPhotoRestoreFailed(garmentId, message)
                        }
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                restoreSyncLogRepository.append(
                    RestoreSyncLogEvent(
                        correlationId = retryCorrelationId,
                        category = "photo_restore_retry",
                        level = "error",
                        phase = CloudRestorePhase.Downloading,
                        status = CloudRestoreStatus.Failed,
                        message = PHOTO_RESTORE_RETRY_TIMEOUT_MESSAGE,
                        garmentId = garmentId,
                        exceptionClass = timeout::class.java.simpleName,
                        exceptionMessage = timeout.message,
                    ),
                )
                withContext(NonCancellable) {
                    wardrobeRepository.markGarmentPhotoRestoreTransient(
                        garmentId,
                        "$MISSING_RESTORED_PHOTO_MESSAGE $PHOTO_RESTORE_RETRY_TIMEOUT_MESSAGE",
                    )
                }
            } catch (cancellation: CancellationException) {
                restoreSyncLogRepository.append(
                    RestoreSyncLogEvent(
                        correlationId = retryCorrelationId,
                        category = "photo_restore_retry",
                        level = "warn",
                        phase = CloudRestorePhase.Downloading,
                        status = CloudRestoreStatus.Interrupted,
                        message = SYNC_INTERRUPTED_MESSAGE,
                        garmentId = garmentId,
                        exceptionClass = cancellation::class.java.simpleName,
                        exceptionMessage = cancellation.message,
                    ),
                )
                withContext(NonCancellable) {
                    wardrobeRepository.markGarmentPhotoRestoreFailed(
                        garmentId,
                        "$MISSING_RESTORED_PHOTO_MESSAGE $SYNC_INTERRUPTED_MESSAGE",
                    )
                }
                throw cancellation
            }
        }
    }

    private fun recordPhotoRetryDiagnosticEvents(
        correlationId: String,
        garmentId: String,
        events: List<String>,
    ) {
        events.take(MAX_RETRY_DIAGNOSTIC_EVENTS).forEach { event ->
            val category = when {
                event.startsWith("drive_file_lookup_result") -> "photo_restore_lookup"
                event.startsWith("photo_restore_fetch_result") -> "photo_restore_download"
                event.startsWith("photo_restore_decode_result") -> "photo_restore_decode"
                event.startsWith("photo_restore_local_write_result") -> "photo_restore_write"
                event.startsWith("photo_restore_import_result") -> "photo_restore_apply"
                else -> "photo_restore_retry"
            }
            restoreSyncLogRepository.append(
                RestoreSyncLogEvent(
                    correlationId = correlationId,
                    category = category,
                    phase = CloudRestorePhase.Downloading,
                    status = CloudRestoreStatus.Running,
                    message = event,
                    garmentId = garmentId,
                ),
            )
        }
    }

    private suspend fun restoreFreshInstallOnceIfNeeded() {
        if (hasAttemptedFreshInstallRestore || restoreProgress.value?.isRetryableTerminal == true) return
        if (accountMismatchHeld()) return

        val settings = settingsRepository.settings.first()
        if (settings.driveFreshInstallRestoreAttempted) {
            hasAttemptedFreshInstallRestore = true
            return
        }

        // Freshness is decided under the mutex: a forced import that ran first has already filled the phone, and a
        // restore must never be applied over it. The export and the restore share one lock hold, so nothing lands between them.
        // Null when the phone already holds user data, so no restore ran.
        val restoreResult = mutex.withLock {
            if (!snapshotRepository.exportSnapshot().isFreshInstallSnapshot()) return@withLock null
            // Progress left by an earlier cycle is not this restore's outcome: an idle return below would read it as settled.
            restoreProgress.value = null
            runCycleHoldingMutex(reason = "fresh_install_restore", trigger = SyncTrigger.Background, forceSnapshot = true, forceImport = true, mergeConfirmed = false)
        }
        if (restoreResult == null) {
            // Once the phone has user wardrobe data, it is authoritative and must not be overwritten.
            hasAttemptedFreshInstallRestore = true
            settingsRepository.markDriveFreshInstallRestoreAttempted()
            return
        }

        // The one-shot attempt is spent only when the restore settled: it succeeded, or it ended in a terminal state that is
        // not retryable. An idle return (not connected, a merge waiting) restored nothing, and a retryable failure or a local
        // edit that moved the phone on keeps the flag false, so a later run or reconnect re-enters the restore path instead of
        // leaving a fresh install permanently empty or placeholder-only.
        val restoreSettled = restoreResult == SyncRunResult.Success || restoreProgress.value?.let { !it.isRetryableTerminal } == true
        if (restoreSettled) {
            hasAttemptedFreshInstallRestore = true
            settingsRepository.markDriveFreshInstallRestoreAttempted()
        }
    }

    private suspend fun runCycle(
        reason: String,
        trigger: SyncTrigger,
        forceSnapshot: Boolean,
        forceImport: Boolean,
        mergeConfirmed: Boolean = false,
    ): SyncRunResult = mutex.withLock { runCycleHoldingMutex(reason, trigger, forceSnapshot, forceImport, mergeConfirmed) }

    /** The cycle body. The caller holds [mutex]; [restoreFreshInstallOnceIfNeeded] holds it across its freshness check and the restore. */
    private suspend fun runCycleHoldingMutex(
        reason: String,
        trigger: SyncTrigger,
        forceSnapshot: Boolean,
        forceImport: Boolean,
        mergeConfirmed: Boolean,
    ): SyncRunResult {
        // A new cycle starts with no sign-in state: only its own outcome sets the banner again.
        authBlocked.value = false
        DriveHttpDiagnostics.lastFailure = null
        cycleBlockedReason = null
        val idleStartedAt = System.currentTimeMillis()
        val status = settingsRepository.settings.first().driveSyncConnectionStatus
        if (status != DriveSyncConnectionStatus.Connected) {
            return recordIdleCycle(
                idleStartedAt,
                if (status == DriveSyncConnectionStatus.Disconnected) SyncRunResult.AuthBlocked else SyncRunResult.NeedsUser,
            )
        }
        // A different Google account waits for the user's choice (AccountMismatch); nothing is uploaded, restored or purged.
        if (accountMismatchHeld()) return recordIdleCycle(idleStartedAt, SyncRunResult.NeedsUser)
        // An unresolved conflict or an unconfirmed merge holds every upload until the user chooses.
        if (!forceImport && settingsRepository.syncConflict.first()) return recordIdleCycle(idleStartedAt, SyncRunResult.Conflict)
        if (!mergeConfirmed && pendingMergeSummary.value != null) return recordIdleCycle(idleStartedAt, SyncRunResult.NeedsUser)

        // Held under the mutex, so no claim of this process is live: a Running row now was left by a process that died.
        // photo_restore_guarded rows are excluded by the query: they have their own recovery at processor init.
        wardrobeRepository.recoverStaleRunningSyncWork(System.currentTimeMillis())
        val pendingWork = wardrobeRepository.pendingGarmentSyncWork()
        val pendingMetadataWork = wardrobeRepository.pendingMetadataSyncWork()
        val hasPendingEmbeddings = embeddingRepository?.pendingUploadKeys()?.isNotEmpty() == true
        if (pendingWork.isEmpty() && pendingMetadataWork.isEmpty() && !hasPendingEmbeddings && !forceSnapshot && !forceImport) {
            authBlocked.value = false
            return recordIdleCycle(idleStartedAt, SyncRunResult.Success)
        }
        cycleStorageFull = false
        cyclePhotoUploadFailureIds = emptySet()
        cycleDriveFailure = null
        cycleMergeDecision = null
        isProcessing.value = true
        // Claims happen inside the try so a cancellation mid-claim releases every row claimed so far.
        val lockedWork = mutableListOf<PendingGarmentSyncWork>()
        val lockedMetadataWork = mutableListOf<PendingMetadataSyncWork>()
        val syncCorrelationId = UUID.randomUUID().toString().take(8)
        val syncStartedAt = System.currentTimeMillis()
        val pendingAtStart = pendingWork.size + pendingMetadataWork.size
        // Stays null when the cycle was cancelled: no outcome to report.
        var cycleResult: SyncRunResult? = null
        var cycleFailure: Throwable? = null
        var keepTerminalProgress = false
        return try {
            pendingWork.forEach { work ->
                // Listed before the claim: a cancellation after the row turned Running must still release it.
                lockedWork += work
                if (!wardrobeRepository.markGarmentSyncing(work.id, work.revision, System.currentTimeMillis())) lockedWork -= work
            }
            pendingMetadataWork.forEach { work -> if (wardrobeRepository.markMetadataSyncing(work)) lockedMetadataWork += work }
            restoreSyncLogRepository.append(
                RestoreSyncLogEvent(
                    correlationId = syncCorrelationId,
                    category = "sync",
                    phase = null,
                    status = CloudRestoreStatus.Running,
                    message = "sync started trigger=${trigger.name} reason=$reason forceSnapshot=$forceSnapshot forceImport=$forceImport " +
                        "garments=${lockedWork.size} metadata=${lockedMetadataWork.size}",
                ),
            )
            cycleExportedEmbeddingKeys = embeddingRepository?.pendingUploadKeys().orEmpty()
            val localSnapshot = snapshotRepository.exportSnapshot()
            // Read after the export the decision merges, so an edit that landed between the two is still flagged. Any row not
            // yet at Drive counts, including NeedsUserAction, AuthBlocked and backoff rows the claim skips.
            cyclePendingLocalWork = pendingWork.isNotEmpty() || pendingMetadataWork.isNotEmpty() || hasPendingEmbeddings ||
                wardrobeRepository.hasPendingCloudDeletion() || wardrobeRepository.hasAnyUnsyncedLocalRow()
            val result = executeDirection(localSnapshot, forceImport, mergeConfirmed)
            cycleResult = result
            keepTerminalProgress = result != SyncRunResult.Success
            recordSyncEnded(
                syncCorrelationId,
                syncStartedAt,
                "result=$result decision=${cycleMergeDecision?.name ?: "none"}" +
                    syncFailureDetail(result, cycleBlockedReason, DriveHttpDiagnostics.lastFailure),
                level = if (result == SyncRunResult.Success) "info" else "warn",
            )
            settleClaimedWork(result, lockedWork, lockedMetadataWork)
            authBlocked.value = result == SyncRunResult.AuthBlocked
            result
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { releaseClaimedWork(lockedWork, lockedMetadataWork) }
            recordSyncEnded(syncCorrelationId, syncStartedAt, "interrupted", level = "warn", throwable = cancellation)
            throw cancellation
        } catch (throwable: Throwable) {
            keepTerminalProgress = true
            cycleFailure = throwable
            cycleResult = SyncRunResult.Retry
            releaseClaimedWork(lockedWork, lockedMetadataWork)
            recordSyncEnded(
                syncCorrelationId,
                syncStartedAt,
                "failure" + syncFailureDetail(SyncRunResult.Retry, cycleBlockedReason, DriveHttpDiagnostics.lastFailure),
                level = "error",
                throwable = throwable,
            )
            SyncRunResult.Retry
        } finally {
            isProcessing.value = false
            // A restore still Running has no terminal outcome left to show: the restored wardrobe is applied and WorkManager retries the upload.
            // Terminal failures stay visible for the user to read.
            val restoreStillRunning = restoreProgress.value?.status == CloudRestoreStatus.Running
            if (!keepTerminalProgress || restoreStillRunning) restoreProgress.value = null
            lastCycle.value = SyncCycleSummary(
                startedAtEpochMillis = syncStartedAt,
                endedAtEpochMillis = System.currentTimeMillis(),
                result = cycleResult,
                pendingAtStart = pendingAtStart,
                errorClass = (cycleFailure ?: cycleDriveFailure)?.javaClass?.simpleName,
                errorMessage = (cycleFailure ?: cycleDriveFailure)?.message,
            )
        }
    }

    /**
     * Outcome of a cycle that returned before claiming any row (not connected, conflict, merge waiting, nothing pending).
     * Records it so [lastCycle] never shows an older cycle as the current one. The pending count is a best-effort read.
     */
    private suspend fun recordIdleCycle(startedAt: Long, result: SyncRunResult): SyncRunResult {
        val pending = runCatching {
            wardrobeRepository.pendingGarmentSyncWork().size + wardrobeRepository.pendingMetadataSyncWork().size
        }.getOrDefault(0)
        lastCycle.value = SyncCycleSummary(
            startedAtEpochMillis = startedAt,
            endedAtEpochMillis = System.currentTimeMillis(),
            result = result,
            pendingAtStart = pending,
        )
        return result
    }

    private suspend fun executeDirection(
        localSnapshot: WardrobeSyncSnapshot,
        forceImport: Boolean,
        mergeConfirmed: Boolean,
    ): SyncRunResult = when {
        mergeConfirmed -> restoreThenFlushAndResolveConflict(localSnapshot, userConfirmed = true)
        // An explicit import on a phone with data must not upload while a conflict is open: the user decides first.
        forceImport && localSnapshot.hasUserData() && settingsRepository.syncConflict.first() -> SyncRunResult.Conflict
        else -> when (SyncDirectionPolicy.resolve(localSnapshot, explicitRestoreRequested = forceImport)) {
            SyncDirection.UploadLocalSnapshot -> uploadLocalSnapshot(localSnapshot)
            SyncDirection.MergeWithRemote -> proposeMergeOrUpload(localSnapshot)
            SyncDirection.RestoreRemoteThenUpload -> restoreThenFlushAndResolveConflict(localSnapshot, userConfirmed = false)
            SyncDirection.SkipEmptyLocalUpload -> {
                // No garment carries these vectors, so nothing will ever upload them: clear them, or they keep every cycle alive.
                if (!localSnapshot.hasUserData()) markExportedEmbeddingsUploaded()
                SyncRunResult.Success
            }
        }
    }

    /** A restore that succeeds resolves the open conflict: the phone now holds the remote data it was asked to take. */
    private suspend fun restoreThenFlushAndResolveConflict(localSnapshot: WardrobeSyncSnapshot, userConfirmed: Boolean): SyncRunResult {
        val result = restoreThenFlush(localSnapshot, userConfirmed)
        if (result == SyncRunResult.Success) {
            settingsRepository.setSyncConflict(false)
            pendingMergeSummary.value = null
        }
        return result
    }

    private suspend fun restoreThenFlush(localSnapshot: WardrobeSyncSnapshot, userConfirmed: Boolean): SyncRunResult = try {
        restoreRemoteThenUpload(localSnapshot, userConfirmed)
    } finally {
        withContext(NonCancellable) { restoreSyncLogRepository.flush() }
    }

    /**
     * Forced import on a phone with garments (connect or retry). The decision reads the record JSON only: a backup
     * that needs no change never downloads its photo blobs. Nothing is uploaded until [confirmMerge] when it proposes.
     */
    private suspend fun proposeMergeOrUpload(localSnapshot: WardrobeSyncSnapshot): SyncRunResult {
        val remote = when (val fetched = driveRepository.fetchSnapshotRecords()) {
            is DriveSyncResult.Success -> fetched.value.sortedDeterministically()
            is DriveSyncResult.Blocked -> return blockedRunResult(fetched)
            is DriveSyncResult.Failure -> return fetched.retry()
        }
        return decideWithRemote(localSnapshot, remote) ?: uploadLocalSnapshot(localSnapshot)
    }

    /**
     * Runs [decideMerge] for a remote backup and carries out every outcome except the ordinary upload, which the caller
     * performs when this returns null ([MergeDecision.UploadLocal]). Only [MergeDecision.Propose] shows the dialog.
     */
    private suspend fun decideWithRemote(localSnapshot: WardrobeSyncSnapshot, remote: WardrobeSyncSnapshot): SyncRunResult? {
        val decision = decideMerge(
            local = localSnapshot,
            remote = remote,
            lastKnownRemoteRevision = settingsRepository.lastKnownRemoteRevision.first(),
            lastKnownRemoteWriter = settingsRepository.lastKnownRemoteWriterDeviceId.first(),
            deviceId = settingsRepository.deviceId(),
            pendingLocalWork = cyclePendingLocalWork,
            localWasEmptyAtStart = localSnapshot.isFreshInstallSnapshot(),
        )
        cycleMergeDecision = decision
        return when (decision) {
            MergeDecision.UploadLocal -> null
            MergeDecision.NoOp -> SyncRunResult.Success
            MergeDecision.RecordBase -> {
                settingsRepository.setLastKnownRemoteRevision(remote.metadata.revision, remote.metadata.writerDeviceId)
                SyncRunResult.Success
            }
            // The restore merges the phone with the backup before it writes, so no local row is dropped on either path.
            MergeDecision.RestoreSilently,
            MergeDecision.FastForward -> restoreThenFlushAndResolveConflict(localSnapshot, userConfirmed = false)
            MergeDecision.Propose -> proposeMerge(localSnapshot, remote)
        }
    }

    private fun proposeMerge(localSnapshot: WardrobeSyncSnapshot, remote: WardrobeSyncSnapshot): SyncRunResult {
        pendingMergeRemoteRevision = remote.metadata.revision
        val merged = mergeSnapshots(localSnapshot, remote)
        pendingMergeSummary.value = MergeSummary(
            remoteGarmentCount = remote.garments.size,
            localGarmentCount = localSnapshot.garments.size,
            mergedGarmentCount = merged.garments.size,
        )
        return SyncRunResult.NeedsUser
    }

    /**
     * Ordinary upload: the newer-writer check runs on the JSON manifest before anything is uploaded.
     * Another writer's newer backup with user data is never overwritten: it goes to the merge proposal.
     */
    private suspend fun uploadLocalSnapshot(localSnapshot: WardrobeSyncSnapshot): SyncRunResult {
        val deviceId = settingsRepository.deviceId()
        val lastKnownRevision = settingsRepository.lastKnownRemoteRevision.first()
        val remoteManifest = when (val fetched = driveRepository.fetchManifest()) {
            is DriveSyncResult.Success -> fetched.value
            is DriveSyncResult.Blocked -> return blockedRunResult(fetched)
            is DriveSyncResult.Failure -> return fetched.retry()
        }
        val otherWriterBackup = remoteManifest.isNewerWriterThan(deviceId, lastKnownRevision)
        // The remote JSON (photo records, no blobs) is always read: the no-op check compares against it, and it carries
        // another writer's data and the guarded or failed photo records. A missing file reads as an empty snapshot.
        val remote = when (val fetched = driveRepository.fetchSnapshotRecords()) {
            is DriveSyncResult.Success -> fetched.value.sortedDeterministically()
            is DriveSyncResult.Blocked -> return blockedRunResult(fetched)
            is DriveSyncResult.Failure -> return fetched.retry()
        }
        if (otherWriterBackup && remote.hasUserData()) decideWithRemote(localSnapshot, remote)?.let { return it }
        // Forced cycles (tag and palette edits) upload even when nothing changed: a no-op cycle needs no Drive write.
        if (remoteAlreadyHoldsLocalSnapshot(localSnapshot, remote)) return SyncRunResult.Success
        val snapshot = localSnapshot.withWriter(deviceId).sortedDeterministically()
        return when (val uploaded = driveRepository.upsertSnapshot(snapshot, remote)) {
            is DriveSyncResult.Success -> {
                settingsRepository.setLastKnownRemoteRevision(uploaded.value.revision, deviceId)
                markExportedEmbeddingsUploaded()
                cyclePhotoUploadFailureIds = uploaded.value.photoUploadFailures.mapTo(mutableSetOf()) { it.garmentId }
                SyncRunResult.Success
            }
            is DriveSyncResult.Blocked -> blockedRunResult(uploaded)
            is DriveSyncResult.Failure -> uploaded.failureResult()
        }
    }

    /** Comparison plus one read of the pending-deletion flag. Skipping writes nothing to Drive; the claimed rows settle as on any success. */
    private suspend fun remoteAlreadyHoldsLocalSnapshot(localSnapshot: WardrobeSyncSnapshot, remote: WardrobeSyncSnapshot): Boolean =
        localSnapshot.remoteAlreadyHolds(
            remote = remote,
            hasPendingEmbeddings = cycleExportedEmbeddingKeys.isNotEmpty(),
            hasPendingCloudDeletion = wardrobeRepository.hasPendingCloudDeletion(),
        )

    /** Any Drive failure of the cycle is retried; its class and message are kept for [lastCycle]. */
    private fun DriveSyncResult.Failure.retry(): SyncRunResult {
        cycleDriveFailure = throwable
        return SyncRunResult.Retry
    }

    /** A full Drive parks the garments for the user; any other failure is transient and retried. */
    private fun DriveSyncResult.Failure.failureResult(): SyncRunResult {
        cycleDriveFailure = throwable
        if (throwable !is DriveStorageFullException) return SyncRunResult.Retry
        cycleStorageFull = true
        return SyncRunResult.NeedsUser
    }

    /** "Use backup": clears local garments only after the remote snapshot is fetched and decoded. */
    private suspend fun useBackupLocked(): SyncRunResult {
        if (accountMismatchHeld()) return SyncRunResult.NeedsUser
        val remote = when (val fetched = driveRepository.fetchSnapshot()) {
            is DriveSyncResult.Success -> fetched.value.sortedDeterministically()
            is DriveSyncResult.Blocked -> return blockedRunResult(fetched)
            is DriveSyncResult.Failure -> return SyncRunResult.Retry
        }
        // An empty remote must never wipe the phone; it keeps the conflict visible instead.
        if (!remote.hasUserData()) return SyncRunResult.NeedsUser
        // A newer schema is never applied: the conflict stays unresolved and nothing on the phone is cleared.
        if (remote.metadata.schemaVersion > WARDROBE_SYNC_SCHEMA_VERSION) return SyncRunResult.NeedsUser
        snapshotRepository.replaceLocalWithRemote(remote)
        settingsRepository.setLastKnownRemoteRevision(remote.metadata.revision, remote.metadata.writerDeviceId)
        settingsRepository.setSyncConflict(false)
        return SyncRunResult.Success
    }

    /**
     * "Keep this phone": accept only the Drive revision the conflict was raised against, then the caller uploads this phone's data.
     * A newer revision written since then reopens the conflict at that revision: the user sees it again before anything is uploaded.
     */
    private suspend fun acceptRemoteRevisionAsKnown(): SyncRunResult {
        if (accountMismatchHeld()) return SyncRunResult.NeedsUser
        val manifest = when (val fetched = driveRepository.fetchManifest()) {
            is DriveSyncResult.Success -> fetched.value
            is DriveSyncResult.Blocked -> return blockedRunResult(fetched)
            is DriveSyncResult.Failure -> return SyncRunResult.Retry
        }
        val conflictRevision = settingsRepository.syncConflictRemoteRevision.first()
        if (manifest.revision != conflictRevision) {
            if (manifest.revision > conflictRevision) settingsRepository.setSyncConflict(true, manifest.revision)
            return SyncRunResult.Conflict
        }
        settingsRepository.setLastKnownRemoteRevision(manifest.revision, manifest.writerDeviceId)
        settingsRepository.setSyncConflict(false)
        return SyncRunResult.Success
    }

    /** Conflict and merge actions: they run outside [runCycle] and never claim outbox rows. */
    private suspend fun runResolution(label: String, block: suspend () -> SyncRunResult): SyncRunResult {
        // Read before the flag is raised: a throwing read here must not leave isProcessing stuck on.
        val pendingAtStart = runCatching {
            wardrobeRepository.pendingGarmentSyncWork().size + wardrobeRepository.pendingMetadataSyncWork().size
        }.getOrDefault(0)
        isProcessing.value = true
        val correlationId = UUID.randomUUID().toString().take(8)
        val startedAt = System.currentTimeMillis()
        var outcome: SyncRunResult? = null
        var failure: Throwable? = null
        return try {
            block().also { result ->
                outcome = result
                recordSyncEnded(correlationId, startedAt, "$label result=$result")
            }
        } catch (cancellation: CancellationException) {
            recordSyncEnded(correlationId, startedAt, "$label interrupted", level = "warn", throwable = cancellation)
            throw cancellation
        } catch (throwable: Throwable) {
            failure = throwable
            outcome = SyncRunResult.Retry
            recordSyncEnded(correlationId, startedAt, "$label failure", level = "error", throwable = throwable)
            SyncRunResult.Retry
        } finally {
            isProcessing.value = false
            lastCycle.value = SyncCycleSummary(
                startedAtEpochMillis = startedAt,
                endedAtEpochMillis = System.currentTimeMillis(),
                result = outcome,
                pendingAtStart = pendingAtStart,
                errorClass = failure?.javaClass?.simpleName,
                errorMessage = failure?.message,
            )
        }
    }

    /**
     * Success marks rows synced, except garments whose photo could not be uploaded (they wait for the user).
     * A full Drive parks the garments as drive_full. Anything else returns the rows to Queued (still pending).
     */
    private suspend fun settleClaimedWork(
        result: SyncRunResult,
        lockedWork: List<PendingGarmentSyncWork>,
        lockedMetadataWork: List<PendingMetadataSyncWork>,
    ) {
        when {
            result == SyncRunResult.Success -> {
                val (photoFailed, synced) = lockedWork.partition { it.id in cyclePhotoUploadFailureIds }
                markSynced(synced, lockedMetadataWork)
                photoFailed.forEach { item ->
                    wardrobeRepository.markGarmentNeedsUserAction(item.id, item.revision, PHOTO_UPLOAD_FAILED_REASON)
                }
            }
            cycleStorageFull -> {
                lockedWork.forEach { item ->
                    wardrobeRepository.markGarmentNeedsUserAction(item.id, item.revision, DRIVE_FULL_REASON)
                }
                lockedMetadataWork.forEach { item -> wardrobeRepository.releaseMetadataSyncToQueued(item) }
            }
            else -> releaseClaimedWork(lockedWork, lockedMetadataWork)
        }
    }

    /** Transient and undecided outcomes return claimed rows to Queued: still pending, no attempt counter, no attention. */
    private suspend fun releaseClaimedWork(
        lockedWork: List<PendingGarmentSyncWork>,
        lockedMetadataWork: List<PendingMetadataSyncWork>,
    ) {
        lockedWork.forEach { item -> wardrobeRepository.releaseGarmentSyncToQueued(item.id, item.revision) }
        lockedMetadataWork.forEach { item -> wardrobeRepository.releaseMetadataSyncToQueued(item) }
    }

    /** The run result of a Blocked fetch or upload; its reason is kept for the sync-ended log line. */
    private fun blockedRunResult(blocked: DriveSyncResult.Blocked): SyncRunResult {
        cycleBlockedReason = blocked.reason
        return blocked.reason.toRunResult()
    }

    private fun DriveSyncDisabledReason.toRunResult(): SyncRunResult = when (this) {
        DriveSyncDisabledReason.UserNotConnected,
        DriveSyncDisabledReason.AccountBindingConflict -> SyncRunResult.AuthBlocked
        DriveSyncDisabledReason.GoogleCloudSetupRequired,
        DriveSyncDisabledReason.OAuthClientMissing,
        DriveSyncDisabledReason.UnsafeLocalState -> SyncRunResult.NeedsUser
    }

    private fun recordSyncEnded(
        correlationId: String,
        startedAtEpochMillis: Long,
        outcome: String,
        level: String = "info",
        throwable: Throwable? = null,
    ) {
        restoreSyncLogRepository.append(
            RestoreSyncLogEvent(
                correlationId = correlationId,
                category = "sync",
                level = level,
                phase = null,
                status = null,
                message = "sync ended outcome=$outcome",
                durationMillis = System.currentTimeMillis() - startedAtEpochMillis,
                exceptionClass = throwable?.javaClass?.simpleName,
                exceptionMessage = throwable?.message,
            ),
        )
    }

    /** A local edit landed during the download: nothing was written, so the cycle retries and the edit stays pending. */
    private fun localChangedDuringRestore(diagnostics: RestoreDiagnosticsTracker): SyncRunResult {
        diagnostics.event("local_changed_during_restore_retry")
        return SyncRunResult.Retry
    }

    private suspend fun restoreRemoteThenUpload(localSnapshot: WardrobeSyncSnapshot, userConfirmed: Boolean): SyncRunResult {
        val deviceId = settingsRepository.deviceId()
        val diagnostics = RestoreDiagnosticsTracker(
            attempt = ++restoreAttemptCounter,
            localSnapshot = localSnapshot,
            restoreSyncLogRepository = restoreSyncLogRepository,
        )
        diagnostics.event("drive_authorization_result=available")
        restoreProgress.value = diagnostics.progress(
            phase = CloudRestorePhase.Preparing,
            completedWork = RESTORE_STEP_AUTH_CHECKED,
        )
        diagnostics.event("local_snapshot_exported")
        restoreProgress.value = diagnostics.progress(
            phase = CloudRestorePhase.Preparing,
            completedWork = RESTORE_STEP_LOCAL_EXPORTED,
        )
        diagnostics.event("remote_snapshot_fetch_started")
        restoreProgress.value = diagnostics.progress(
            phase = CloudRestorePhase.Downloading,
            completedWork = RESTORE_STEP_REMOTE_FETCH_STARTED,
        )
        val remoteSnapshot = when (val result = driveRepository.fetchSnapshot(DriveRestoreProgressListener { progress ->
            diagnostics.recordDownloadProgress(progress)
            restoreProgress.value = diagnostics.progress(
                phase = CloudRestorePhase.Downloading,
                completedWork = RESTORE_STEP_REMOTE_FETCH_STARTED,
                itemProgress = RestoreItemProgress(progress.completedItems, progress.totalItems),
                byteProgress = RestoreByteProgress(progress.completedBytes, progress.totalBytes),
            )
        })) {
            is DriveSyncResult.Success -> {
                val snapshot = result.value.sortedDeterministically()
                diagnostics.recordRemoteSnapshot(snapshot)
                diagnostics.recordGuardedRemotePhotos(snapshot.guardedPhotoRestoreIssues())
                snapshot
            }
            is DriveSyncResult.Blocked -> {
                diagnostics.recordBlocked(result.reason, result.message)
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Downloading,
                    completedWork = CloudRestorePhase.Downloading.completedRestoreWork,
                    status = CloudRestoreStatus.Failed,
                )
                return result.reason.toRunResult()
            }
            is DriveSyncResult.Failure -> {
                diagnostics.recordFailure(result.throwable)
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Downloading,
                    completedWork = CloudRestorePhase.Downloading.completedRestoreWork,
                    status = result.throwable.restoreFetchFailureStatus(),
                )
                return SyncRunResult.Retry
            }
        }
        diagnostics.event("remote_snapshot_fetched")
        restoreProgress.value = diagnostics.progress(
            phase = CloudRestorePhase.Downloading,
            completedWork = RESTORE_STEP_REMOTE_FETCHED,
        )
        diagnostics.event("remote_snapshot_validating")
        restoreProgress.value = diagnostics.progress(
            phase = CloudRestorePhase.Validating,
            completedWork = RESTORE_STEP_VALIDATED,
        )
        if (remoteSnapshot.metadata.schemaVersion > WARDROBE_SYNC_SCHEMA_VERSION) {
            diagnostics.recordFailure(
                IllegalStateException(
                    "Remote schema ${remoteSnapshot.metadata.schemaVersion} is newer than supported schema $WARDROBE_SYNC_SCHEMA_VERSION.",
                ),
                category = "schema_version",
            )
            restoreProgress.value = diagnostics.progress(
                phase = CloudRestorePhase.Validating,
                completedWork = CloudRestorePhase.Validating.completedRestoreWork,
                status = CloudRestoreStatus.Failed,
            )
            return SyncRunResult.NeedsUser
        }

        // Only a user-confirmed merge may write over a phone file that a guarded remote photo would replace; a silent restore asks.
        if (!userConfirmed && remoteSnapshot.guardedPhotoOverPhoneFile(localSnapshot)) {
            diagnostics.event("guarded_photo_over_phone_file_proposed")
            cycleMergeDecision = MergeDecision.Propose
            return proposeMerge(localSnapshot, remoteSnapshot)
        }
        val mergedSnapshot = mergeSnapshots(localSnapshot, remoteSnapshot).withWriter(deviceId)
        diagnostics.event("snapshots_merged")
        restoreProgress.value = diagnostics.progress(
            phase = CloudRestorePhase.Validating,
            completedWork = RESTORE_STEP_MERGED,
        )
        diagnostics.event("local_import_started")
        restoreProgress.value = diagnostics.progress(
            phase = CloudRestorePhase.Applying,
            completedWork = RESTORE_STEP_IMPORT_STARTED,
        )
        // The phone may have changed while the backup downloaded. The write happens only if it still matches the export this
        // cycle merged against; otherwise nothing is written, the edit stays pending, and the next run merges again.
        val importResult = if (mergedSnapshot.hasUserData()) {
            snapshotRepository.importSnapshotIfLocalUnchanged(localSnapshot, mergedSnapshot)
                ?: return localChangedDuringRestore(diagnostics)
        } else {
            ImportSnapshotResult(restoredGarmentCount = 0, restoredPhotoCount = 0, guardedPhotoCount = 0)
        }
        diagnostics.recordLocalSave(importResult)
        onRestoreImported()

        restoreProgress.value = diagnostics.progress(
            phase = CloudRestorePhase.Applying,
            completedWork = RESTORE_STEP_LOCAL_SAVED,
        )
        val finalUploadGuardReason = mergedSnapshot.finalUploadGuardReasonAfterRestore()
        if (finalUploadGuardReason != null) {
            diagnostics.recordFinalUploadSkipped(finalUploadGuardReason)
            restoreProgress.value = diagnostics.progress(
                phase = CloudRestorePhase.Complete,
                completedWork = RESTORE_TOTAL_STEPS,
                status = CloudRestoreStatus.CompletedWithAttention,
            )
            // Nothing was uploaded, so the claimed local rows stay pending; the guard is retried on the next run.
            // The base is not recorded either: the remote stays unknown, so the next cycle compares it again and proposes.
            return SyncRunResult.Retry
        }
        settingsRepository.setLastKnownRemoteRevision(remoteSnapshot.metadata.revision, remoteSnapshot.metadata.writerDeviceId)
        if (!mergedSnapshot.hasUserData()) {
            diagnostics.recordFinalUploadSkipped("no_remote_user_data")
            restoreProgress.value = diagnostics.progress(
                phase = CloudRestorePhase.Complete,
                completedWork = RESTORE_TOTAL_STEPS,
            )
            return SyncRunResult.Success
        }
        // SnapshotEquivalence ignores embeddings, so a pending vector must still be uploaded even when the rest matches.
        if (mergedSnapshot.equivalentForUpload(remoteSnapshot) && cycleExportedEmbeddingKeys.isEmpty()) {
            diagnostics.recordFinalUploadSkipped("remote_equivalent")
            restoreProgress.value = diagnostics.progress(
                phase = CloudRestorePhase.Complete,
                completedWork = RESTORE_TOTAL_STEPS,
            )
            return SyncRunResult.Success
        }

        diagnostics.recordFinalUploadAttempted()
        restoreProgress.value = diagnostics.progress(
            phase = CloudRestorePhase.Uploading,
            completedWork = RESTORE_STEP_UPLOAD_STARTED,
        )

        // Photo progress only updates the overlay state; it adds no sync-log lines, one per photo would flood the log.
        val onPhotoProgress = { completedPhotos: Int, totalPhotos: Int ->
            restoreProgress.value = restoreProgress.value?.copy(
                uploadProgress = RestoreUploadProgress(completedPhotos, totalPhotos),
            )
        }
        return when (val uploadResult = driveRepository.upsertSnapshot(mergedSnapshot, remoteSnapshot, onPhotoProgress)) {
            is DriveSyncResult.Success -> {
                diagnostics.recordFinalUploadSucceeded()
                settingsRepository.setLastKnownRemoteRevision(uploadResult.value.revision, deviceId)
                markExportedEmbeddingsUploaded()
                // Garments whose photo did not upload wait for the user, exactly as in uploadLocalSnapshot.
                cyclePhotoUploadFailureIds = uploadResult.value.photoUploadFailures.mapTo(mutableSetOf()) { it.garmentId }
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Uploading,
                    completedWork = RESTORE_STEP_FINAL_SYNCED,
                )
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Complete,
                    completedWork = RESTORE_TOTAL_STEPS,
                )
                SyncRunResult.Success
            }
            is DriveSyncResult.Blocked -> {
                diagnostics.recordBlocked(uploadResult.reason, uploadResult.message, finalUploadSucceeded = false)
                // The restored wardrobe is applied locally already, so the progress overlay still completes.
                // The claimed rows are not marked synced: the run reports the block and they stay pending.
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Complete,
                    completedWork = RESTORE_TOTAL_STEPS,
                )
                uploadResult.reason.toRunResult()
            }
            is DriveSyncResult.Failure -> {
                diagnostics.recordFailure(uploadResult.throwable, finalUploadSucceeded = false)
                // Same as the blocked branch: the local restore stands, but the upload did not happen.
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Complete,
                    completedWork = RESTORE_TOTAL_STEPS,
                )
                uploadResult.failureResult()
            }
        }
    }

    /** Only after a stored snapshot: a failed upload leaves the vectors pending for the next cycle. */
    private suspend fun markExportedEmbeddingsUploaded() {
        embeddingRepository?.markUploaded(cycleExportedEmbeddingKeys)
    }

    private suspend fun markSynced(work: List<PendingGarmentSyncWork>, metadataWork: List<PendingMetadataSyncWork>) {
        val now = System.currentTimeMillis()
        work.forEach { item -> wardrobeRepository.markGarmentSynced(item.id, item.revision, now) }
        metadataWork.forEach { item -> wardrobeRepository.markMetadataSynced(item, now) }
    }

    private fun DriveSyncConnectionStatus.toWardrobeSyncState(
        inputs: WardrobeSyncStateInputs,
        restoreProgress: CloudRestoreProgress?,
    ): WardrobeSyncState {
        val displayStatus = when {
            inputs.isProcessing && this == DriveSyncConnectionStatus.Connected -> DriveSyncConnectionStatus.Syncing
            inputs.needsPhotoRestoreAttention && this == DriveSyncConnectionStatus.Connected -> DriveSyncConnectionStatus.NeedsAttention
            inputs.attentionOperationCount > 0 && this == DriveSyncConnectionStatus.Connected -> DriveSyncConnectionStatus.NeedsAttention
            else -> this
        }
        return WardrobeSyncState(
            connectionStatus = displayStatus,
            disabledReason = when (this) {
                DriveSyncConnectionStatus.NotConfigured -> DriveSyncDisabledReason.GoogleCloudSetupRequired
                DriveSyncConnectionStatus.Disabled -> DriveSyncDisabledReason.UnsafeLocalState
                DriveSyncConnectionStatus.Disconnected -> DriveSyncDisabledReason.UserNotConnected
                DriveSyncConnectionStatus.Connected,
                DriveSyncConnectionStatus.Syncing,
                DriveSyncConnectionStatus.NeedsAttention -> null
            },
            pendingOperationCount = inputs.pendingOperationCount,
            restoreProgress = restoreProgress,
            attention = inputs.attention(),
            pendingMergeSummary = inputs.pendingMergeSummary,
            conflict = inputs.conflict,
            lastCycle = inputs.lastCycle,
            driveUsage = inputs.driveUsage,
            authorizedAccountEmail = inputs.authorizedAccountEmail,
            expectedAccountEmail = inputs.expectedAccountEmail,
        )
    }

    private fun WardrobeSyncOperation.affectedGarmentIds(): Set<String> = when (this) {
        is WardrobeSyncOperation.UpsertItem -> setOf(itemId)
        is WardrobeSyncOperation.DeleteItemFolder -> setOf(itemId)
        is WardrobeSyncOperation.UpsertGarments -> touchedGarmentIds
        is WardrobeSyncOperation.UpsertTags,
        is WardrobeSyncOperation.UpsertPalette,
        is WardrobeSyncOperation.ExportFullSnapshot,
        is WardrobeSyncOperation.ImportFullSnapshot,
        is WardrobeSyncOperation.RetryRestoredPhoto,
        is WardrobeSyncOperation.RetryAttention,
        is WardrobeSyncOperation.UpsertTaxonomy,
        is WardrobeSyncOperation.RecordTombstones,
        is WardrobeSyncOperation.DeleteCloudBackup -> emptySet()
    }
}

/** Outcome of one sync run. The worker maps [SyncRunResult.Retry] to WorkManager backoff and everything else to success. */
sealed interface SyncRunResult {
    data object Success : SyncRunResult
    data object Retry : SyncRunResult
    data object AuthBlocked : SyncRunResult
    data object Conflict : SyncRunResult
    data object NeedsUser : SyncRunResult
}

enum class SyncTrigger { Foreground, Background }

private data class WardrobeSyncStateInputs(
    val authorizedAccountEmail: String?,
    val expectedAccountEmail: String?,
    val connectionStatus: DriveSyncConnectionStatus,
    val pendingOperationCount: Int,
    val attentionOperationCount: Int,
    val isProcessing: Boolean,
    val needsPhotoRestoreAttention: Boolean,
    val pendingMergeSummary: MergeSummary?,
    val conflict: Boolean,
    val authBlocked: Boolean,
    val driveFullCount: Int,
    val photoUploadFailedCount: Int,
    val lastCycle: SyncCycleSummary?,
    val driveUsage: DriveUsage?,
)

private fun WardrobeSyncStateInputs.attention(): List<SyncAttention> = buildList {
    if (authBlocked || (connectionStatus == DriveSyncConnectionStatus.Disconnected && pendingOperationCount > 0)) {
        add(SyncAttention(SyncAttentionKind.SignInRequired))
    }
    if (conflict) add(SyncAttention(SyncAttentionKind.BackupConflict))
    if (pendingMergeSummary != null) add(SyncAttention(SyncAttentionKind.MergeConfirmation))
    if (needsPhotoRestoreAttention) add(SyncAttention(SyncAttentionKind.PhotoUnrecoverable))
    if (driveFullCount > 0) add(SyncAttention(SyncAttentionKind.DriveFull))
    if (photoUploadFailedCount > 0) add(SyncAttention(SyncAttentionKind.PhotoUploadFailed))
    if (accountsConflict(authorizedAccountEmail, expectedAccountEmail)) add(SyncAttention(SyncAttentionKind.AccountMismatch))
}

/**
 * What a cycle that did not succeed adds to its "sync ended" line: the Blocked reason and the last HTTP code and Google reason.
 * Developer log only; it carries no token and no response body.
 */
internal fun syncFailureDetail(result: SyncRunResult, blockedReason: DriveSyncDisabledReason?, lastHttpFailure: String?): String {
    if (result == SyncRunResult.Success) return ""
    return listOfNotNull(blockedReason?.let { reason -> "blocked=${reason.name}" }, lastHttpFailure)
        .joinToString(separator = "") { detail -> " $detail" }
}

/**
 * True when the backup is bound to an account and the signed-in account is either unknown or a different one
 * (case-insensitive). An unknown account is not allowed to write to a bound backup.
 */
internal fun accountsConflict(authorizedEmail: String?, boundEmail: String?): Boolean =
    boundEmail != null && (authorizedEmail == null || !authorizedEmail.equals(boundEmail, ignoreCase = true))

/** The e-mail to keep after an authorization: a failed lookup (null) keeps the address already on record. */
internal fun authorizedEmailAfterLookup(looked: String?, onRecord: String?): String? = looked ?: onRecord

private fun WardrobeSyncSnapshot.withWriter(deviceId: String): WardrobeSyncSnapshot =
    copy(metadata = metadata.copy(writerDeviceId = deviceId))

/**
 * Another writer made a backup newer than the last revision this phone saw or uploaded.
 * A backup written before S04 has no writer id; it counts as another writer (PLAN-CHANGE 7).
 */
private fun DriveManifest.isNewerWriterThan(deviceId: String, lastKnownRevision: Long): Boolean =
    writerDeviceId != deviceId && revision > lastKnownRevision

private const val MAX_RETRY_ATTEMPTS = 3
private const val DRIVE_FULL_REASON = "drive_full"
private const val PHOTO_UPLOAD_FAILED_REASON = "photo_upload_failed"
private const val MAX_RETRY_DIAGNOSTIC_EVENTS = 16
/** Drive states in which a request reaches a run that can act on it; any other state would only wake WorkManager for nothing. */
// NeedsAttention is derived for display only and never stored, so only a stored Connected can run a request.
private val INIT_REQUEST_STATUSES = setOf(DriveSyncConnectionStatus.Connected)
private const val PHOTO_RESTORE_RETRY_TIMEOUT_MILLIS = 60 * 1000L
private const val PHOTO_RESTORE_RETRY_TIMEOUT_MESSAGE = "Drive photo retry timed out."
private const val SYNC_INTERRUPTED_MESSAGE = "Drive sync was interrupted. Retry is available after backoff."

private class RestoreDiagnosticsTracker(
    private val attempt: Int,
    localSnapshot: WardrobeSyncSnapshot,
    private val restoreSyncLogRepository: RestoreSyncLogRepository,
    private val startedAtEpochMillis: Long = System.currentTimeMillis(),
    private val correlationId: String = UUID.randomUUID().toString().take(8),
) {
    private var localSaveCompleted: Boolean? = null
    private var finalUploadAttempted: Boolean? = null
    private var finalUploadSucceeded: Boolean? = null
    private var remoteSnapshot: WardrobeSyncSnapshot? = null
    private var restoredGarmentCount: Int? = null
    private var guardedPhotoCount: Int? = null
    private var downloadedPhotoCount: Int? = null
    private var downloadedPhotoBytes: Long? = null
    private var lastExceptionClass: String? = null
    private var lastExceptionMessage: String? = null
    private var failureCategory: String? = null
    private val events = mutableListOf<String>()

    private val localWasEmpty = localSnapshot.isFreshInstallSnapshot()
    private val localGarmentCount = localSnapshot.garments.size
    private val localPhotoCount = localSnapshot.photos.size

    init {
        event("restore_started")
        event("local_empty=$localWasEmpty garments=$localGarmentCount photos=$localPhotoCount")
    }

    fun recordRemoteSnapshot(snapshot: WardrobeSyncSnapshot) {
        remoteSnapshot = snapshot
        event(
            "remote_snapshot schema=${snapshot.metadata.schemaVersion} revision=${snapshot.metadata.revision} " +
                "garments=${snapshot.garments.size} photos=${snapshot.photos.size} favorites=${snapshot.garments.count { it.isFavorite }}",
        )
        snapshot.photos.take(MAX_PHOTO_EVENTS).forEach { photo ->
            val photoEvents = photo.restoreDiagnosticEvents.ifEmpty { listOf(photo.restoreDiagnosticEvent()) }
            photoEvents.take(MAX_EVENTS_PER_PHOTO).forEach(::event)
        }
        if (snapshot.photos.size > MAX_PHOTO_EVENTS) {
            event("photo_restore_events_truncated total=${snapshot.photos.size} shown=$MAX_PHOTO_EVENTS")
        }
    }

    fun recordLocalSave(result: ImportSnapshotResult) {
        localSaveCompleted = true
        restoredGarmentCount = result.restoredGarmentCount
        guardedPhotoCount = result.guardedPhotoCount
        event(
            "local_save_completed restored_garments=${result.restoredGarmentCount} " +
                "restored_photos=${result.restoredPhotoCount} guarded_photos=${result.guardedPhotoCount}",
        )
    }

    fun recordDownloadProgress(progress: DriveRestoreProgress) {
        downloadedPhotoCount = progress.completedItems
        downloadedPhotoBytes = progress.completedBytes
        restoreSyncLogRepository.append(
            RestoreSyncLogEvent(
                correlationId = correlationId,
                category = progress.safeCategory ?: "photo_restore_download",
                phase = CloudRestorePhase.Downloading,
                status = CloudRestoreStatus.Running,
                message = "restore download progress",
                garmentId = progress.garmentId,
                itemIndex = progress.completedItems,
                itemTotal = progress.totalItems,
                bytesCompleted = progress.completedBytes,
                bytesTotal = progress.totalBytes,
            ),
        )
    }

    fun recordGuardedRemotePhotos(issues: List<PhotoRestoreIssue>) {
        if (issues.isEmpty()) return
        guardedPhotoCount = issues.size
        failureCategory = PHOTO_RESTORE_GUARDED_CATEGORY
        lastExceptionClass = "RemotePhotoRestoreGuarded"
        lastExceptionMessage = sanitizeDiagnosticMessage(
            "${issues.size} remote photo blob(s) missing/corrupt/unreadable; restored garment metadata without those photos.",
        )
        val categoryCounts = issues.groupingBy(PhotoRestoreIssue::category).eachCount().toSortedMap()
        event("guarded_remote_photos total=${issues.size} categories=$categoryCounts")
        event(
            "guarded_remote_photos_recovery path=install_new_apk_then_retry_restore_or_delete_readd_affected_garment " +
                "effect=final_upload_skipped_until_photo_restore_issue_is_resolved",
        )
        issues.take(MAX_EVENTS).forEach { issue ->
            restoreSyncLogRepository.append(
                RestoreSyncLogEvent(
                    correlationId = correlationId,
                    phase = CloudRestorePhase.Downloading,
                    status = CloudRestoreStatus.Running,
                    message = "remote photo restore guarded",
                    garmentId = issue.garmentId,
                    blobPath = issue.blobPath,
                    byteSize = issue.byteSize,
                    mimeType = issue.mimeType,
                    byteMagic = issue.byteMagic,
                    contentHash = issue.contentHash,
                    placeholderReason = issue.category,
                    exceptionClass = "RemotePhotoRestoreGuarded",
                    exceptionMessage = issue.message,
                ),
            )
        }
    }

    fun recordFinalUploadAttempted() {
        finalUploadAttempted = true
        event("final_upload_attempted")
    }

    fun recordFinalUploadSucceeded() {
        finalUploadSucceeded = true
        event("final_upload_succeeded")
    }

    fun recordFinalUploadSkipped(reason: String) {
        finalUploadAttempted = false
        finalUploadSucceeded = null
        event("final_upload_skipped reason=$reason")
    }

    fun recordBlocked(
        reason: DriveSyncDisabledReason,
        message: String,
        finalUploadSucceeded: Boolean? = this.finalUploadSucceeded,
    ) {
        this.finalUploadSucceeded = finalUploadSucceeded
        lastExceptionClass = reason.name
        lastExceptionMessage = sanitizeDiagnosticMessage(message)
        failureCategory = "blocked_${reason.name}"
        event("blocked reason=${reason.name}")
    }

    fun recordFailure(
        throwable: Throwable,
        category: String = throwable.diagnosticCategory(),
        finalUploadSucceeded: Boolean? = this.finalUploadSucceeded,
    ) {
        this.finalUploadSucceeded = finalUploadSucceeded
        lastExceptionClass = throwable::class.java.simpleName
        lastExceptionMessage = sanitizeDiagnosticMessage(throwable.message ?: throwable.toString())
        failureCategory = category
        event("failure class=$lastExceptionClass category=$failureCategory")
    }

    fun event(name: String) {
        val sanitizedName = sanitizeDiagnosticMessage(name)
        events += sanitizedName
        if (events.size > MAX_EVENTS) events.removeAt(0)
        restoreSyncLogRepository.append(
            RestoreSyncLogEvent(
                correlationId = correlationId,
                phase = null,
                status = null,
                message = sanitizedName,
            ),
        )
    }

    fun progress(
        phase: CloudRestorePhase,
        completedWork: Int,
        status: CloudRestoreStatus = CloudRestoreStatus.Running,
        message: String? = null,
        itemProgress: RestoreItemProgress? = null,
        byteProgress: RestoreByteProgress? = null,
    ): CloudRestoreProgress {
        restoreSyncLogRepository.append(
            RestoreSyncLogEvent(
                correlationId = correlationId,
                phase = phase,
                status = status,
                message = message ?: "restore progress ${phase.name}",
                completedWork = completedWork,
                totalWork = RESTORE_TOTAL_STEPS,
                placeholderReason = failureCategory,
                exceptionClass = lastExceptionClass,
                exceptionMessage = lastExceptionMessage,
                itemIndex = itemProgress?.completedItems,
                itemTotal = itemProgress?.totalItems,
                bytesCompleted = byteProgress?.completedBytes,
                bytesTotal = byteProgress?.totalBytes,
            ),
        )
        return CloudRestoreProgress(
            phase = phase,
            completedWork = completedWork,
            totalWork = RESTORE_TOTAL_STEPS,
            status = status,
            message = message,
            diagnostics = snapshot(phase, status),
            itemProgress = itemProgress,
            byteProgress = byteProgress,
        )
    }

    private fun snapshot(phase: CloudRestorePhase, status: CloudRestoreStatus): CloudRestoreDiagnostics = CloudRestoreDiagnostics(
        correlationId = correlationId,
        attempt = attempt,
        startedAtEpochMillis = startedAtEpochMillis,
        elapsedMillis = System.currentTimeMillis() - startedAtEpochMillis,
        phase = phase,
        status = status,
        localWasEmpty = localWasEmpty,
        localGarmentCount = localGarmentCount,
        localPhotoCount = localPhotoCount,
        remoteSchemaVersion = remoteSnapshot?.metadata?.schemaVersion,
        remoteRevision = remoteSnapshot?.metadata?.revision,
        remoteGarmentCount = remoteSnapshot?.garments?.size,
        remotePhotoCount = remoteSnapshot?.photos?.size,
        remoteFavoriteFieldPresent = remoteSnapshot?.let { true },
        remoteFavoriteMarkedCount = remoteSnapshot?.garments?.count { it.isFavorite },
        restoredGarmentCount = restoredGarmentCount,
        guardedPhotoCount = guardedPhotoCount,
        downloadedPhotoCount = downloadedPhotoCount,
        downloadedPhotoBytes = downloadedPhotoBytes,
        localSaveCompleted = localSaveCompleted,
        finalUploadAttempted = finalUploadAttempted,
        finalUploadSucceeded = finalUploadSucceeded,
        lastExceptionClass = lastExceptionClass,
        lastExceptionMessage = lastExceptionMessage,
        failureCategory = failureCategory,
        events = events.toList(),
    )

    private companion object {
        const val MAX_EVENTS = 32
        const val MAX_PHOTO_EVENTS = 12
        const val MAX_EVENTS_PER_PHOTO = 12
    }
}

private enum class SyncDirection {
    UploadLocalSnapshot,
    MergeWithRemote,
    RestoreRemoteThenUpload,
    SkipEmptyLocalUpload,
}

private object SyncDirectionPolicy {
    fun resolve(
        localSnapshot: WardrobeSyncSnapshot,
        explicitRestoreRequested: Boolean,
    ): SyncDirection = when {
        explicitRestoreRequested && localSnapshot.isFreshInstallSnapshot() -> SyncDirection.RestoreRemoteThenUpload
        explicitRestoreRequested && localSnapshot.hasUserData() -> SyncDirection.MergeWithRemote
        localSnapshot.hasUserData() -> SyncDirection.UploadLocalSnapshot
        else -> SyncDirection.SkipEmptyLocalUpload
    }
}

private fun WardrobeSyncSnapshot.isFreshInstallSnapshot(): Boolean = !hasUserData()

internal fun WardrobeSyncSnapshot.hasUserData(): Boolean =
    garments.isNotEmpty() ||
        photos.isNotEmpty() ||
        tombstones.isNotEmpty() ||
        // An archived default is a customisation too: DefaultTags compares names and order, not the archive flag.
        taxonomies.categories.any { category -> category.isArchived || DefaultTags.isCustomOrModifiedDefault(category.toDomain()) } ||
        taxonomies.tags.any { tag -> tag.isArchived || DefaultTags.isCustomOrModifiedDefault(tag.toDomain()) } ||
        taxonomies.mainColors.any { color -> color.isArchived || DefaultTags.isCustomOrModifiedDefault(color.toDomain()) }

private fun TagCategorySyncRecord.toDomain(): TagCategory = TagCategory(
    id = id,
    name = name,
    sortOrder = sortOrder,
    isSystem = isSystem,
)

private fun TagSyncRecord.toDomain(): GarmentTag = GarmentTag(
    id = id,
    categoryId = categoryId,
    name = name,
    sortOrder = sortOrder,
    isSystem = isSystem,
    description = description,
    suggestOccasions = suggestOccasions,
)

private fun MainColorSyncRecord.toDomain(): MainColor = MainColor(
    id = id,
    name = name,
    hex = hex,
    sortOrder = sortOrder,
    isDefault = isDefault,
)

private val CloudRestoreProgress.isRetryableTerminal: Boolean
    get() = status == CloudRestoreStatus.Interrupted ||
        status == CloudRestoreStatus.Offline ||
        status == CloudRestoreStatus.Failed ||
        status == CloudRestoreStatus.RolledBack

private val CloudRestorePhase.completedRestoreWork: Int
    get() = when (this) {
        CloudRestorePhase.Preparing -> RESTORE_STEP_AUTH_CHECKED
        CloudRestorePhase.Downloading -> RESTORE_STEP_REMOTE_FETCH_STARTED
        CloudRestorePhase.Validating -> RESTORE_STEP_VALIDATED
        CloudRestorePhase.Applying -> RESTORE_STEP_IMPORT_STARTED
        CloudRestorePhase.Uploading -> RESTORE_STEP_UPLOAD_STARTED
        CloudRestorePhase.RollingBack,
        CloudRestorePhase.Complete -> RESTORE_TOTAL_STEPS
    }

internal fun mergeSnapshots(local: WardrobeSyncSnapshot, remote: WardrobeSyncSnapshot): WardrobeSyncSnapshot {
    val tombstones = mergeByKey(local.tombstones + remote.tombstones, { "${it.entityType}:${it.entityId}" }, SyncTombstoneRecord::revision)
    val tombstoneByGarmentId = tombstones
        .filter { tombstone -> tombstone.entityType in garmentEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)
    val tombstoneByCategoryId = tombstones
        .filter { tombstone -> tombstone.entityType in categoryEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)
    val tombstoneByTagId = tombstones
        .filter { tombstone -> tombstone.entityType in tagEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)
    val tombstoneByMainColorId = tombstones
        .filter { tombstone -> tombstone.entityType in mainColorEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)

    val mergedGarments = mergeByKey(local.garments + remote.garments, GarmentSyncRecord::id, GarmentSyncRecord::revision)
        .filterNot { garment -> (tombstoneByGarmentId[garment.id]?.revision ?: Long.MIN_VALUE) > garment.revision }
    val activeGarmentIds = mergedGarments.map(GarmentSyncRecord::id).toSet()
    val mergedCategories = mergeByKey(
        local.taxonomies.categories + remote.taxonomies.categories,
        TagCategorySyncRecord::id,
        TagCategorySyncRecord::revision,
    ).filterNot { category -> (tombstoneByCategoryId[category.id]?.revision ?: Long.MIN_VALUE) >= category.revision }
    val activeCategoryIds = mergedCategories.map(TagCategorySyncRecord::id).toSet()
    val mergedTags = mergeByKey(local.taxonomies.tags + remote.taxonomies.tags, TagSyncRecord::id, TagSyncRecord::revision)
        .filterNot { tag -> (tombstoneByTagId[tag.id]?.revision ?: Long.MIN_VALUE) >= tag.revision }
        .filter { tag -> tag.categoryId in activeCategoryIds }
    val activeTagIds = mergedTags.map(TagSyncRecord::id).toSet()
    val mergedMainColors = mergeByKey(
        local.taxonomies.mainColors + remote.taxonomies.mainColors,
        MainColorSyncRecord::id,
        MainColorSyncRecord::revision,
    ).filterNot { color -> (tombstoneByMainColorId[color.id]?.revision ?: Long.MIN_VALUE) >= color.revision }
    val activeMainColorIds = mergedMainColors.map(MainColorSyncRecord::id).toSet()
    val mergedMetadata = WardrobeSnapshotMetadata(
        generatedAtEpochMillis = System.currentTimeMillis(),
        revision = maxOf(local.metadata.revision, remote.metadata.revision, System.currentTimeMillis()),
        wardrobeId = local.metadata.wardrobeId ?: remote.metadata.wardrobeId,
    )

    return WardrobeSyncSnapshot(
        metadata = mergedMetadata,
        taxonomies = WardrobeTaxonomySnapshot(
            categories = mergedCategories,
            tags = mergedTags,
            mainColors = mergedMainColors,
        ),
        garments = mergedGarments,
        garmentTags = mergeByKey(
            local.garmentTags + remote.garmentTags,
            { "${it.garmentId}:${it.tagId}" },
            GarmentTagMappingRecord::revision,
        ).filter { record -> record.garmentId in activeGarmentIds && record.tagId in activeTagIds },
        garmentColors = mergeByKey(
            local.garmentColors + remote.garmentColors,
            { "${it.garmentId}:${it.role}" },
            GarmentColorMappingRecord::revision,
        ).filter { record ->
            record.garmentId in activeGarmentIds &&
                record.paletteColorId?.let(activeMainColorIds::contains) != false
        },
        photos = mergePhotos(local.photos, remote.photos)
            .filter { record -> record.garmentId in activeGarmentIds },
        tombstones = tombstones,
    ).sortedDeterministically()
}

private fun mergePhotos(
    localPhotos: List<GarmentPhotoRecord>,
    remotePhotos: List<GarmentPhotoRecord>,
): List<GarmentPhotoRecord> {
    val localPhotoByGarmentId = localPhotos.associateBy(GarmentPhotoRecord::garmentId)
    return mergeByKey(localPhotos + remotePhotos, GarmentPhotoRecord::garmentId, GarmentPhotoRecord::revision)
        .map { merged ->
            val local = localPhotoByGarmentId[merged.garmentId]
            if (
                local != null &&
                local.revision >= merged.revision &&
                merged.restoredLocalUri.isNullOrBlank() &&
                merged.localUri != local.localUri
            ) {
                local
            } else {
                merged
            }
        }
}

/**
 * A remote photo whose blob did not restore, for a garment the phone still holds a file for. The silent restore must not decide
 * it: the phone's file would be kept and the garment held in attention, so the user chooses. Only the fetched blobs carry the
 * guard, so this runs after the fetch.
 */
internal fun WardrobeSyncSnapshot.guardedPhotoOverPhoneFile(local: WardrobeSyncSnapshot): Boolean {
    val phoneFileGarmentIds = local.photos.filter { it.localUri.isNotBlank() }.mapTo(mutableSetOf()) { it.garmentId }
    return guardedPhotoRestoreIssues().any { issue -> issue.garmentId in phoneFileGarmentIds }
}

private fun <T, K> mergeByKey(records: List<T>, key: (T) -> K, revision: (T) -> Long): List<T> =
    records
        .groupBy(key)
        .values
        .map { group -> group.maxWith(compareBy<T> { revision(it) }.thenBy { records.indexOf(it) }) }

private fun Throwable.diagnosticCategory(): String = when (this) {
    is java.net.SocketTimeoutException -> "network_timeout"
    is java.net.UnknownHostException -> "network_dns"
    is java.net.ConnectException -> "network_connect"
    is IOException -> message?.httpStatusCategory() ?: "network_io"
    else -> message?.httpStatusCategory() ?: "exception"
}

private fun String.httpStatusCategory(): String? {
    val status = Regex("HTTP\\s+(\\d{3})").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
    return when (status) {
        401, 403 -> "http_auth_$status"
        404 -> "http_not_found"
        in 400..499 -> "http_client_$status"
        in 500..599 -> "http_server_$status"
        else -> "http_$status"
    }
}

internal data class PhotoRestoreIssue(
    val garmentId: String,
    val category: String,
    val message: String?,
    val blobPath: String?,
    val byteSize: Long?,
    val mimeType: String?,
    val byteMagic: String?,
    val contentHash: String?,
)

internal const val PHOTO_RESTORE_GUARDED_CATEGORY = "remote_photo_guarded"

internal fun WardrobeSyncSnapshot.guardedPhotoRestoreIssues(): List<PhotoRestoreIssue> = photos.mapNotNull { photo ->
    val category = photo.restoreFailureCategory?.takeIf(String::isNotBlank) ?: return@mapNotNull null
    PhotoRestoreIssue(
        garmentId = photo.garmentId,
        category = category,
        message = photo.restoreFailureMessage,
        blobPath = photo.blobPath,
        byteSize = photo.byteSize,
        mimeType = photo.mimeType,
        byteMagic = photo.byteMagic,
        contentHash = photo.contentHash,
    )
}

internal fun WardrobeSyncSnapshot.finalUploadGuardReasonAfterRestore(): String? {
    val guardedCount = guardedPhotoRestoreIssues().size
    return if (guardedCount > 0) "guarded_remote_photos count=$guardedCount" else null
}

internal fun GarmentPhotoRecord.restoreDiagnosticEvent(): String {
    val status = when {
        restoreFailureCategory != null -> "guarded:$restoreFailureCategory"
        !restoredLocalUri.isNullOrBlank() -> "fetched_importable"
        else -> "metadata_only"
    }
    return buildString {
        append("photo_restore status=$status garmentId=$garmentId blobPath=$blobPath")
        byteSize?.let { append(" byteSize=$it") }
        mimeType?.takeIf(String::isNotBlank)?.let { append(" mimeType=$it") }
        contentHash?.takeIf(String::isNotBlank)?.let { append(" contentHash=${it.take(12)}") }
        byteMagic?.takeIf(String::isNotBlank)?.let { append(" byteMagic=${it.imageMagicClassification()}") }
        if (decodedWidth != null && decodedHeight != null) append(" decoded=${decodedWidth}x$decodedHeight")
        append(" restoredLocalUri=${!restoredLocalUri.isNullOrBlank()}")
        restoreFailureMessage?.takeIf(String::isNotBlank)?.let { message ->
            append(" message=${sanitizeDiagnosticMessage(message)}")
        }
    }.let(::sanitizeDiagnosticMessage)
}

internal fun RestoredPhotoApplyResult.toDiagnosticEvent(): String {
    val before = rowBefore
    val after = rowAfter
    return buildString {
        append("photo_restore_apply_result garmentId=$garmentId applied=$applied")
        if (this@toDiagnosticEvent is RestoredPhotoApplyResult.NotApplied) {
            append(" reason=$reason")
        } else {
            append(" reason=applied")
        }
        append(" daoRowsUpdated=$daoRowsUpdated expectedRevision=$expectedRevision")
        append(" restoredUriPresent=$restoredUriPresent restoredUriUsable=$importedPhotoUsable")
        append(" localRowPresentBefore=${before != null}")
        before?.let {
            append(" localRevisionBefore=${it.revision}")
            append(" statusBefore=${it.syncStatus}")
            append(" guardBefore=${it.photoRestoreGuarded}")
            append(" retryAttemptBefore=${it.retryAttemptCount}")
            append(" retryAfterBefore=${it.retryAfterEpochMillis}")
            append(" retryDeadlineBefore=${it.retryDeadlineEpochMillis}")
            append(" dirtyBefore=${it.syncDirtyAtEpochMillis != null}")
            append(" photoUriPresentBefore=${!it.photoUri.isNullOrBlank()}")
        }
        append(" localRowPresentAfter=${after != null}")
        after?.let {
            append(" localRevisionAfter=${it.revision}")
            append(" statusAfter=${it.syncStatus}")
            append(" guardAfter=${it.photoRestoreGuarded}")
            append(" retryAttemptAfter=${it.retryAttemptCount}")
            append(" retryAfterAfter=${it.retryAfterEpochMillis}")
            append(" retryDeadlineAfter=${it.retryDeadlineEpochMillis}")
            append(" dirtyAfter=${it.syncDirtyAtEpochMillis != null}")
            append(" photoUriPresentAfter=${!it.photoUri.isNullOrBlank()}")
        }
        append(" blobPath=$blobPath")
    }.let(::sanitizeDiagnosticMessage)
}

internal fun Throwable.restoreFetchFailureStatus(): CloudRestoreStatus =
    if (diagnosticCategory().startsWith("network_")) CloudRestoreStatus.Offline else CloudRestoreStatus.Failed

private fun sanitizeDiagnosticMessage(message: String): String = message
    .replace(Regex("Bearer\\s+[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE), "Bearer <redacted>")
    .replace(Regex("(?i)(access[_-]?token|refresh[_-]?token|id[_-]?token|authorization)=\\S+"), "\$1=<redacted>")
    .replace(Regex("(?i)\\b(first32|last32|readbackFirst32|readbackLast32)=[0-9a-f]+"), "\$1=<redacted>")
    .replace(Regex("(?i)\\b(sourceMagic|magic)=[0-9a-f]+"), "\$1=<redacted>")
    .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email-redacted>")
    .replace(Regex("(^|\\s)(/[^\\s:]+(?:/[^\\s:]+)+)")) { match -> "${match.groupValues[1]}<path-redacted>" }
    .take(MAX_DIAGNOSTIC_MESSAGE_CHARS)

private fun String.imageMagicClassification(): String {
    val hex = filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.lowercase()
    return when {
        hex.startsWith("ffd8ff") -> "jpeg_signature"
        hex.startsWith("89504e470d0a1a0a") -> "png_signature"
        hex.length >= 24 && hex.startsWith("52494646") && hex.substring(16, 24) == "57454250" -> "webp_signature"
        hex.startsWith("474946383761") || hex.startsWith("474946383961") -> "gif_signature"
        else -> "unknown_signature"
    }
}

private val garmentEntityTypes = setOf("garment", "clothing_item", "item")
private val categoryEntityTypes = setOf("tag_category", "category")
private val tagEntityTypes = setOf("garment_tag", "tag")
private val mainColorEntityTypes = setOf("main_color", "palette_color", "color")

private const val RESTORE_STEP_AUTH_CHECKED = 1
private const val RESTORE_STEP_LOCAL_EXPORTED = 2
private const val RESTORE_STEP_REMOTE_FETCH_STARTED = 3
private const val RESTORE_STEP_REMOTE_FETCHED = 4
private const val RESTORE_STEP_VALIDATED = 5
private const val RESTORE_STEP_MERGED = 6
private const val RESTORE_STEP_IMPORT_STARTED = 7
private const val RESTORE_STEP_LOCAL_SAVED = 8
private const val RESTORE_STEP_UPLOAD_STARTED = 9
private const val RESTORE_STEP_FINAL_SYNCED = 11
private const val RESTORE_TOTAL_STEPS = 12
private const val MAX_DIAGNOSTIC_MESSAGE_CHARS = 1_200

