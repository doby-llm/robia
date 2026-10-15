package com.gusanitolabs.robia.sync.fakes

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DriveBackupDeletionState
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.LanguagePreference
import com.gusanitolabs.robia.core.model.RobiaSettings
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.data.PendingGarmentSyncWork
import com.gusanitolabs.robia.data.PendingMetadataSyncWork
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.data.WardrobeRepository
import com.gusanitolabs.robia.sync.DriveWardrobeRepository
import com.gusanitolabs.robia.sync.ImportSnapshotResult
import com.gusanitolabs.robia.sync.RestoredPhotoApplyResult
import com.gusanitolabs.robia.sync.SyncWorkScheduler
import com.gusanitolabs.robia.sync.WardrobeSnapshotStore
import com.gusanitolabs.robia.sync.WardrobeSyncOutboxProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal const val FAKE_DEVICE_ID = "device-under-test"

private val ATTENTION_STATUSES = setOf("FailedRetryable", "NeedsUserAction", "AuthBlocked")

internal class FakeGarmentRow(val id: String, var revision: Long, var status: String, var failureMessage: String? = null)

/** In-memory garment sync rows. Only the calls the processor makes are modelled; the rest are not used by sync. */
internal class FakeWardrobeRepository : WardrobeRepository {
    val rows = linkedMapOf<String, FakeGarmentRow>()
    val failedRetryableCalls = mutableListOf<String>()
    /** When set, claiming this garment throws a cancellation, as if the work was cancelled mid-claim. */
    var cancelClaimOf: String? = null
    /** When set, the claim of this garment commits (Running) and then throws a cancellation, as if cancelled right after the write. */
    var cancelAfterClaimOf: String? = null
    val needsUserActions = mutableListOf<Pair<String, String>>()
    private val guardedIds = mutableSetOf<String>()
    private val pendingCount = MutableStateFlow(0)
    private val attentionCount = MutableStateFlow(0)
    private val guardedCount = MutableStateFlow(0)
    private val reasonCounts = mutableMapOf<String, MutableStateFlow<Int>>()
    var pendingCloudDeletion = false
    /** Thresholds passed to [recoverStaleRunningSyncWork], so tests can see when a cycle recovers stale claims. */
    val staleRecoveryThresholds = mutableListOf<Long>()

    fun addQueued(id: String, revision: Long = 1L) = put(id, revision, "Queued")

    fun addSynced(id: String, revision: Long = 1L) = put(id, revision, "Synced")

    fun markGuarded(id: String) {
        guardedIds += id
        publish()
    }

    fun statusOf(id: String): String? = rows[id]?.status

    private fun put(id: String, revision: Long, status: String) {
        rows[id] = FakeGarmentRow(id, revision, status)
        publish()
    }

    private fun publish() {
        pendingCount.value = rows.values.count { it.status == "Queued" || it.status == "Dirty" }
        attentionCount.value = rows.values.count { it.status in ATTENTION_STATUSES }
        guardedCount.value = guardedIds.size
        reasonCounts.forEach { (reason, flow) -> flow.value = needsUserActionCount(reason) }
    }

    private fun needsUserActionCount(reason: String): Int =
        rows.values.count { it.status == "NeedsUserAction" && it.failureMessage == reason }

    private fun setStatus(id: String, revision: Long, status: String, message: String? = null): Boolean {
        val row = rows[id]?.takeIf { it.revision == revision } ?: return false
        row.status = status
        row.failureMessage = message
        publish()
        return true
    }

    override fun observeActiveItems(): Flow<List<ClothingItem>> = error("not used by sync tests")
    override fun observeItem(id: String): Flow<ClothingItem?> = flowOf(null)
    override fun observePendingGarmentSyncCount(): Flow<Int> = pendingCount
    override fun observeGarmentSyncAttentionCount(): Flow<Int> = attentionCount
    override fun observeGuardedPhotoRestoreCount(): Flow<Int> = guardedCount
    override fun observePendingMetadataSyncCount(): Flow<Int> = flowOf(0)
    override fun observeMetadataSyncAttentionCount(): Flow<Int> = flowOf(0)
    override fun observeGarmentNeedsUserActionCount(reasonCode: String): Flow<Int> =
        reasonCounts.getOrPut(reasonCode) { MutableStateFlow(needsUserActionCount(reasonCode)) }

    override suspend fun pendingGarmentSyncWork(): List<PendingGarmentSyncWork> =
        rows.values.filter { it.status == "Queued" || it.status == "Dirty" }.map { PendingGarmentSyncWork(it.id, it.revision) }

    override suspend fun pendingMetadataSyncWork(): List<PendingMetadataSyncWork> = emptyList()
    override suspend fun nextRunnableSyncRetryEpochMillis(): Long? = null
    override suspend fun hasPendingCloudDeletion(): Boolean = pendingCloudDeletion
    override suspend fun recoverStaleRunningSyncWork(staleBeforeEpochMillis: Long): Int {
        staleRecoveryThresholds += staleBeforeEpochMillis
        return 0
    }
    override suspend fun recoverStaleGuardedPhotoSyncWork(staleBeforeEpochMillis: Long): Int = 0
    override suspend fun upsertItem(item: ClothingItem) = error("not used by sync tests")
    override suspend fun upsertItems(items: List<ClothingItem>) = error("not used by sync tests")
    override suspend fun archiveItem(id: String, updatedAtEpochMillis: Long) = error("not used by sync tests")
    override suspend fun archiveItems(ids: List<String>, updatedAtEpochMillis: Long) = error("not used by sync tests")

