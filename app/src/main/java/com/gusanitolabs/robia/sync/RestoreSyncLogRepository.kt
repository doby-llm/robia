package com.gusanitolabs.robia.sync

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Developer-mode-only, bounded restore/sync log stored in app-private files. */
interface RestoreSyncLogRepository {
    val text: Flow<String>
    fun setEnabled(enabled: Boolean)
    fun append(event: RestoreSyncLogEvent)
    fun clear()
}

object NoOpRestoreSyncLogRepository : RestoreSyncLogRepository {
    override val text: Flow<String> = MutableStateFlow("")
    override fun setEnabled(enabled: Boolean) = Unit
    override fun append(event: RestoreSyncLogEvent) = Unit
    override fun clear() = Unit
}

data class RestoreSyncLogEvent(
    val correlationId: String,
    val phase: CloudRestorePhase?,
    val status: CloudRestoreStatus?,
    val message: String,
    val garmentId: String? = null,
    val description: String? = null,
    val blobPath: String? = null,
    val byteSize: Long? = null,
    val contentHash: String? = null,
    val restoredUriStatus: String? = null,
    val placeholderReason: String? = null,
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
    val completedWork: Int? = null,
    val totalWork: Int? = null,
    val occurredAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun toLogLine(): String = buildString {
        append("ts=").append(occurredAtEpochMillis)
        append(" correlation_id=").append(sanitizeLogField(correlationId))
        phase?.let { append(" phase=").append(it.name) }
        status?.let { append(" status=").append(it.name) }
        completedWork?.let { completed ->
            val total = totalWork ?: 0
            append(" progress=").append(completed).append('/').append(total)
        }
        append(" message=\"").append(sanitizeLogField(message)).append('"')
        garmentId?.let { append(" garment_id=").append(sanitizeLogField(it)) }
        description?.let { append(" description=\"").append(sanitizeLogField(it)).append('"') }
        blobPath?.let { append(" blob_path=").append(sanitizeLogField(it)) }
        byteSize?.let { append(" byte_size=").append(it) }
        contentHash?.let { append(" content_hash=").append(sanitizeHash(it)) }
        restoredUriStatus?.let { append(" restored_uri_status=").append(sanitizeLogField(it)) }
        placeholderReason?.let { append(" placeholder_reason=").append(sanitizeLogField(it)) }
        exceptionClass?.let { append(" exception_class=").append(sanitizeLogField(it)) }
        exceptionMessage?.let { append(" exception_message=\"").append(sanitizeLogField(it)).append('"') }
    }
}

class FileRestoreSyncLogRepository(
    context: Context,
    private val maxLines: Int = DEFAULT_MAX_LINES,
) : RestoreSyncLogRepository {
    private val logFile = File(context.filesDir, "developer_restore_sync.log")
    private val enabledLock = Any()
    private val mutableText = MutableStateFlow(readLogText())
    @Volatile private var enabled = false

    override val text: Flow<String> = mutableText

    override fun setEnabled(enabled: Boolean) {
        synchronized(enabledLock) {
            this.enabled = enabled
            if (!enabled) mutableText.value = readLogText()
        }
    }

    override fun append(event: RestoreSyncLogEvent) {
        synchronized(enabledLock) {
            if (!enabled) return
            val lines = buildList {
                addAll(readLines())
                add(event.toLogLine())
            }.takeLast(maxLines.coerceAtLeast(1))
            logFile.parentFile?.mkdirs()
            logFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
            mutableText.value = readLogText(lines)
        }
    }

    override fun clear() {
        synchronized(enabledLock) {
            if (logFile.exists()) logFile.delete()
            mutableText.value = ""
        }
    }

    private fun readLogText(lines: List<String> = readLines()): String = lines.joinToString("\n")

    private fun readLines(): List<String> = if (logFile.exists()) {
        logFile.readLines(Charsets.UTF_8).filter(String::isNotBlank).takeLast(maxLines.coerceAtLeast(1))
    } else {
        emptyList()
    }

    private companion object {
        const val DEFAULT_MAX_LINES = 200
    }
}

internal fun sanitizeLogField(value: String): String = value
    .replace(Regex("Bearer\\s+[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE), "Bearer <redacted>")
    .replace(Regex("(?i)(access[_-]?token|refresh[_-]?token|id[_-]?token|authorization)=\\S+"), "\$1=<redacted>")
    .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email-redacted>")
    .replace(Regex("content://[^\\s]+"), "content://<redacted>")
    .replace(Regex("file://[^\\s]+"), "file://<redacted>")
    .replace(Regex("/[^\\s:]+(?:/[^\\s:]+)+"), "<path-redacted>")
    .replace('\n', ' ')
    .replace('\r', ' ')
    .take(MAX_LOG_FIELD_CHARS)

private fun sanitizeHash(value: String): String = value
    .filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    .take(64)

private const val MAX_LOG_FIELD_CHARS = 240
