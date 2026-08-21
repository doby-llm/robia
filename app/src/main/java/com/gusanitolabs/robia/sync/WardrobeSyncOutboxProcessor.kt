package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.DriveBackupDeletionState
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
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
 * the source of truth and the processor uploads the local snapshot without fetching Drive first.
 * Drive -> phone restore is reserved for an explicit setup/restore request on a fresh local install.
 * This prevents an empty reinstall from overwriting Drive while avoiding stale cloud overwrites.
 */
class WardrobeSyncOutboxProcessor(
    private val settingsRepository: SettingsRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val snapshotRepository: LocalWardrobeSyncSnapshotRepository,
    private val driveRepository: DriveWardrobeRepository,
    private val restoreSyncLogRepository: RestoreSyncLogRepository = NoOpRestoreSyncLogRepository,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WardrobeSyncGateway {
    private val mutex = Mutex()
    private val isProcessing = MutableStateFlow(false)
    private val hasGuardedPhotoRestoreIssues = MutableStateFlow(false)
    private val restoreProgress = MutableStateFlow<CloudRestoreProgress?>(null)
    private val mutableState = MutableStateFlow(WardrobeSyncState.notConfigured())
    private var scheduledWorkJob: Job? = null
    private var retryWakeUpJob: Job? = null
    private var hasAttemptedFreshInstallRestore = false
    private var restoreAttemptCounter = 0
    private var lastLoggedSettings: RobiaSettings? = null

    override val state: Flow<WardrobeSyncState> = mutableState
    override val restoreSyncLogText: Flow<String> = restoreSyncLogRepository.text

    init {
        scope.launch(dispatcher) {
            wardrobeRepository.observeGuardedPhotoRestoreCount().collect { count ->
                hasGuardedPhotoRestoreIssues.value = count > 0
            }
        }
        scope.launch(dispatcher) {
            wardrobeRepository.recoverStaleRunningSyncWork(System.currentTimeMillis() - STALE_RUNNING_TIMEOUT_MILLIS)
            scheduleRetryWakeUp()
            combine(
                settingsRepository.settings,
                wardrobeRepository.observePendingGarmentSyncCount(),
                wardrobeRepository.observeGarmentSyncAttentionCount(),
                wardrobeRepository.observePendingMetadataSyncCount(),
                wardrobeRepository.observeMetadataSyncAttentionCount(),
                isProcessing,
                hasGuardedPhotoRestoreIssues,
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
                    connectionStatus = settings.driveSyncConnectionStatus,
                    pendingOperationCount = pendingCount + metadataPendingCount,
                    attentionOperationCount = attentionCount + metadataAttentionCount,
                    isProcessing = processing,
                    needsPhotoRestoreAttention = photoAttention,
                )
            }.combine(restoreProgress) { inputs, progress ->
                inputs.connectionStatus.toWardrobeSyncState(
                    pendingOperationCount = inputs.pendingOperationCount,
                    attentionOperationCount = inputs.attentionOperationCount,
                    isProcessing = inputs.isProcessing,
                    needsPhotoRestoreAttention = inputs.needsPhotoRestoreAttention,
                    restoreProgress = progress,
                )
            }.collect { nextState ->
                mutableState.value = nextState
                scheduleWorkFor(nextState)
            }
        }
    }

    private fun scheduleWorkFor(nextState: WardrobeSyncState) {
        when {
            (nextState.connectionStatus == DriveSyncConnectionStatus.Connected ||
                nextState.connectionStatus == DriveSyncConnectionStatus.NeedsAttention) &&
                nextState.pendingOperationCount > 0 &&
                !isProcessing.value &&
                !hasGuardedPhotoRestoreIssues.value -> launchSyncWork { processPendingGarments() }

            nextState.connectionStatus == DriveSyncConnectionStatus.Connected &&
                nextState.pendingOperationCount == 0 &&
                !isProcessing.value -> launchSyncWork { restoreFreshInstallOnceIfNeeded() }

            nextState.pendingOperationCount > 0 -> launchSyncWork {
                markBlockedForCurrentSetupState(nextState.connectionStatus)
            }
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
                deleteCloudBackup()
                return@withContext
            }
            when (settingsRepository.settings.first().driveSyncConnectionStatus) {
                DriveSyncConnectionStatus.Connected,
                DriveSyncConnectionStatus.Syncing,
                DriveSyncConnectionStatus.NeedsAttention -> when (operation) {
                    is WardrobeSyncOperation.RetryRestoredPhoto -> retryRestoredPhoto(operation.garmentId)
                    else -> processPendingGarments(
                        forceSnapshot = operation.affectedGarmentIds().isEmpty(),
                        forceImport = operation is WardrobeSyncOperation.ImportFullSnapshot,
                    )
                }
                DriveSyncConnectionStatus.Disconnected -> markOperationAuthBlocked(operation)
                DriveSyncConnectionStatus.Disabled,
                DriveSyncConnectionStatus.NotConfigured -> markOperationSetupRequired(operation)
            }
        }
    }

    override suspend fun clearRestoreSyncLog() {
        withContext(dispatcher) { restoreSyncLogRepository.clear() }
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
                restoreSyncLogRepository.append(
                    RestoreSyncLogEvent(
                        correlationId = retryCorrelationId,
                        category = "photo_restore_retry",
                        level = "warn",
                        phase = null,
                        status = null,
                        message = "retry rejected reason=not_eligible_or_duplicate_or_backoff_or_exhausted",
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
                        val applied = snapshotRepository.applyRestoredPhoto(result.value, retryRevision)
                        restoreSyncLogRepository.append(
                            RestoreSyncLogEvent(
                                correlationId = retryCorrelationId,
                                category = "photo_restore_apply",
                                phase = CloudRestorePhase.Applying,
                                status = if (applied) CloudRestoreStatus.Running else CloudRestoreStatus.Failed,
                                message = "retry apply result applied=$applied",
                                garmentId = garmentId,
                            ),
                        )
                        if (!applied) {
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
                        wardrobeRepository.markGarmentPhotoRestoreFailed(
                            garmentId,
                            "$MISSING_RESTORED_PHOTO_MESSAGE ${result.throwable.message ?: "Drive photo download failed."}",
                        )
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
                    wardrobeRepository.markGarmentPhotoRestoreFailed(
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

    private suspend fun restoreFreshInstallOnceIfNeeded() {
        if (hasAttemptedFreshInstallRestore || restoreProgress.value?.isRetryableTerminal == true) return

        val settings = settingsRepository.settings.first()
        if (settings.driveFreshInstallRestoreAttempted) {
            hasAttemptedFreshInstallRestore = true
            return
        }

        val localSnapshot = snapshotRepository.exportSnapshot()
        if (!localSnapshot.isFreshInstallSnapshot()) {
            // Once the phone has user wardrobe data, it is authoritative and must not be overwritten.
            hasAttemptedFreshInstallRestore = true
            settingsRepository.markDriveFreshInstallRestoreAttempted()
            return
        }

        processPendingGarments(forceSnapshot = true, forceImport = true)

        // Do not burn the one-shot bootstrap attempt on a retryable Drive/photo failure. Keeping the
        // flag false lets a later explicit retry or reconnect re-enter the restore path instead of
        // leaving a fresh install permanently empty/placeholder-only.
        if (restoreProgress.value?.isRetryableTerminal != true) {
            hasAttemptedFreshInstallRestore = true
            settingsRepository.markDriveFreshInstallRestoreAttempted()
        }
    }

    private suspend fun processPendingGarments(
        forceSnapshot: Boolean = false,
        forceImport: Boolean = false,
    ) {
        mutex.withLock {
            if (settingsRepository.settings.first().driveSyncConnectionStatus != DriveSyncConnectionStatus.Connected) {
                return@withLock
            }

            val pendingWork = wardrobeRepository.pendingGarmentSyncWork()
            val pendingMetadataWork = wardrobeRepository.pendingMetadataSyncWork()
            if (pendingWork.isEmpty() && pendingMetadataWork.isEmpty() && !forceSnapshot && !forceImport) return@withLock
            // Never lock ordinary outbox work into Running while a guarded remote photo protects the
            // current Drive snapshot. Its targeted retry is the only work allowed to resolve that guard.
            if (hasGuardedPhotoRestoreIssues.value && !forceImport) return@withLock

            isProcessing.value = true
            val lockedWork = pendingWork.filter { work ->
                wardrobeRepository.markGarmentSyncing(work.id, work.revision, System.currentTimeMillis())
            }
            val lockedMetadataWork = pendingMetadataWork.filter { work ->
                wardrobeRepository.markMetadataSyncing(work)
            }
            if (lockedWork.isEmpty() && lockedMetadataWork.isEmpty() && !forceSnapshot && !forceImport) {
                isProcessing.value = false
                return@withLock
            }

            var keepTerminalProgress = false
            val syncCorrelationId = UUID.randomUUID().toString().take(8)
            val syncStartedAt = System.currentTimeMillis()
            restoreSyncLogRepository.append(
                RestoreSyncLogEvent(
                    correlationId = syncCorrelationId,
                    category = "sync",
                    phase = null,
                    status = CloudRestoreStatus.Running,
                    message = "sync started forceSnapshot=$forceSnapshot forceImport=$forceImport garments=${lockedWork.size} metadata=${lockedMetadataWork.size}",
                ),
            )
            try {
                val result = withTimeout(SYNC_OPERATION_TIMEOUT_MILLIS) {
                    val localSnapshot = snapshotRepository.exportSnapshot()
                    when (
                        SyncDirectionPolicy.resolve(
                            localSnapshot = localSnapshot,
                            explicitRestoreRequested = forceImport,
                        )
                    ) {
                        SyncDirection.UploadLocalSnapshot -> syncUploadLocalSnapshot(localSnapshot)
                        SyncDirection.RestoreRemoteThenUpload -> syncRestoreRemoteThenUpload(localSnapshot)
                        SyncDirection.SkipEmptyLocalUpload -> SyncCycleResult.NoBackup
                    }
                }
                when (result) {
                    is SyncCycleResult.Success -> {
                        markSynced(lockedWork, lockedMetadataWork)
                        recordSyncEnded(syncCorrelationId, syncStartedAt, "success guarded_photos=${result.guardedPhotoCount}")
                    }
                    SyncCycleResult.Attention -> {
                        markFailedRetryable(lockedWork, lockedMetadataWork)
                        recordSyncEnded(syncCorrelationId, syncStartedAt, "attention")
                    }
                    SyncCycleResult.NoBackup -> {
                        markSynced(lockedWork, lockedMetadataWork)
                        recordSyncEnded(syncCorrelationId, syncStartedAt, "no_backup")
                    }
                    is SyncCycleResult.Blocked -> {
                        keepTerminalProgress = true
                        markBlocked(lockedWork, lockedMetadataWork, result.reason)
                        recordSyncEnded(syncCorrelationId, syncStartedAt, "blocked reason=${result.reason.name}", level = "warn")
                    }
                    is SyncCycleResult.Failure -> {
                        keepTerminalProgress = true
                        markFailedRetryable(lockedWork, lockedMetadataWork)
                        recordSyncEnded(syncCorrelationId, syncStartedAt, "failure", level = "error")
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                keepTerminalProgress = true
                recordSyncEnded(syncCorrelationId, syncStartedAt, "timeout", level = "error", throwable = timeout)
                releaseClaimedWork(
                    lockedWork = lockedWork,
                    lockedMetadataWork = lockedMetadataWork,
                    timeoutMessage = SYNC_OPERATION_TIMEOUT_MESSAGE,
                    terminalStatus = CloudRestoreStatus.Failed,
                )
            } catch (cancellation: CancellationException) {
                keepTerminalProgress = true
                recordSyncEnded(syncCorrelationId, syncStartedAt, "interrupted", level = "warn", throwable = cancellation)
                releaseClaimedWork(
                    lockedWork = lockedWork,
                    lockedMetadataWork = lockedMetadataWork,
                    timeoutMessage = SYNC_INTERRUPTED_MESSAGE,
                    terminalStatus = CloudRestoreStatus.Interrupted,
                )
                throw cancellation
            } catch (throwable: Throwable) {
                keepTerminalProgress = true
                recordSyncEnded(syncCorrelationId, syncStartedAt, "failure", level = "error", throwable = throwable)
                releaseClaimedWork(
                    lockedWork = lockedWork,
                    lockedMetadataWork = lockedMetadataWork,
                    timeoutMessage = throwable.message ?: SYNC_FAILED_MESSAGE,
                    terminalStatus = CloudRestoreStatus.Failed,
                )
            } finally {
                isProcessing.value = false
                if (!keepTerminalProgress) {
                    restoreProgress.value = null
                }
                scheduleRetryWakeUp()
            }
        }
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

    /** Persists terminal retry state even when the owning coroutine was cancelled. */
    private suspend fun releaseClaimedWork(
        lockedWork: List<PendingGarmentSyncWork>,
        lockedMetadataWork: List<PendingMetadataSyncWork>,
        timeoutMessage: String,
        terminalStatus: CloudRestoreStatus,
    ) = withContext(NonCancellable) {
        markFailedRetryable(lockedWork, lockedMetadataWork, timeoutMessage)
        restoreProgress.value = restoreProgress.value?.copy(status = terminalStatus, message = timeoutMessage)
    }

    /** One delayed wake-up per persisted backoff boundary; never poll or hot-retry. */
    private fun scheduleRetryWakeUp() {
        retryWakeUpJob?.cancel()
        retryWakeUpJob = scope.launch(dispatcher) {
            val retryAt = wardrobeRepository.nextRunnableSyncRetryEpochMillis() ?: return@launch
            delay((retryAt - System.currentTimeMillis()).coerceAtLeast(0L))
            if (!isProcessing.value) processPendingGarments()
        }
    }

    private suspend fun syncUploadLocalSnapshot(localSnapshot: WardrobeSyncSnapshot): SyncCycleResult {
        // A missing restored blob must not be converted into a permanent Drive deletion by a later
        // normal sync. Leave ordinary work pending until its targeted recovery reaches a terminal success.
        if (snapshotRepository.hasGuardedPhotoRestoreIssues()) return SyncCycleResult.Attention
        // Local edits own the snapshot once the phone has user data; do not fetch stale Drive data here.
        return when (driveRepository.upsertSnapshot(localSnapshot.sortedDeterministically())) {
            is DriveSyncResult.Success -> SyncCycleResult.Success(guardedPhotoCount = 0)
            is DriveSyncResult.Blocked -> SyncCycleResult.Blocked(DriveSyncDisabledReason.UserNotConnected)
            is DriveSyncResult.Failure -> SyncCycleResult.Failure
        }
    }

    private suspend fun syncRestoreRemoteThenUpload(localSnapshot: WardrobeSyncSnapshot): SyncCycleResult {
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
                return SyncCycleResult.Blocked(result.reason)
            }
            is DriveSyncResult.Failure -> {
                diagnostics.recordFailure(result.throwable)
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Downloading,
                    completedWork = CloudRestorePhase.Downloading.completedRestoreWork,
                    status = result.throwable.restoreFetchFailureStatus(),
                )
                return SyncCycleResult.Failure
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
            return SyncCycleResult.Failure
        }

        val mergedSnapshot = mergeSnapshots(localSnapshot, remoteSnapshot)
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
        val importResult = if (mergedSnapshot.hasUserData()) {
            snapshotRepository.importSnapshot(mergedSnapshot)
        } else {
            ImportSnapshotResult(restoredGarmentCount = 0, guardedPhotoCount = 0)
        }
        diagnostics.recordLocalSave(importResult)

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
            return SyncCycleResult.Success(importResult.guardedPhotoCount)
        }
        if (!mergedSnapshot.hasUserData()) {
            diagnostics.recordFinalUploadSkipped("no_remote_user_data")
            restoreProgress.value = diagnostics.progress(
                phase = CloudRestorePhase.Complete,
                completedWork = RESTORE_TOTAL_STEPS,
            )
            return SyncCycleResult.NoBackup
        }

        diagnostics.recordFinalUploadAttempted()
        restoreProgress.value = diagnostics.progress(
            phase = CloudRestorePhase.Uploading,
            completedWork = RESTORE_STEP_UPLOAD_STARTED,
        )

        return when (val uploadResult = driveRepository.upsertSnapshot(mergedSnapshot)) {
            is DriveSyncResult.Success -> {
                diagnostics.recordFinalUploadSucceeded()
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Uploading,
                    completedWork = RESTORE_STEP_FINAL_SYNCED,
                )
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Complete,
                    completedWork = RESTORE_TOTAL_STEPS,
                )
                SyncCycleResult.Success(importResult.guardedPhotoCount)
            }
            is DriveSyncResult.Blocked -> {
                diagnostics.recordBlocked(uploadResult.reason, uploadResult.message, finalUploadSucceeded = false)
                // The fresh-install restore is already transactionally applied at this point. A final
                // cloud refresh is best-effort; surfacing it as a blocking restore failure strands the
                // user on the progress overlay even though the restored wardrobe is safe locally.
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Complete,
                    completedWork = RESTORE_TOTAL_STEPS,
                )
                SyncCycleResult.Success(importResult.guardedPhotoCount)
            }
            is DriveSyncResult.Failure -> {
                diagnostics.recordFailure(uploadResult.throwable, finalUploadSucceeded = false)
                // The fresh-install restore is already transactionally applied at this point. A final
                // cloud refresh is best-effort; surfacing it as a blocking restore failure strands the
                // user on the progress overlay even though the restored wardrobe is safe locally.
                restoreProgress.value = diagnostics.progress(
                    phase = CloudRestorePhase.Complete,
                    completedWork = RESTORE_TOTAL_STEPS,
                )
                SyncCycleResult.Success(importResult.guardedPhotoCount)
            }
        }
    }

    private suspend fun markOperationAuthBlocked(operation: WardrobeSyncOperation) {
        operation.affectedGarmentIds().forEach { id ->
            wardrobeRepository.markGarmentSyncAuthBlocked(id)
        }
        if (operation.affectedGarmentIds().isEmpty()) {
            wardrobeRepository.pendingMetadataSyncWork().forEach { work -> wardrobeRepository.markMetadataSyncAuthBlocked(work) }
        }
    }

    private suspend fun markBlockedForCurrentSetupState(connectionStatus: DriveSyncConnectionStatus) {
        val pendingWork = wardrobeRepository.pendingGarmentSyncWork()
        val pendingMetadataWork = wardrobeRepository.pendingMetadataSyncWork()
        when (connectionStatus) {
            DriveSyncConnectionStatus.Disconnected -> {
                pendingWork.forEach { work -> wardrobeRepository.markGarmentSyncAuthBlocked(work.id) }
                pendingMetadataWork.forEach { work -> wardrobeRepository.markMetadataSyncAuthBlocked(work) }
            }
            DriveSyncConnectionStatus.Disabled,
            DriveSyncConnectionStatus.NotConfigured -> markFailedRetryable(pendingWork, pendingMetadataWork)
            DriveSyncConnectionStatus.Connected,
            DriveSyncConnectionStatus.Syncing,
            DriveSyncConnectionStatus.NeedsAttention -> Unit
        }
    }

    private suspend fun markOperationSetupRequired(operation: WardrobeSyncOperation) {
        val workById = wardrobeRepository.pendingGarmentSyncWork().associateBy(PendingGarmentSyncWork::id)
        operation.affectedGarmentIds().forEach { id ->
            workById[id]?.let { work -> wardrobeRepository.markGarmentSyncFailedRetryable(work.id, work.revision) }
        }
        if (operation.affectedGarmentIds().isEmpty()) {
            wardrobeRepository.pendingMetadataSyncWork().forEach { work -> wardrobeRepository.markMetadataSyncFailedRetryable(work) }
        }
    }

    private suspend fun markSynced(work: List<PendingGarmentSyncWork>, metadataWork: List<PendingMetadataSyncWork>) {
        val now = System.currentTimeMillis()
        work.forEach { item -> wardrobeRepository.markGarmentSynced(item.id, item.revision, now) }
        metadataWork.forEach { item -> wardrobeRepository.markMetadataSynced(item, now) }
    }

    private suspend fun markBlocked(
        work: List<PendingGarmentSyncWork>,
        metadataWork: List<PendingMetadataSyncWork>,
        reason: DriveSyncDisabledReason,
    ) {
        when (reason) {
            DriveSyncDisabledReason.UserNotConnected,
            DriveSyncDisabledReason.AccountBindingConflict -> {
                work.forEach { item -> wardrobeRepository.markGarmentSyncAuthBlocked(item.id) }
                metadataWork.forEach { item -> wardrobeRepository.markMetadataSyncAuthBlocked(item) }
            }
            DriveSyncDisabledReason.GoogleCloudSetupRequired,
            DriveSyncDisabledReason.OAuthClientMissing,
            DriveSyncDisabledReason.UnsafeLocalState -> markFailedRetryable(work, metadataWork)
        }
    }

    private suspend fun markFailedRetryable(
        work: List<PendingGarmentSyncWork>,
        metadataWork: List<PendingMetadataSyncWork> = emptyList(),
        message: String? = null,
    ) {
        work.forEach { item -> wardrobeRepository.markGarmentSyncFailedRetryable(item.id, item.revision, message) }
        metadataWork.forEach { item -> wardrobeRepository.markMetadataSyncFailedRetryable(item, message) }
    }

    private fun DriveSyncConnectionStatus.toWardrobeSyncState(
        pendingOperationCount: Int,
        attentionOperationCount: Int,
        isProcessing: Boolean,
        needsPhotoRestoreAttention: Boolean,
        restoreProgress: CloudRestoreProgress?,
    ): WardrobeSyncState {
        val displayStatus = when {
            isProcessing && this == DriveSyncConnectionStatus.Connected -> DriveSyncConnectionStatus.Syncing
            needsPhotoRestoreAttention && this == DriveSyncConnectionStatus.Connected -> DriveSyncConnectionStatus.NeedsAttention
            attentionOperationCount > 0 && this == DriveSyncConnectionStatus.Connected -> DriveSyncConnectionStatus.NeedsAttention
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
            pendingOperationCount = pendingOperationCount,
            restoreProgress = restoreProgress,
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
        is WardrobeSyncOperation.UpsertTaxonomy,
        is WardrobeSyncOperation.RecordTombstones,
        is WardrobeSyncOperation.DeleteCloudBackup -> emptySet()
    }
}

private sealed interface SyncCycleResult {
    data class Success(val guardedPhotoCount: Int) : SyncCycleResult
    data object Attention : SyncCycleResult
    data object NoBackup : SyncCycleResult
    data class Blocked(val reason: DriveSyncDisabledReason) : SyncCycleResult
    data object Failure : SyncCycleResult
}

private data class WardrobeSyncStateInputs(
    val connectionStatus: DriveSyncConnectionStatus,
    val pendingOperationCount: Int,
    val attentionOperationCount: Int,
    val isProcessing: Boolean,
    val needsPhotoRestoreAttention: Boolean,
)

private const val MAX_RETRY_ATTEMPTS = 3
private const val STALE_RUNNING_TIMEOUT_MILLIS = 15 * 60 * 1000L
private const val SYNC_OPERATION_TIMEOUT_MILLIS = 2 * 60 * 1000L
private const val PHOTO_RESTORE_RETRY_TIMEOUT_MILLIS = 60 * 1000L
private const val SYNC_OPERATION_TIMEOUT_MESSAGE = "Drive sync timed out. Retry is available after backoff."
private const val PHOTO_RESTORE_RETRY_TIMEOUT_MESSAGE = "Drive photo retry timed out."
private const val SYNC_INTERRUPTED_MESSAGE = "Drive sync was interrupted. Retry is available after backoff."
private const val SYNC_FAILED_MESSAGE = "Drive sync failed. Retry is available after backoff."
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
        event("local_save_completed restored_garments=${result.restoredGarmentCount} guarded_photos=${result.guardedPhotoCount}")
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
    RestoreRemoteThenUpload,
    SkipEmptyLocalUpload,
}

private object SyncDirectionPolicy {
    fun resolve(
        localSnapshot: WardrobeSyncSnapshot,
        explicitRestoreRequested: Boolean,
    ): SyncDirection = when {
        explicitRestoreRequested && localSnapshot.isFreshInstallSnapshot() -> SyncDirection.RestoreRemoteThenUpload
        localSnapshot.hasUserData() -> SyncDirection.UploadLocalSnapshot
        else -> SyncDirection.SkipEmptyLocalUpload
    }
}

private fun WardrobeSyncSnapshot.isFreshInstallSnapshot(): Boolean = !hasUserData()

private fun WardrobeSyncSnapshot.hasUserData(): Boolean =
    garments.isNotEmpty() ||
        photos.isNotEmpty() ||
        tombstones.isNotEmpty() ||
        taxonomies.categories.any { category -> DefaultTags.isCustomOrModifiedDefault(category.toDomain()) } ||
        taxonomies.tags.any { tag -> DefaultTags.isCustomOrModifiedDefault(tag.toDomain()) } ||
        taxonomies.mainColors.any { color -> DefaultTags.isCustomOrModifiedDefault(color.toDomain()) }

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

private fun mergeSnapshots(local: WardrobeSyncSnapshot, remote: WardrobeSyncSnapshot): WardrobeSyncSnapshot {
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
        byteMagic?.takeIf(String::isNotBlank)?.let { append(" byteMagic=${it.take(32)}") }
        if (decodedWidth != null && decodedHeight != null) append(" decoded=${decodedWidth}x$decodedHeight")
        append(" restoredLocalUri=${!restoredLocalUri.isNullOrBlank()}")
        restoreFailureMessage?.takeIf(String::isNotBlank)?.let { message ->
            append(" message=${sanitizeDiagnosticMessage(message)}")
        }
    }.let(::sanitizeDiagnosticMessage)
}

internal fun Throwable.restoreFetchFailureStatus(): CloudRestoreStatus =
    if (diagnosticCategory().startsWith("network_")) CloudRestoreStatus.Offline else CloudRestoreStatus.Failed

private fun sanitizeDiagnosticMessage(message: String): String = message
    .replace(Regex("Bearer\\s+[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE), "Bearer <redacted>")
    .replace(Regex("(?i)(access[_-]?token|refresh[_-]?token|id[_-]?token|authorization)=\\S+"), "\$1=<redacted>")
    .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email-redacted>")
    .replace(Regex("(^|\\s)(/[^\\s:]+(?:/[^\\s:]+)+)")) { match -> "${match.groupValues[1]}<path-redacted>" }
    .take(MAX_DIAGNOSTIC_MESSAGE_CHARS)

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