    override suspend fun markGarmentSyncing(id: String, revision: Long, startedAtEpochMillis: Long): Boolean {
        if (id == cancelClaimOf) throw CancellationException("cancelled while claiming $id")
        val claimed = rows[id]?.status == "Queued" && setStatus(id, revision, "Running")
        if (id == cancelAfterClaimOf) throw CancellationException("cancelled after claiming $id")
        return claimed
    }

    override suspend fun markGarmentSynced(id: String, revision: Long, syncedAtEpochMillis: Long): Boolean =
        setStatus(id, revision, "Synced")

    override suspend fun markGarmentSyncFailedRetryable(id: String, revision: Long, message: String?, now: Long): Boolean {
        failedRetryableCalls += id
        return setStatus(id, revision, "FailedRetryable", message)
    }

    override suspend fun markGarmentSyncAuthBlocked(id: String, message: String?): Boolean {
        val row = rows[id] ?: return false
        row.status = "AuthBlocked"
        row.failureMessage = message
        publish()
        return true
    }

    override suspend fun releaseGarmentSyncToQueued(id: String, revision: Long): Boolean =
        setStatus(id, revision, "Queued")

    override suspend fun releaseNeedsUserActionToQueued(reasonCode: String): Int {
        val released = rows.values.filter { it.status == "NeedsUserAction" && it.failureMessage == reasonCode && !guardedIds.contains(it.id) }
        released.forEach { row ->
            row.status = "Queued"
            row.failureMessage = null
        }
        publish()
        return released.size
    }

    override suspend fun keepGarmentWithoutRestoredPhoto(id: String, now: Long): Boolean {
        val row = rows[id]?.takeIf { it.status == "NeedsUserAction" && guardedIds.contains(id) } ?: return false
        guardedIds -= id
        row.status = "Queued"
        row.failureMessage = null
        publish()
        return true
    }

    override suspend fun markGarmentNeedsUserAction(id: String, revision: Long, reasonCode: String): Boolean {
        needsUserActions += id to reasonCode
        return setStatus(id, revision, "NeedsUserAction", reasonCode)
    }

    override suspend fun claimGarmentPhotoRestoreRetry(id: String, startedAtEpochMillis: Long, now: Long): Long? = null
    override suspend fun markGarmentPhotoRestoreFailed(id: String, message: String, now: Long): Boolean = false
    override suspend fun markGarmentPhotoRestoreTransient(id: String, message: String): Boolean = false
    override suspend fun guardedPhotoRetryCandidateIds(now: Long): List<String> = emptyList()
    override suspend fun markMetadataSyncing(work: PendingMetadataSyncWork): Boolean = false
    override suspend fun markMetadataSynced(work: PendingMetadataSyncWork, syncedAtEpochMillis: Long): Boolean = false
    override suspend fun markMetadataSyncFailedRetryable(work: PendingMetadataSyncWork, message: String?): Boolean = false
    override suspend fun markMetadataSyncAuthBlocked(work: PendingMetadataSyncWork, message: String?): Boolean = false
    override suspend fun releaseMetadataSyncToQueued(work: PendingMetadataSyncWork): Boolean = false
}

internal class FakeSettingsRepository(
    status: DriveSyncConnectionStatus = DriveSyncConnectionStatus.Connected,
) : SettingsRepository {
    private val current = MutableStateFlow(
        RobiaSettings(driveSyncConnectionStatus = status, driveFreshInstallRestoreAttempted = true),
    )
    private val revisionState = MutableStateFlow(0L)
    private val conflictState = MutableStateFlow(false)
    private val conflictRevisionState = MutableStateFlow(0L)
    private val modelPromptState = MutableStateFlow(false)
    private val mobileDataState = MutableStateFlow(false)

    override val settings: Flow<RobiaSettings> = current
    override val lastKnownRemoteRevision: Flow<Long> = revisionState
    override val syncConflict: Flow<Boolean> = conflictState
    override val syncConflictRemoteRevision: Flow<Long> = conflictRevisionState
    override val modelPromptShown: Flow<Boolean> = modelPromptState
    override val modelMobileDataApproved: Flow<Boolean> = mobileDataState

    private fun update(transform: (RobiaSettings) -> RobiaSettings) {
        current.value = transform(current.value)
    }

    override suspend fun setLanguagePreference(languagePreference: LanguagePreference) =
        update { it.copy(languagePreference = languagePreference) }

    override suspend fun setDeveloperModeUnlocked(unlocked: Boolean) = update { it.copy(developerModeUnlocked = unlocked) }
    override suspend fun setDeveloperModeEnabled(enabled: Boolean) = update { it.copy(developerModeEnabled = enabled) }
    override suspend fun setDriveSyncConnectionStatus(status: DriveSyncConnectionStatus) =
        update { it.copy(driveSyncConnectionStatus = status) }

    override suspend fun setDriveBackupDeletionState(state: DriveBackupDeletionState) =
        update { it.copy(driveBackupDeletionState = state) }

    override suspend fun pauseSyncForDriveBackupDeletion() =
        update { it.copy(driveSyncConnectionStatus = DriveSyncConnectionStatus.Disabled) }

    override suspend fun markCloudSetupPromptInteracted() = update { it.copy(cloudSetupPromptInteracted = true) }
    override suspend fun markDriveFreshInstallRestoreAttempted() =
        update { it.copy(driveFreshInstallRestoreAttempted = true) }

    override suspend fun setModelPromptShown(shown: Boolean) {
        modelPromptState.value = shown
    }

    override suspend fun setModelMobileDataApproved(approved: Boolean) {
        mobileDataState.value = approved
    }

    override suspend fun deviceId(): String = FAKE_DEVICE_ID

    override suspend fun setLastKnownRemoteRevision(revision: Long) {
        revisionState.value = revision
    }

    override suspend fun setSyncConflict(conflict: Boolean, remoteRevision: Long) {
        conflictState.value = conflict
        conflictRevisionState.value = if (conflict) remoteRevision else 0L
    }
}

