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
    val restoreSyncLogText: Flow<String>

    suspend fun enqueue(operation: WardrobeSyncOperation)
    suspend fun clearRestoreSyncLog()
}

object NoOpWardrobeSyncGateway : WardrobeSyncGateway {
    override val state: Flow<WardrobeSyncState> = flowOf(WardrobeSyncState.notConfigured())
    override val restoreSyncLogText: Flow<String> = flowOf("")
    override suspend fun enqueue(operation: WardrobeSyncOperation) = Unit
    override suspend fun clearRestoreSyncLog() = Unit
}

/** Testable queue-only gateway that never talks to Google services. */
class RecordingWardrobeSyncGateway(
    initialState: WardrobeSyncState = WardrobeSyncState.notConfigured(),
) : WardrobeSyncGateway {
    private val mutableState = MutableStateFlow(initialState)
    private val operations = mutableListOf<WardrobeSyncOperation>()

    override val state: Flow<WardrobeSyncState> = mutableState
    override val restoreSyncLogText: Flow<String> = flowOf("")

    val pendingOperations: List<WardrobeSyncOperation>
        get() = operations.toList()

    override suspend fun enqueue(operation: WardrobeSyncOperation) {
        operations += operation
        mutableState.value = mutableState.value.copy(pendingOperationCount = operations.size)
    }

    override suspend fun clearRestoreSyncLog() = Unit
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
    val diagnostics: CloudRestoreDiagnostics? = null,
) {
    val remainingWork: Int
        get() = (totalWork - completedWork).coerceAtLeast(0)

    val progressFraction: Float?
        get() = totalWork.takeIf { it > 0 }?.let { completedWork.coerceIn(0, it).toFloat() / it }
}

/**
 * Sanitized, support-copyable telemetry for opaque Drive restore failures.
 *
 * Keep this model free of tokens, email addresses, file paths, garment names/notes, raw JSON, and
 * photo bytes. Values are aggregate counts, schema/revision metadata, and bounded error summaries.
 */
data class CloudRestoreDiagnostics(
    val correlationId: String,
    val attempt: Int,
    val startedAtEpochMillis: Long,
    val elapsedMillis: Long,
    val phase: CloudRestorePhase,
    val status: CloudRestoreStatus,
    val localWasEmpty: Boolean? = null,
    val localGarmentCount: Int? = null,
    val localPhotoCount: Int? = null,
    val remoteSchemaVersion: Int? = null,
    val remoteRevision: Long? = null,
    val remoteGarmentCount: Int? = null,
    val remotePhotoCount: Int? = null,
    val remoteFavoriteFieldPresent: Boolean? = null,
    val remoteFavoriteMarkedCount: Int? = null,
    val restoredGarmentCount: Int? = null,
    val guardedPhotoCount: Int? = null,
    val localSaveCompleted: Boolean? = null,
    val finalUploadAttempted: Boolean? = null,
    val finalUploadSucceeded: Boolean? = null,
    val lastExceptionClass: String? = null,
    val lastExceptionMessage: String? = null,
    val failureCategory: String? = null,
    val events: List<String> = emptyList(),
) {
    fun toCopyText(): String = buildString {
        appendLine("robia_restore_diagnostics")
        appendLine("correlation_id: $correlationId")
        appendLine("attempt: $attempt")
        appendLine("started_at_epoch_ms: $startedAtEpochMillis")
        appendLine("elapsed_ms: $elapsedMillis")
        appendLine("phase: ${phase.name}")
        appendLine("status: ${status.name}")
        appendNullable("local_was_empty", localWasEmpty)
        appendNullable("local_garment_count", localGarmentCount)
        appendNullable("local_photo_count", localPhotoCount)
        appendNullable("remote_schema_version", remoteSchemaVersion)
        appendNullable("remote_revision", remoteRevision)
        appendNullable("remote_garment_count", remoteGarmentCount)
        appendNullable("remote_photo_count", remotePhotoCount)
        appendNullable("remote_favorite_field_present", remoteFavoriteFieldPresent)
        appendNullable("remote_favorite_marked_count", remoteFavoriteMarkedCount)
        appendNullable("restored_garment_count", restoredGarmentCount)
        appendNullable("guarded_photo_count", guardedPhotoCount)
        appendNullable("local_save_completed", localSaveCompleted)
        appendNullable("final_upload_attempted", finalUploadAttempted)
        appendNullable("final_upload_succeeded", finalUploadSucceeded)
        appendNullable("last_exception_class", lastExceptionClass)
        appendNullable("last_exception_message", lastExceptionMessage)
        appendNullable("failure_category", failureCategory)
        if (events.isNotEmpty()) {
            appendLine("events:")
            events.takeLast(MAX_DIAGNOSTIC_EVENTS).forEach { event -> appendLine("- $event") }
        }
    }

    private fun StringBuilder.appendNullable(name: String, value: Any?) {
        if (value != null) appendLine("$name: $value")
    }

    private companion object {
        const val MAX_DIAGNOSTIC_EVENTS = 32
    }
}

enum class CloudRestorePhase {
    Preparing,
    Downloading,
    Validating,
    Applying,
    Uploading,
    RollingBack,
    Complete,
}

enum class CloudRestoreStatus {
    Running,
    Offline,
    Failed,
    RolledBack,
    CompletedWithAttention,
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

    /** Manual, bounded recovery of one guarded Drive photo; it must not restart full restore. */
    data class RetryRestoredPhoto(
        val garmentId: String,
        override val localOperationId: String = operationId("photo_restore_retry", garmentId),
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
