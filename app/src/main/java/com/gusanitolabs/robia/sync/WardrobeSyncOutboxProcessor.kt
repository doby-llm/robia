package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
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
 * The settings repository remains the source of truth for setup/account state; the processor only
 * derives transient activity (pending/syncing/needs attention) and advances garment revisions.
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
    private val mutableState = MutableStateFlow(WardrobeSyncState.notConfigured())

    override val state: Flow<WardrobeSyncState> = mutableState

    init {
        scope.launch(dispatcher) {
            combine(
                settingsRepository.settings,
                wardrobeRepository.observePendingGarmentSyncCount(),
                wardrobeRepository.observeGarmentSyncAttentionCount(),
                isProcessing,
            ) { settings, pendingCount, attentionCount, processing ->
                settings.driveSyncConnectionStatus.toWardrobeSyncState(
                    pendingOperationCount = pendingCount,
                    attentionOperationCount = attentionCount,
                    isProcessing = processing,
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
                )
                DriveSyncConnectionStatus.Disconnected -> markOperationAuthBlocked(operation)
                DriveSyncConnectionStatus.Disabled,
                DriveSyncConnectionStatus.NotConfigured -> markOperationSetupRequired(operation)
            }
        }
    }

    private suspend fun processPendingGarments(forceSnapshot: Boolean = false) {
        mutex.withLock {
            if (settingsRepository.settings.first().driveSyncConnectionStatus != DriveSyncConnectionStatus.Connected) {
                return@withLock
            }

            val pendingWork = wardrobeRepository.pendingGarmentSyncWork()
            if (pendingWork.isEmpty() && !forceSnapshot) return@withLock

            isProcessing.value = true
            val lockedWork = pendingWork.filter { work ->
                wardrobeRepository.markGarmentSyncing(work.id, work.revision)
            }
            if (lockedWork.isEmpty() && !forceSnapshot) {
                isProcessing.value = false
                return@withLock
            }

            try {
                when (val result = driveRepository.upsertSnapshot(snapshotRepository.exportSnapshot())) {
                    is DriveSyncResult.Success -> markSynced(lockedWork)
                    is DriveSyncResult.Blocked -> markBlocked(lockedWork, result.reason)
                    is DriveSyncResult.Failure -> markFailedRetryable(lockedWork)
                }
            } finally {
                isProcessing.value = false
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
    ): WardrobeSyncState {
        val displayStatus = when {
            isProcessing && this == DriveSyncConnectionStatus.Connected -> DriveSyncConnectionStatus.Syncing
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