/** Phone-side snapshot store. `local` is what the phone currently holds; imports and replacements are recorded. */
internal class FakeWardrobeSnapshotStore(
    var local: WardrobeSyncSnapshot = WardrobeSyncSnapshot(),
) : WardrobeSnapshotStore {
    val imports = mutableListOf<WardrobeSyncSnapshot>()
    val replacements = mutableListOf<WardrobeSyncSnapshot>()
    var replaceFailure: Throwable? = null

    override suspend fun exportSnapshot(generatedAtEpochMillis: Long): WardrobeSyncSnapshot = local

    override suspend fun importSnapshot(snapshot: WardrobeSyncSnapshot): ImportSnapshotResult {
        imports += snapshot
        local = snapshot
        return ImportSnapshotResult(snapshot.garments.size, snapshot.photos.size, guardedPhotoCount = 0)
    }

    override suspend fun applyRestoredPhoto(photo: com.gusanitolabs.robia.core.model.GarmentPhotoRecord, expectedRevision: Long): RestoredPhotoApplyResult =
        error("not used by sync tests")

    override suspend fun hasGuardedPhotoRestoreIssues(): Boolean = false

    override suspend fun replaceLocalWithRemote(snapshot: WardrobeSyncSnapshot): ImportSnapshotResult {
        // Worst case: the store changes and then throws. The processor must treat this as not applied.
        local = snapshot
        replaceFailure?.let { throw it }
        replacements += snapshot
        return ImportSnapshotResult(snapshot.garments.size, snapshot.photos.size, guardedPhotoCount = 0)
    }
}

internal class FakeSyncWorkScheduler : SyncWorkScheduler {
    var requestSyncCount = 0
    var ensurePeriodicCount = 0
    var cancelAllCount = 0

    override fun requestSync() {
        requestSyncCount++
    }

    override fun ensurePeriodic() {
        ensurePeriodicCount++
    }

    override fun cancelAll() {
        cancelAllCount++
    }
}

internal fun newTestProcessor(
    settings: FakeSettingsRepository = FakeSettingsRepository(),
    wardrobe: FakeWardrobeRepository = FakeWardrobeRepository(),
    snapshots: FakeWardrobeSnapshotStore = FakeWardrobeSnapshotStore(),
    drive: FakeDriveWardrobeRepository = FakeDriveWardrobeRepository(),
    scheduler: FakeSyncWorkScheduler = FakeSyncWorkScheduler(),
): WardrobeSyncOutboxProcessor = WardrobeSyncOutboxProcessor(
    settingsRepository = settings,
    wardrobeRepository = wardrobe,
    snapshotRepository = snapshots,
    driveRepository = drive,
    syncWorkScheduler = scheduler,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    dispatcher = Dispatchers.Unconfined,
)

/** Same wiring as [newTestProcessor], for tests that wrap the Drive fake (for example to block or cancel an upload). */
internal fun processorWithDrive(
    drive: DriveWardrobeRepository,
    wardrobe: FakeWardrobeRepository = FakeWardrobeRepository(),
    snapshots: FakeWardrobeSnapshotStore = FakeWardrobeSnapshotStore(),
    settings: FakeSettingsRepository = FakeSettingsRepository(),
): WardrobeSyncOutboxProcessor = WardrobeSyncOutboxProcessor(
    settingsRepository = settings,
    wardrobeRepository = wardrobe,
    snapshotRepository = snapshots,
    driveRepository = drive,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    dispatcher = Dispatchers.Unconfined,
)

internal fun snapshotWithGarments(vararg ids: String): WardrobeSyncSnapshot = WardrobeSyncSnapshot(
    garments = ids.map { id ->
        GarmentSyncRecord(
            id = id,
            name = id,
            notes = "",
            fitValue = null,
            isFavorite = false,
            isArchived = false,
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )
    },
)
