package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.GarmentTagMappingRecord
import com.gusanitolabs.robia.core.model.MainColorSyncRecord
import com.gusanitolabs.robia.core.model.SyncTombstoneRecord
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
 * Every sync cycle downloads Drive first, merges by stable ids/revisions, imports the merged snapshot
 * locally, then uploads the merged local snapshot. This prevents a clean reinstall from uploading an
 * empty local database over an existing Drive backup.
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
                settings.driveSyncConnectionStatus.toWardrobeSyncState(
                    pendingOperationCount = pendingCount,
                    attentionOperationCount = attentionCount,
                    isProcessing = processing,
                    needsPhotoRestoreAttention = photoAttention,
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

            try {
                when (val result = syncFetchMergeThenUpload()) {
                    is SyncCycleResult.Success -> {
                        needsPhotoRestoreAttention.value = result.guardedPhotoCount > 0
                        markSynced(lockedWork)
                    }
                    SyncCycleResult.NoBackup -> {
                        needsPhotoRestoreAttention.value = false
                        markSynced(lockedWork)
                    }
                    is SyncCycleResult.Blocked -> markBlocked(lockedWork, result.reason)
                    is SyncCycleResult.Failure -> markFailedRetryable(lockedWork)
                }
            } finally {
                isProcessing.value = false
            }
        }
    }

    private suspend fun syncFetchMergeThenUpload(): SyncCycleResult {
        val localSnapshot = snapshotRepository.exportSnapshot()
        val remoteSnapshot = when (val result = driveRepository.fetchSnapshot()) {
            is DriveSyncResult.Success -> result.value.sortedDeterministically()
            is DriveSyncResult.Blocked -> return SyncCycleResult.Blocked(result.reason)
            is DriveSyncResult.Failure -> return SyncCycleResult.Failure
        }
        if (remoteSnapshot.metadata.schemaVersion > WARDROBE_SYNC_SCHEMA_VERSION) {
            return SyncCycleResult.Failure
        }

        val mergedSnapshot = mergeSnapshots(localSnapshot, remoteSnapshot)
        val importResult = if (mergedSnapshot.hasUserData()) {
            snapshotRepository.importSnapshot(mergedSnapshot)
        } else {
            ImportSnapshotResult(restoredGarmentCount = 0, guardedPhotoCount = 0)
        }

        return when (driveRepository.upsertSnapshot(snapshotRepository.exportSnapshot())) {
            is DriveSyncResult.Success -> if (localSnapshot.hasUserData() || remoteSnapshot.hasUserData()) {
                SyncCycleResult.Success(importResult.guardedPhotoCount)
            } else {
                SyncCycleResult.NoBackup
            }
            is DriveSyncResult.Blocked -> SyncCycleResult.Blocked(DriveSyncDisabledReason.UserNotConnected)
            is DriveSyncResult.Failure -> SyncCycleResult.Failure
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

private fun WardrobeSyncSnapshot.hasUserData(): Boolean =
    garments.isNotEmpty() || photos.isNotEmpty() || tombstones.isNotEmpty()

private fun mergeSnapshots(local: WardrobeSyncSnapshot, remote: WardrobeSyncSnapshot): WardrobeSyncSnapshot {
    val tombstones = mergeByKey(local.tombstones + remote.tombstones, { "${it.entityType}:${it.entityId}" }, SyncTombstoneRecord::revision)
    val tombstoneByGarmentId = tombstones
        .filter { tombstone -> tombstone.entityType in garmentEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)

    val mergedGarments = mergeByKey(local.garments + remote.garments, GarmentSyncRecord::id, GarmentSyncRecord::revision)
        .filterNot { garment -> (tombstoneByGarmentId[garment.id]?.revision ?: Long.MIN_VALUE) > garment.revision }
    val activeGarmentIds = mergedGarments.map(GarmentSyncRecord::id).toSet()
    val mergedMetadata = WardrobeSnapshotMetadata(
        generatedAtEpochMillis = System.currentTimeMillis(),
        revision = maxOf(local.metadata.revision, remote.metadata.revision, System.currentTimeMillis()),
        wardrobeId = local.metadata.wardrobeId ?: remote.metadata.wardrobeId,
    )

    return WardrobeSyncSnapshot(
        metadata = mergedMetadata,
        taxonomies = WardrobeTaxonomySnapshot(
            categories = mergeByKey(
                local.taxonomies.categories + remote.taxonomies.categories,
                TagCategorySyncRecord::id,
                TagCategorySyncRecord::revision,
            ),
            tags = mergeByKey(local.taxonomies.tags + remote.taxonomies.tags, TagSyncRecord::id, TagSyncRecord::revision),
            mainColors = mergeByKey(
                local.taxonomies.mainColors + remote.taxonomies.mainColors,
                MainColorSyncRecord::id,
                MainColorSyncRecord::revision,
            ),
        ),
        garments = mergedGarments,
        garmentTags = mergeByKey(
            local.garmentTags + remote.garmentTags,
            { "${it.garmentId}:${it.tagId}" },
            GarmentTagMappingRecord::revision,
        ).filter { record -> record.garmentId in activeGarmentIds },
        garmentColors = mergeByKey(
            local.garmentColors + remote.garmentColors,
            { "${it.garmentId}:${it.role}" },
            GarmentColorMappingRecord::revision,
        ).filter { record -> record.garmentId in activeGarmentIds },
        photos = mergeByKey(local.photos + remote.photos, GarmentPhotoRecord::garmentId, GarmentPhotoRecord::revision)
            .filter { record -> record.garmentId in activeGarmentIds },
        tombstones = tombstones,
    ).sortedDeterministically()
}

private fun <T, K> mergeByKey(records: List<T>, key: (T) -> K, revision: (T) -> Long): List<T> =
    records
        .groupBy(key)
        .values
        .map { group -> group.maxWith(compareBy<T> { revision(it) }.thenBy { records.indexOf(it) }) }

private val garmentEntityTypes = setOf("garment", "clothing_item", "item")
