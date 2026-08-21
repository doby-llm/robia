package com.gusanitolabs.robia.sync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Developer-mode-only, bounded diagnostic event log stored in app-private files. */
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
    val category: String = "restore",
    val level: String = "info",
    val phase: CloudRestorePhase?,
    val status: CloudRestoreStatus?,
    val message: String,
    val garmentId: String? = null,
    val description: String? = null,
    val blobPath: String? = null,
    val byteSize: Long? = null,
    val mimeType: String? = null,
    val byteMagic: String? = null,
    val contentHash: String? = null,
    val restoredUriStatus: String? = null,
    val placeholderReason: String? = null,
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
    val completedWork: Int? = null,
    val totalWork: Int? = null,
    val itemIndex: Int? = null,
    val itemTotal: Int? = null,
    val bytesCompleted: Long? = null,
    val bytesTotal: Long? = null,
    val durationMillis: Long? = null,
    val occurredAtEpochMillis: Long = System.currentTimeMillis(),
) {
    fun toLogLine(): String = buildString {
        append("ts=").append(occurredAtEpochMillis)
        append(" correlation_id=").append(sanitizeLogField(correlationId))
        append(" category=").append(sanitizeLogField(category))
        append(" level=").append(sanitizeLogField(level))
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
        itemIndex?.let { append(" item_index=").append(it) }
        itemTotal?.let { append(" item_total=").append(it) }
        bytesCompleted?.let { append(" bytes_completed=").append(it) }
        bytesTotal?.let { append(" bytes_total=").append(it) }
        durationMillis?.let { append(" duration_ms=").append(it) }
        mimeType?.let { append(" mime_type=").append(sanitizeLogField(it)) }
        byteMagic?.let { append(" byte_magic=").append(sanitizeMagicClassification(it)) }
        contentHash?.let { append(" content_hash_prefix=").append(sanitizeHash(it).take(12)) }
        restoredUriStatus?.let { append(" restored_uri_status=").append(sanitizeLogField(it)) }
        placeholderReason?.let { append(" placeholder_reason=").append(sanitizeLogField(it)) }
        exceptionClass?.let { append(" exception_class=").append(sanitizeLogField(it)) }
        exceptionMessage?.let { append(" exception_message=\"").append(sanitizeLogField(it)).append('"') }
    }
}

class FileRestoreSyncLogRepository(
    context: Context,
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : RestoreSyncLogRepository {
    // Keep the established file name so enabling the generalized event log does not orphan the
    // existing bounded restore/sync history or create a second diagnostics file.
    private val logFile = File(context.filesDir, "developer_restore_sync.log")
    private val enabledLock = Any()
    private val mutableText = MutableStateFlow("")
    @Volatile private var enabled = false

    override val text: Flow<String> = mutableText

    override fun setEnabled(enabled: Boolean) {
        synchronized(enabledLock) {
            this.enabled = enabled
            if (enabled) {
                scope.launch {
                    runCatching {
                        val lines = readBoundedLines()
                        synchronized(enabledLock) {
                            if (this@FileRestoreSyncLogRepository.enabled) {
                                mutableText.value = readLogText(lines)
                            }
                        }
                    }
                }
            } else {
                mutableText.value = ""
            }
        }
    }

    override fun append(event: RestoreSyncLogEvent) {
        if (!enabled) return
        val line = event.toLogLine()
        scope.launch {
            runCatching {
                synchronized(enabledLock) {
                    if (!enabled) return@synchronized
                    val lines = boundedDiagnosticLogLines(readBoundedLines() + line, maxEvents, maxBytes)
                    logFile.parentFile?.mkdirs()
                    logFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
                    mutableText.value = readLogText(lines)
                }
            }
        }
    }

    override fun clear() {
        synchronized(enabledLock) {
            runCatching {
                if (logFile.exists()) logFile.delete()
            }
            mutableText.value = ""
        }
    }

    private fun readLogText(lines: List<String> = readLines()): String = lines.joinToString("\n")

    private fun readBoundedLines(): List<String> = boundedDiagnosticLogLines(readLines(), maxEvents, maxBytes)

    private fun readLines(): List<String> = if (logFile.exists()) {
        logFile.readLines(Charsets.UTF_8).filter(String::isNotBlank)
    } else {
        emptyList()
    }

    internal companion object {
        const val DEFAULT_MAX_EVENTS = 500
        const val DEFAULT_MAX_BYTES = 256 * 1024
    }
}

internal fun boundedDiagnosticLogLines(
    lines: List<String>,
    maxEvents: Int = FileRestoreSyncLogRepository.DEFAULT_MAX_EVENTS,
    maxBytes: Int = FileRestoreSyncLogRepository.DEFAULT_MAX_BYTES,
): List<String> {
    val boundedByEvents = lines.takeLast(maxEvents.coerceAtLeast(1))
    val selected = ArrayDeque<String>()
    var byteCount = 0
    val byteLimit = maxBytes.coerceAtLeast(1)
    boundedByEvents.asReversed().forEach { line ->
        val lineBytes = line.toByteArray(Charsets.UTF_8).size + 1
        if (lineBytes > byteLimit || byteCount + lineBytes > byteLimit) return@forEach
        selected.addFirst(line)
        byteCount += lineBytes
    }
    return selected.toList()
}

internal fun sanitizeLogField(value: String): String = value
    .replace(Regex("Bearer\\s+[A-Za-z0-9._~+/=-]+", RegexOption.IGNORE_CASE), "Bearer <redacted>")
    .replace(Regex("(?i)(access[_-]?token|refresh[_-]?token|id[_-]?token|authorization)=\\S+"), "\$1=<redacted>")
    .replace(Regex("(?i)\\b(first32|last32|readbackFirst32|readbackLast32)=[0-9a-f]+"), "\$1=<redacted>")
    .replace(Regex("(?i)\\b(sourceMagic|magic)=[0-9a-f]+"), "\$1=<redacted>")
    .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email-redacted>")
    .replace(Regex("content://[^\\s]+"), "content://<redacted>")
    .replace(Regex("file://[^\\s]+"), "file://<redacted>")
    .replace(Regex("(^|\\s)(/[^\\s:]+(?:/[^\\s:]+)+)")) { match -> "${match.groupValues[1]}<path-redacted>" }
    .replace('\n', ' ')
    .replace('\r', ' ')
    .take(MAX_LOG_FIELD_CHARS)

private fun sanitizeHash(value: String): String = value
    .filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    .take(64)

private fun sanitizeMagicClassification(value: String): String {
    val hex = value.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.lowercase()
    return when {
        hex.startsWith("ffd8ff") -> "jpeg_signature"
        hex.startsWith("89504e470d0a1a0a") -> "png_signature"
        hex.length >= 24 && hex.startsWith("52494646") && hex.substring(16, 24) == "57454250" -> "webp_signature"
        hex.startsWith("474946383761") || hex.startsWith("474946383961") -> "gif_signature"
        else -> "unknown_signature"
    }
}

private const val MAX_LOG_FIELD_CHARS = 240
