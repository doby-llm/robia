package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.MainColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Future seam for Drive or another backend; MVP deliberately stays credential-gated. */
interface WardrobeSyncGateway {
    val state: Flow<WardrobeSyncState>

    suspend fun enqueue(operation: WardrobeSyncOperation)
}

object NoOpWardrobeSyncGateway : WardrobeSyncGateway {
    override val state: Flow<WardrobeSyncState> = flowOf(WardrobeSyncState.notConfigured())
    override suspend fun enqueue(operation: WardrobeSyncOperation) = Unit
}

/** Testable queue-only gateway that never talks to Google services. */
class RecordingWardrobeSyncGateway(
    initialState: WardrobeSyncState = WardrobeSyncState.notConfigured(),
) : WardrobeSyncGateway {
    private val mutableState = MutableStateFlow(initialState)
    private val operations = mutableListOf<WardrobeSyncOperation>()

    override val state: Flow<WardrobeSyncState> = mutableState

    val pendingOperations: List<WardrobeSyncOperation>
        get() = operations.toList()

    override suspend fun enqueue(operation: WardrobeSyncOperation) {
        operations += operation
        mutableState.value = mutableState.value.copy(pendingOperationCount = operations.size)
    }
}

data class WardrobeSyncState(
    val connectionStatus: DriveSyncConnectionStatus,
    val disabledReason: DriveSyncDisabledReason? = null,
    val pendingOperationCount: Int = 0,
    val lastSyncedAtEpochMillis: Long? = null,
    val authorizedAccountEmail: String? = null,
    val expectedAccountEmail: String? = null,
    val restoreProgress: CloudRestoreProgress? = null,
) {
    val canAttemptGoogleDriveSync: Boolean
        get() = connectionStatus == DriveSyncConnectionStatus.Connected

    val hasConflictingAccountBinding: Boolean
        get() = authorizedAccountEmail != null &&
            expectedAccountEmail != null &&
            !authorizedAccountEmail.equals(expectedAccountEmail, ignoreCase = true)

    companion object {
        fun notConfigured(): WardrobeSyncState = WardrobeSyncState(
            connectionStatus = DriveSyncConnectionStatus.NotConfigured,
            disabledReason = DriveSyncDisabledReason.GoogleCloudSetupRequired,
        )
    }
}

data class CloudRestoreProgress(
    val phase: CloudRestorePhase,
    val completedWork: Int,
    val totalWork: Int,
    val status: CloudRestoreStatus = CloudRestoreStatus.Running,
    val message: String? = null,
) {
    val remainingWork: Int
        get() = (totalWork - completedWork).coerceAtLeast(0)

    val progressFraction: Float?
        get() = totalWork.takeIf { it > 0 }?.let { completedWork.coerceIn(0, it).toFloat() / it }
}

enum class CloudRestorePhase {
    Preparing,
    Downloading,
    Validating,
    Applying,
    RollingBack,
    Complete,
}

enum class CloudRestoreStatus {
    Running,
    Offline,
    Failed,
    RolledBack,
}

sealed interface WardrobeSyncOperation {
    val localOperationId: String
    val createdAtEpochMillis: Long

    data class UpsertItem(
        val itemId: String,
        override val localOperationId: String = operationId("item_upsert", itemId),
        override val createdAtEpochMillis: Long = System.currentTimeMillis(),
    ) : WardrobeSyncOperation

    data class DeleteItemFolder(
        val itemId: String,
        override val localOperationId: String = operationId("item_delete", itemId),
        override val createdAtEpochMillis: Long = System.currentTimeMillis(),
    ) : WardrobeSyncOperation

    data class UpsertTags(
        val touchedTagIds: Set<String>,
        override val localOperationId: String = operationId("tags_upsert", touchedTagIds.sorted().joinToString("_")),
        override val createdAtEpochMillis: Long = System.currentTimeMillis(),
    ) : WardrobeSyncOperation

    data class UpsertPalette(
        val colors: List<MainColor>,
        override val localOperationId: String = operationId("palette_upsert", colors.map(MainColor::id).sorted().joinToString("_")),
        override val createdAtEpochMillis: Long = System.currentTimeMillis(),
    ) : WardrobeSyncOperation

    data class ExportFullSnapshot(
        override val localOperationId: String = operationId("snapshot_export", "full"),
        override val createdAtEpochMillis: Long = System.currentTimeMillis(),
    ) : WardrobeSyncOperation

    data class ImportFullSnapshot(
        val sourceRevision: Long,
        override val localOperationId: String = operationId("snapshot_import", sourceRevision.toString()),
        override val createdAtEpochMillis: Long = System.currentTimeMillis(),
    ) : WardrobeSyncOperation

    data class UpsertGarments(
        val touchedGarmentIds: Set<String>,
        override val localOperationId: String = operationId("garments_upsert", touchedGarmentIds.sorted().joinToString("_")),
        override val createdAtEpochMillis: Long = System.currentTimeMillis(),
    ) : WardrobeSyncOperation

    data class UpsertTaxonomy(
        val touchedEntityIds: Set<String>,
        override val localOperationId: String = operationId("taxonomy_upsert", touchedEntityIds.sorted().joinToString("_")),
        override val createdAtEpochMillis: Long = System.currentTimeMillis(),
    ) : WardrobeSyncOperation

    data class RecordTombstones(
        val tombstoneIds: Set<String>,
        override val localOperationId: String = operationId("tombstone_record", tombstoneIds.sorted().joinToString("_")),
        override val createdAtEpochMillis: Long = System.currentTimeMillis(),
    ) : WardrobeSyncOperation
}

private fun operationId(prefix: String, key: String): String =
    "$prefix:${key.ifBlank { "all" }}:${System.currentTimeMillis()}"
