package com.gusanitolabs.robia.media

import android.net.Uri
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** The user-visible reason an image request is made. It is part of cache identity. */
enum class ImagePurpose {
    Browse,
    BatchPreview,
    ColorReview,
    Editor,
    Detail,
    Original,
}

enum class ImagePriority {
    Visible,
    Prefetch,
}

/** Domain-owned request data passed to the Coil adapter. */
data class ImageRequest(
    val sourceUri: Uri,
    val purpose: ImagePurpose,
    val targetBounds: ImageTargetBounds,
    val sourceRevision: String? = null,
    val priority: ImagePriority = ImagePriority.Visible,
    val maxEdgePx: Int? = null,
    val allowOriginal: Boolean = false,
) {
    init {
        require(maxEdgePx == null || maxEdgePx > 0) { "Image max edge must be positive when set." }
    }

    fun effectiveTargetBounds(): ImageTargetBounds = if (allowOriginal) {
        ImageTargetBounds(0, 0)
    } else {
        targetBounds.boundedBy(maxEdgePx ?: Int.MAX_VALUE)
    }

    fun cacheKey(): ImageCacheKey = ImageCacheKey(
        sourceRevision = buildString {
            append(sourceUri)
            sourceRevision?.takeIf(String::isNotBlank)?.let { revision ->
                append('|').append(revision)
            }
        },
        purpose = purpose,
        target = effectiveTargetBounds(),
    )
}

data class ImageTargetBounds(
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(widthPx >= 0 && heightPx >= 0) { "Image bounds must not be negative." }
    }

    val maxEdgePx: Int
        get() = maxOf(widthPx, heightPx)

    fun boundedBy(maxEdgePx: Int): ImageTargetBounds {
        if (maxEdgePx <= 0 || this.maxEdgePx <= maxEdgePx) return this
        val scale = maxEdgePx.toFloat() / this.maxEdgePx.toFloat()
        return ImageTargetBounds(
            widthPx = (widthPx * scale).toInt().coerceAtLeast(1),
            heightPx = (heightPx * scale).toInt().coerceAtLeast(1),
        )
    }
}

/**
 * A stable, sanitized display identity. The raw source revision never appears in logs or file names.
 */
data class ImageCacheKey(
    val sourceRevision: String,
    val purpose: ImagePurpose,
    val target: ImageTargetBounds,
    val transform: String = "fit-center",
    val pipelineVersion: Int = IMAGE_PIPELINE_VERSION,
) {
    val stableId: String = sha256(
        buildString {
            append(sourceRevision)
            append('|').append(purpose.name)
            append('|').append(target.widthPx).append('x').append(target.heightPx)
            append('|').append(transform)
            append('|').append(pipelineVersion)
        },
    ).take(STABLE_ID_LENGTH)

    companion object {
        private const val STABLE_ID_LENGTH = 32
    }
}

internal const val IMAGE_PIPELINE_VERSION = 3

/** Snapshot of Coil's byte-aware decoded-memory cache for diagnostics and safe tests. */
data class ImageMemorySnapshot(
    val bytes: Long,
    val entries: Int,
    val evictions: Long,
)

data class ImageTelemetryEvent(
    val stage: String,
    val requestId: String,
    val purpose: ImagePurpose,
    val priority: ImagePriority,
    val target: ImageTargetBounds,
    val cache: String? = null,
    val elapsedMs: Long? = null,
    val blankDurationMs: Long? = null,
    val activeDecodeCount: Int? = null,
    val memoryCacheBytes: Long? = null,
    val memoryCacheEntries: Int? = null,
    val diskCacheBytes: Long? = null,
    val diskCacheEntries: Int? = null,
    val evictionReason: String? = null,
    val error: String? = null,
)

fun interface ImageTelemetrySink {
    fun record(event: ImageTelemetryEvent)
}

/** Debuggable in-app evidence sink; the source identity is always the hashed request id. */
class SampledImageTelemetrySink(
    private val sampleEvery: Int = 8,
    private val logger: (String, String) -> Unit = { tag, message -> android.util.Log.i(tag, message) },
) : ImageTelemetrySink {
    private val sequence = AtomicLong(0L)

    override fun record(event: ImageTelemetryEvent) {
        val count = sequence.incrementAndGet()
        if (sampleEvery > 1 && count % sampleEvery != 0L && event.stage !in ALWAYS_LOGGED_STAGES) return
        val fields = buildList {
            add("stage=${sanitizeStage(event.stage)}")
            add("requestId=${sanitizeToken(event.requestId)}")
            add("purpose=${event.purpose.name}")
            add("priority=${event.priority.name}")
            add("target=${event.target.widthPx}x${event.target.heightPx}")
            event.cache?.let { add("cache=$it") }
            event.elapsedMs?.let { add("elapsedMs=${it.coerceAtLeast(0L)}") }
            event.blankDurationMs?.let { add("blankDurationMs=${it.coerceAtLeast(0L)}") }
            event.activeDecodeCount?.let { add("activeDecodeCount=${it.coerceAtLeast(0)}") }
            event.memoryCacheBytes?.let { add("memoryCacheBytes=${it.coerceAtLeast(0L)}") }
            event.memoryCacheEntries?.let { add("memoryCacheEntries=${it.coerceAtLeast(0)}") }
            event.diskCacheBytes?.let { add("diskCacheBytes=${it.coerceAtLeast(0L)}") }
            event.diskCacheEntries?.let { add("diskCacheEntries=${it.coerceAtLeast(0)}") }
            event.evictionReason?.let { add("evictionReason=${sanitizeToken(it)}") }
            event.error?.let { add("error=${sanitizeError(it)}") }
        }
        logger("RobiaPerformance", fields.joinToString(separator = " "))
    }

    private fun sanitizeStage(stage: String): String = stage
        .takeIf { it in KNOWN_STAGES }
        ?: "unknown"

    private fun sanitizeToken(value: String): String = value
        .replace(Regex("[^A-Za-z0-9_.:-]"), "_")
        .take(48)
        .ifBlank { "unknown" }

    private fun sanitizeError(error: String): String = error
        .replace(Regex("(?:content|file|android\\.resource)://\\S+"), "<uri>")
        .replace(Regex("/[^ ]+"), "<path>")
        .replace(Regex("[^A-Za-z0-9_.<>:-]"), "_")
        .take(96)

    private companion object {
        val ALWAYS_LOGGED_STAGES = REQUIRED_IMAGE_STAGES + setOf("cancel", "error")
        val KNOWN_STAGES = setOf(
            "memory_hit",
            "memory_miss",
            "disk_hit",
            "cancel",
            "error",
        ) + REQUIRED_IMAGE_STAGES
    }
}

internal object NoOpImageTelemetrySink : ImageTelemetrySink {
    override fun record(event: ImageTelemetryEvent) = Unit
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

internal val REQUIRED_IMAGE_STAGES = setOf(
    "resolve",
    "decode",
    "bind",
    "first_draw",
    "in_flight_wait",
    "placeholder_visible",
    "eviction",
)
