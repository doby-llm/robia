package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.GarmentTagMappingRecord
import com.gusanitolabs.robia.core.model.MainColor
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
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.data.WardrobeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Durable Room/DataStore-backed sync outbox processor.
 *
 * Normal sync is local-first: once the local wardrobe contains user data, Room/DataStore remains
 * the source of truth and the processor uploads the local snapshot without fetching Drive first.
 * Drive -> phone restore is reserved for an explicit setup/restore request on a fresh local install,
 * which preserves the clean reinstall invariant without letting stale cloud taxonomy resurrect
 * locally deleted colors/tags.
 */
class WardrobeSyncOutboxProcessor(
    private val settingsRepository: SettingsRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val snapshotRepository: LocalWardrobeSyncSnapshotRepository,
    private val driveRepository: DriveWardrobeRepository,
    scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WardrobeSyncGateway {
    private val mutex = Mutex()
    private val isProcessing = MutableStateFlow(false)
    private val needsPhotoRestoreAttention = MutableStateFlow(false)
    private val restoreProgress = MutableStateFlow<CloudRestoreProgress?>(null)
    private val mutableState = MutableStateFlow(WardrobeSyncState.notConfigured())

    override val state: Flow<WardrobeSyncState> = mutableState

    init {
        scope.launch(dispatcher) {
            combine(
                settingsRepository.settings,
                wardrobeRepository.observePendingGarmentSyncCount(),
                wardrobeRepository.observeGarmentSyncAttentionCount(),
                isProcessing,
                needsPhotoRestoreAttention,
            ) { settings, pendingCount, attentionCount, processing, photoAttention ->
                WardrobeSyncStateInputs(
                    connectionStatus = settings.driveSyncConnectionStatus,
                    pendingOperationCount = pendingCount,
                    attentionOperationCount = attentionCount,
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
                if ((nextState.connectionStatus == DriveSyncConnectionStatus.Connected ||
                        nextState.connectionStatus == DriveSyncConnectionStatus.NeedsAttention) &&
                    nextState.pendingOperationCount > 0
                ) {
                    processPendingGarments()
                } else if (nextState.pendingOperationCount > 0) {
                    markBlockedForCurrentSetupState(nextState.connectionStatus)
                }
            }
        }
    }

    override suspend fun enqueue(operation: WardrobeSyncOperation) {
        withContext(dispatcher) {
            when (settingsRepository.settings.first().driveSyncConnectionStatus) {
                DriveSyncConnectionStatus.Connected,
                DriveSyncConnectionStatus.Syncing,
                DriveSyncConnectionStatus.NeedsAttention -> processPendingGarments(
                    forceSnapshot = operation.affectedGarmentIds().isEmpty(),
                    forceImport = operation is WardrobeSyncOperation.ImportFullSnapshot,
                )
                DriveSyncConnectionStatus.Disconnected -> markOperationAuthBlocked(operation)
                DriveSyncConnectionStatus.Disabled,
                DriveSyncConnectionStatus.NotConfigured -> markOperationSetupRequired(operation)
            }
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
            if (pendingWork.isEmpty() && !forceSnapshot && !forceImport) return@withLock

            isProcessing.value = true
            val lockedWork = pendingWork.filter { work ->
                wardrobeRepository.markGarmentSyncing(work.id, work.revision)
            }
            if (lockedWork.isEmpty() && !forceSnapshot && !forceImport) {
                isProcessing.value = false
                return@withLock
            }

            var keepTerminalProgress = false
            try {
                val localSnapshot = snapshotRepository.exportSnapshot()
                val policy = SyncDirectionPolicy.resolve(
                    localSnapshot = localSnapshot,
                    explicitRestoreRequested = forceImport,
                )
                val result = when (policy) {
                    SyncDirection.UploadLocalSnapshot -> syncUploadLocalSnapshot(localSnapshot)
                    SyncDirection.RestoreRemoteThenUpload -> syncRestoreRemoteThenUpload(localSnapshot)
                    SyncDirection.SkipEmptyLocalUpload -> SyncCycleResult.NoBackup
                }
                when (result) {
                    is SyncCycleResult.Success -> {
                        needsPhotoRestoreAttention.value = result.guardedPhotoCount > 0
                        markSynced(lockedWork)
                    }
                    SyncCycleResult.NoBackup -> {
                        needsPhotoRestoreAttention.value = false
                        markSynced(lockedWork)
                    }
                    is SyncCycleResult.Blocked -> {
                        keepTerminalProgress = true
                        markBlocked(lockedWork, result.reason)
                    }
                    is SyncCycleResult.Failure -> {
                        keepTerminalProgress = true
                        markFailedRetryable(lockedWork)
                    }
                }
            } finally {
                isProcessing.value = false
                if (!keepTerminalProgress) {
                    restoreProgress.value = null
                }
            }
        }
    }

    private suspend fun syncUploadLocalSnapshot(localSnapshot: WardrobeSyncSnapshot): SyncCycleResult {
        // Normal sync is local-first: never fetch/import Drive over local edits once local data exists.
        return when (driveRepository.upsertSnapshot(localSnapshot.sortedDeterministically())) {
            is DriveSyncResult.Success -> SyncCycleResult.Success(guardedPhotoCount = 0)
            is DriveSyncResult.Blocked -> SyncCycleResult.Blocked(DriveSyncDisabledReason.UserNotConnected)
            is DriveSyncResult.Failure -> SyncCycleResult.Failure
        }
    }

    private suspend fun syncRestoreRemoteThenUpload(localSnapshot: WardrobeSyncSnapshot): SyncCycleResult {
        restoreProgress.value = CloudRestoreProgress(
            phase = CloudRestorePhase.Preparing,
            completedWork = RESTORE_STEP_PREPARING,
            totalWork = RESTORE_TOTAL_STEPS,
        )
        restoreProgress.value = CloudRestoreProgress(
            phase = CloudRestorePhase.Downloading,
            completedWork = RESTORE_STEP_DOWNLOADED,
            totalWork = RESTORE_TOTAL_STEPS,
        )
        val remoteSnapshot = when (val result = driveRepository.fetchSnapshot()) {
            is DriveSyncResult.Success -> result.value.sortedDeterministically()
            is DriveSyncResult.Blocked -> {
                restoreProgress.value = failedRestoreProgress(CloudRestorePhase.Downloading)
                return SyncCycleResult.Blocked(result.reason)
            }
            is DriveSyncResult.Failure -> {
                restoreProgress.value = offlineRestoreProgress(CloudRestorePhase.Downloading)
                return SyncCycleResult.Failure
            }
        }
        restoreProgress.value = CloudRestoreProgress(
            phase = CloudRestorePhase.Validating,
            completedWork = RESTORE_STEP_VALIDATED,
            totalWork = RESTORE_TOTAL_STEPS,
        )
        if (remoteSnapshot.metadata.schemaVersion > WARDROBE_SYNC_SCHEMA_VERSION) {
            restoreProgress.value = failedRestoreProgress(CloudRestorePhase.Validating)
            return SyncCycleResult.Failure
        }

        val mergedSnapshot = mergeSnapshots(localSnapshot, remoteSnapshot)
        restoreProgress.value = CloudRestoreProgress(
            phase = CloudRestorePhase.Applying,
            completedWork = RESTORE_STEP_APPLIED,
            totalWork = RESTORE_TOTAL_STEPS,
        )
        val importResult = if (mergedSnapshot.hasUserData()) {
            snapshotRepository.importSnapshot(mergedSnapshot)
        } else {
            ImportSnapshotResult(restoredGarmentCount = 0, guardedPhotoCount = 0)
        }

        if (!mergedSnapshot.hasUserData()) {
            restoreProgress.value = CloudRestoreProgress(
                phase = CloudRestorePhase.Complete,
                completedWork = RESTORE_TOTAL_STEPS,
                totalWork = RESTORE_TOTAL_STEPS,
            )
            return SyncCycleResult.NoBackup
        }

        restoreProgress.value = CloudRestoreProgress(
            phase = CloudRestorePhase.Uploading,
            completedWork = RESTORE_STEP_UPLOADED,
            totalWork = RESTORE_TOTAL_STEPS,
        )
        return when (driveRepository.upsertSnapshot(mergedSnapshot)) {
            is DriveSyncResult.Success -> {
                restoreProgress.value = CloudRestoreProgress(
                    phase = CloudRestorePhase.Complete,
                    completedWork = RESTORE_TOTAL_STEPS,
                    totalWork = RESTORE_TOTAL_STEPS,
                )
                SyncCycleResult.Success(importResult.guardedPhotoCount)
            }
            is DriveSyncResult.Blocked -> {
                restoreProgress.value = failedRestoreProgress(CloudRestorePhase.Uploading)
                SyncCycleResult.Blocked(DriveSyncDisabledReason.UserNotConnected)
            }
            is DriveSyncResult.Failure -> {
                restoreProgress.value = offlineRestoreProgress(CloudRestorePhase.Uploading)
                SyncCycleResult.Failure
            }
        }
    }

    private suspend fun markOperationAuthBlocked(operation: WardrobeSyncOperation) {
        operation.affectedGarmentIds().forEach { id ->
            wardrobeRepository.markGarmentSyncAuthBlocked(id)
        }
    }

    private suspend fun markBlockedForCurrentSetupState(connectionStatus: DriveSyncConnectionStatus) {
        val pendingWork = wardrobeRepository.pendingGarmentSyncWork()
        when (connectionStatus) {
            DriveSyncConnectionStatus.Disconnected -> pendingWork.forEach { work ->
                wardrobeRepository.markGarmentSyncAuthBlocked(work.id)
            }
            DriveSyncConnectionStatus.Disabled,
            DriveSyncConnectionStatus.NotConfigured -> markFailedRetryable(pendingWork)
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
    }

    private suspend fun markSynced(work: List<PendingGarmentSyncWork>) {
        val now = System.currentTimeMillis()
        work.forEach { item -> wardrobeRepository.markGarmentSynced(item.id, item.revision, now) }
    }

    private suspend fun markBlocked(work: List<PendingGarmentSyncWork>, reason: DriveSyncDisabledReason) {
        when (reason) {
            DriveSyncDisabledReason.UserNotConnected,
            DriveSyncDisabledReason.AccountBindingConflict -> work.forEach { item ->
                wardrobeRepository.markGarmentSyncAuthBlocked(item.id)
            }
            DriveSyncDisabledReason.GoogleCloudSetupRequired,
            DriveSyncDisabledReason.OAuthClientMissing,
            DriveSyncDisabledReason.UnsafeLocalState -> markFailedRetryable(work)
        }
    }

    private suspend fun markFailedRetryable(work: List<PendingGarmentSyncWork>) {
        work.forEach { item -> wardrobeRepository.markGarmentSyncFailedRetryable(item.id, item.revision) }
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
        is WardrobeSyncOperation.UpsertTaxonomy,
        is WardrobeSyncOperation.RecordTombstones -> emptySet()
    }
}

private sealed interface SyncCycleResult {
    data class Success(val guardedPhotoCount: Int) : SyncCycleResult
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

private fun failedRestoreProgress(phase: CloudRestorePhase): CloudRestoreProgress = CloudRestoreProgress(
    phase = phase,
    completedWork = phase.completedRestoreWork,
    totalWork = RESTORE_TOTAL_STEPS,
    status = CloudRestoreStatus.Failed,
)

private fun offlineRestoreProgress(phase: CloudRestorePhase): CloudRestoreProgress = CloudRestoreProgress(
    phase = phase,
    completedWork = phase.completedRestoreWork,
    totalWork = RESTORE_TOTAL_STEPS,
    status = CloudRestoreStatus.Offline,
)

private val CloudRestorePhase.completedRestoreWork: Int
    get() = when (this) {
        CloudRestorePhase.Preparing -> RESTORE_STEP_PREPARING
        CloudRestorePhase.Downloading -> RESTORE_STEP_DOWNLOADED
        CloudRestorePhase.Validating -> RESTORE_STEP_VALIDATED
        CloudRestorePhase.Applying -> RESTORE_STEP_APPLIED
        CloudRestorePhase.Uploading -> RESTORE_STEP_UPLOADED
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
    ).filterNot { category -> (tombstoneByCategoryId[category.id]?.revision ?: Long.MIN_VALUE) > category.revision }
    val activeCategoryIds = mergedCategories.map(TagCategorySyncRecord::id).toSet()
    val mergedTags = mergeByKey(local.taxonomies.tags + remote.taxonomies.tags, TagSyncRecord::id, TagSyncRecord::revision)
        .filterNot { tag -> (tombstoneByTagId[tag.id]?.revision ?: Long.MIN_VALUE) > tag.revision }
        .filter { tag -> tag.categoryId in activeCategoryIds }
    val activeTagIds = mergedTags.map(TagSyncRecord::id).toSet()
    val mergedMainColors = mergeByKey(
        local.taxonomies.mainColors + remote.taxonomies.mainColors,
        MainColorSyncRecord::id,
        MainColorSyncRecord::revision,
    ).filterNot { color -> (tombstoneByMainColorId[color.id]?.revision ?: Long.MIN_VALUE) > color.revision }
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

private val garmentEntityTypes = setOf("garment", "clothing_item", "item")
private val categoryEntityTypes = setOf("tag_category", "category")
private val tagEntityTypes = setOf("garment_tag", "tag")
private val mainColorEntityTypes = setOf("main_color", "palette_color", "color")

private const val RESTORE_STEP_PREPARING = 0
private const val RESTORE_STEP_DOWNLOADED = 1
private const val RESTORE_STEP_VALIDATED = 2
private const val RESTORE_STEP_APPLIED = 3
private const val RESTORE_STEP_UPLOADED = 4
private const val RESTORE_TOTAL_STEPS = 5
