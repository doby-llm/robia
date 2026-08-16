package com.gusanitolabs.robia.media

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.EventListener
import coil3.Extras
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.ImageRequest as CoilImageRequest
import coil3.request.SuccessResult
import coil3.getExtra
import coil3.size.Size
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withFrameNanos
import okio.Path.Companion.toPath
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Robia's adapter boundary around Coil. Coil types stay inside this file; UI call sites only
 * construct [ImageRequest] values and invoke [Display].
 *
 * Coil owns decoded-memory and disk caches. This adapter owns stable Robia keys, request purpose/priority,
 * composition-safe single-flight coordination, cache budgets, bounded prefetch, placeholder/error policy,
 * and sanitized telemetry.
 */
class ImagePipeline private constructor(
    private val appContext: Context,
    private val telemetry: ImageTelemetrySink,
) {
    private val prefetchScope = CoroutineScope(
        Dispatchers.IO + CoroutineName("robia-image-prefetch"),
    )
    private val prefetchPermit = Semaphore(MAX_PREFETCH_DECODE)
    private val prefetchJobs = ConcurrentHashMap<String, Job>()
    private val ownedInFlightRequests = ConcurrentHashMap<String, InFlightRequest>()
    private val inFlightLock = Any()
    private val activeDecodeCount = AtomicInteger(0)
    private val cacheIdentities = ConcurrentHashMap<String, CacheIdentity>()
    private val cacheIdentityOrder = ConcurrentLinkedQueue<String>()
    private val evictionCount = AtomicLong(0L)
    private val diskCacheDirectory = appContext.cacheDir.resolve(DISK_CACHE_DIRECTORY)

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .memoryCache {
            TrackingMemoryCache(
                delegate = MemoryCache.Builder()
                    .maxSizeBytes(MAX_MEMORY_CACHE_BYTES)
                    .weakReferencesEnabled(false)
                    .build(),
                onEvictions = ::recordEvictions,
            )
        }
        .diskCache {
            DiskCache.Builder()
                .directory(appContext.cacheDir.resolve(DISK_CACHE_DIRECTORY).toPath())
                .maxSizeBytes(MAX_DISK_CACHE_BYTES)
                .build()
        }
        // Coil's decoder context is shared by visible and prefetch work. The prefetch permit below
        // keeps warm-up work bounded while leaving decoder capacity for visible requests.
        .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(MAX_ACTIVE_DECODE))
        .eventListenerFactory { request ->
            RobiaCoilEventListener(
                request = request,
                telemetry = telemetry,
                cacheStats = ::cacheStats,
                activeDecodeCount = activeDecodeCount,
            )
        }
        .build()

    fun requestFor(request: ImageRequest): CoilImageRequest {
        val cacheKey = request.cacheKey()
        rememberCacheIdentity(request, cacheKey)
        return CoilImageRequest.Builder(appContext)
            .data(request.sourceUri)
            .size(
                if (request.allowOriginal) {
                    Size.ORIGINAL
                } else {
                    request.effectiveTargetBounds().let { bounds ->
                        Size(bounds.widthPx, bounds.heightPx)
                    }
                },
            )
            .memoryCacheKey(cacheKey.stableId)
            .diskCacheKey(cacheKey.stableId)
            .apply {
                extras[IMAGE_PRIORITY_EXTRA] = request.priority
                extras[IMAGE_PURPOSE_EXTRA] = request.purpose
                extras[IMAGE_TARGET_EXTRA] = request.effectiveTargetBounds()
            }
            .build()
    }

    /** Enqueue bounded warm-up work through the adapter-owned in-flight registry. */
    fun prefetch(request: ImageRequest): Job? {
        if (request.priority != ImagePriority.Prefetch || request.allowOriginal) return null
        val key = request.cacheKey().stableId
        if (imageLoader.memoryCache?.get(MemoryCache.Key(key)) != null) return null

        synchronized(inFlightLock) {
            ownedInFlightRequests[key]?.let {
                recordInFlightWait(request)
                return prefetchJobs[key]
            }
            if (prefetchJobs.size >= MAX_PREFETCH_JOBS) return null

            val ownedRequest = InFlightRequest(InFlightOwner.Prefetch)
            lateinit var job: Job
            job = prefetchScope.launch(start = CoroutineStart.LAZY) {
                var success = false
                try {
                    prefetchPermit.withPermit {
                        val result = imageLoader.execute(requestFor(request))
                        success = result is SuccessResult
                    }
                } finally {
                    completeInFlight(key, ownedRequest, success)
                    synchronized(inFlightLock) {
                        prefetchJobs.remove(key, job)
                    }
                }
            }
            ownedInFlightRequests[key] = ownedRequest
            prefetchJobs[key] = job
            job.start()
            return job
        }
    }

    /** Drop stale directional work when the grid changes direction or its window jumps. */
    fun cancelPrefetch() {
        val jobs = synchronized(inFlightLock) {
            val jobsToCancel = prefetchJobs.values.toList()
            prefetchJobs.clear()
            ownedInFlightRequests.entries
                .filter { (_, request) -> request.owner == InFlightOwner.Prefetch }
                .forEach { (key, request) ->
                    if (ownedInFlightRequests.remove(key, request)) {
                        request.completion.complete(false)
                    }
                }
            jobsToCancel
        }
        jobs.forEach(Job::cancel)
    }

    private fun acquireVisible(request: ImageRequest): RequestLease {
        val key = request.cacheKey().stableId
        synchronized(inFlightLock) {
            ownedInFlightRequests[key]?.let { existing ->
                recordInFlightWait(request)
                return RequestLease(key, existing, isOwner = false)
            }
            val ownedRequest = InFlightRequest(InFlightOwner.Visible)
            ownedInFlightRequests[key] = ownedRequest
            return RequestLease(key, ownedRequest, isOwner = true)
        }
    }

    private fun completeVisible(lease: RequestLease, success: Boolean) {
        if (lease.isOwner) completeInFlight(lease.key, lease.request, success)
    }

    private fun releaseVisible(lease: RequestLease) {
        completeVisible(lease, success = false)
    }

    private fun completeInFlight(key: String, request: InFlightRequest, success: Boolean) {
        synchronized(inFlightLock) {
            if (ownedInFlightRequests.remove(key, request)) {
                request.completion.complete(success)
            }
        }
    }

    private fun recordInFlightWait(request: ImageRequest) {
        val key = request.cacheKey()
        val stats = cacheStats()
        telemetry.record(
            ImageTelemetryEvent(
                stage = "in_flight_wait",
                requestId = key.stableId,
                purpose = key.purpose,
                priority = request.priority,
                target = key.target,
                cache = "in_flight",
                memoryCacheBytes = stats.memoryCacheBytes,
                memoryCacheEntries = stats.memoryCacheEntries,
                diskCacheBytes = stats.diskCacheBytes,
                diskCacheEntries = stats.diskCacheEntries,
            ),
        )
    }

    fun recordPlaceholderVisible(request: ImageRequest): Long {
        val now = android.os.SystemClock.elapsedRealtime()
        val key = request.cacheKey()
        telemetry.record(
            event = ImageTelemetryEvent(
                stage = "placeholder_visible",
                requestId = key.stableId,
                purpose = key.purpose,
                priority = request.priority,
                target = key.target,
                memoryCacheBytes = imageLoader.memoryCache?.size,
                memoryCacheEntries = imageLoader.memoryCache?.keys?.size,
                diskCacheBytes = imageLoader.diskCache?.size,
                diskCacheEntries = diskCacheEntryCount(),
            ),
        )
        return now
    }

    fun recordBind(request: ImageRequest, placeholderAt: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        recordTerminal(
            request = request,
            stage = "bind",
            placeholderAt = placeholderAt,
            now = now,
        )
    }

    fun recordFirstDraw(request: ImageRequest, placeholderAt: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        recordTerminal(
            request = request,
            stage = "first_draw",
            placeholderAt = placeholderAt,
            now = now,
        )
    }

    private fun recordTerminal(
        request: ImageRequest,
        stage: String,
        placeholderAt: Long,
        now: Long,
    ) {
        val key = request.cacheKey()
        telemetry.record(
            event = ImageTelemetryEvent(
                stage = stage,
                requestId = key.stableId,
                purpose = key.purpose,
                priority = request.priority,
                target = key.target,
                elapsedMs = (now - placeholderAt).coerceAtLeast(0L),
                blankDurationMs = (now - placeholderAt).coerceAtLeast(0L),
                memoryCacheBytes = imageLoader.memoryCache?.size,
                memoryCacheEntries = imageLoader.memoryCache?.keys?.size,
                diskCacheBytes = imageLoader.diskCache?.size,
                diskCacheEntries = diskCacheEntryCount(),
            ),
        )
    }

    private fun cacheStats(): ImageCacheStats = ImageCacheStats(
        memoryCacheBytes = imageLoader.memoryCache?.size,
        memoryCacheEntries = imageLoader.memoryCache?.keys?.size,
        memoryCacheKeys = imageLoader.memoryCache?.keys?.map { it.key }?.toSet().orEmpty(),
        diskCacheBytes = imageLoader.diskCache?.size,
        diskCacheEntries = diskCacheEntryCount(),
    )

    private fun diskCacheEntryCount(): Int? = diskCacheDirectory.listFiles()
        ?.count { file -> file.isFile && file.name.endsWith(".0") }

    private fun rememberCacheIdentity(request: ImageRequest, cacheKey: ImageCacheKey) {
        if (cacheIdentities.putIfAbsent(
                cacheKey.stableId,
                CacheIdentity(request.purpose, request.priority, cacheKey.target),
            ) == null
        ) {
            cacheIdentityOrder.add(cacheKey.stableId)
        }
        while (cacheIdentityOrder.size > MAX_CACHE_IDENTITIES) {
            cacheIdentityOrder.poll()?.let(cacheIdentities::remove)
        }
    }

    private fun recordEvictions(evictedKeys: Set<MemoryCache.Key>) {
        if (evictedKeys.isEmpty()) return
        evictionCount.addAndGet(evictedKeys.size.toLong())
        val stats = cacheStats()
        evictedKeys.forEach { key ->
            val identity = cacheIdentities[key.key]
                ?: CacheIdentity(ImagePurpose.Browse, ImagePriority.Visible, ImageTargetBounds(0, 0))
            telemetry.record(
                ImageTelemetryEvent(
                    stage = "eviction",
                    requestId = key.key,
                    purpose = identity.purpose,
                    priority = identity.priority,
                    target = identity.target,
                    cache = "memory",
                    memoryCacheBytes = stats.memoryCacheBytes,
                    memoryCacheEntries = stats.memoryCacheEntries,
                    diskCacheBytes = stats.diskCacheBytes,
                    diskCacheEntries = stats.diskCacheEntries,
                    evictionReason = "memory_capacity",
                ),
            )
        }
    }

    @Composable
    fun Display(
        request: ImageRequest,
        modifier: Modifier = Modifier,
        errorContentDescription: String = "Image unavailable; tap to retry",
    ) {
        val cacheKey = remember(request) { request.cacheKey() }
        val coilRequest = remember(request) { requestFor(request) }
        val lease = remember(cacheKey.stableId) { acquireVisible(request) }
        var followerReady by remember(lease) { mutableStateOf(lease.isOwner) }
        DisposableEffect(lease) {
            onDispose { releaseVisible(lease) }
        }
        LaunchedEffect(lease) {
            if (!lease.isOwner) {
                lease.await()
                followerReady = true
            }
        }
        val requestReady = lease.isOwner || followerReady
        val painter = rememberAsyncImagePainter(
            model = coilRequest.takeIf { requestReady },
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
        )
        val state by painter.state.collectAsState()
        var previousSuccessPainter by remember(cacheKey.stableId) {
            mutableStateOf<Painter?>(null)
        }
        var placeholderAt by remember(cacheKey.stableId) { mutableStateOf<Long?>(null) }
        var errorRecorded by remember(cacheKey.stableId) { mutableStateOf(false) }

        LaunchedEffect(request, state) {
            when (val current = state) {
                is AsyncImagePainter.State.Empty,
                is AsyncImagePainter.State.Loading -> {
                    if (errorRecorded) {
                        placeholderAt = recordPlaceholderVisible(request)
                        errorRecorded = false
                    } else if (placeholderAt == null) {
                        placeholderAt = recordPlaceholderVisible(request)
                    }
                }
                is AsyncImagePainter.State.Success -> {
                    previousSuccessPainter = current.painter
                    val startedAt = placeholderAt ?: recordPlaceholderVisible(request)
                    recordBind(request, startedAt)
                    completeVisible(lease, success = true)
                    withFrameNanos {
                        recordFirstDraw(request, startedAt)
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    val startedAt = placeholderAt ?: recordPlaceholderVisible(request)
                    if (!errorRecorded) {
                        errorRecorded = true
                        // Coil's listener records the typed failure; this terminal record adds the
                        // user-visible blank duration even when a stale image is retained.
                        recordTerminal(request, "error", startedAt, android.os.SystemClock.elapsedRealtime())
                    }
                    completeVisible(lease, success = false)
                }
            }
        }

        val displayPainter = when (val current = state) {
            is AsyncImagePainter.State.Success -> current.painter
            else -> previousSuccessPainter
        }
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            if (displayPainter != null) {
                Box(modifier = Modifier.matchParentSize()) {
                    Image(
                        painter = displayPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize(),
                    )
                    if (state is AsyncImagePainter.State.Error) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = errorContentDescription,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clickable { painter.restart() }
                                .semantics { contentDescription = errorContentDescription },
                        )
                    }
                }
            } else {
                when (state) {
                    is AsyncImagePainter.State.Error -> {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = errorContentDescription,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clickable { painter.restart() }
                                .semantics { contentDescription = errorContentDescription },
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    fun memorySnapshot(): ImageMemorySnapshot = ImageMemorySnapshot(
        bytes = imageLoader.memoryCache?.size ?: 0L,
        entries = imageLoader.memoryCache?.keys?.size ?: 0,
        evictions = evictionCount.get(),
    )

    companion object {
        private const val MAX_ACTIVE_DECODE = 3
        private const val MAX_PREFETCH_DECODE = 1
        private const val MAX_PREFETCH_JOBS = 6
        private const val MAX_CACHE_IDENTITIES = 256
        private const val MAX_MEMORY_CACHE_BYTES = 32L * 1024L * 1024L
        private const val MAX_DISK_CACHE_BYTES = 64L * 1024L * 1024L
        private const val DISK_CACHE_DIRECTORY = "robia_coil_images"
        private val instances = java.util.WeakHashMap<Context, ImagePipeline>()

        val IMAGE_PRIORITY_EXTRA = Extras.Key(ImagePriority.Visible)

        fun shared(
            context: Context,
            telemetry: ImageTelemetrySink = SampledImageTelemetrySink(),
        ): ImagePipeline {
            val appContext = context.applicationContext
            return synchronized(instances) {
                instances[appContext] ?: ImagePipeline(appContext, telemetry).also {
                    instances[appContext] = it
                }
            }
        }
    }
}

private data class CacheIdentity(
    val purpose: ImagePurpose,
    val priority: ImagePriority,
    val target: ImageTargetBounds,
)

/**
 * Coil 3 exposes cache state but not eviction callbacks. Disabling weak references lets this
 * adapter compare keys before/after capacity trims without mistaking intentional remove/clear calls
 * for evictions.
 */
private class TrackingMemoryCache(
    private val delegate: MemoryCache,
    private val onEvictions: (Set<MemoryCache.Key>) -> Unit,
) : MemoryCache by delegate {
    override fun set(key: MemoryCache.Key, value: MemoryCache.Value) {
        val keysBefore = delegate.keys
        delegate[key] = value
        onEvictions(keysBefore - delegate.keys)
    }

    override fun trimToSize(size: Long) {
        val keysBefore = delegate.keys
        delegate.trimToSize(size)
        onEvictions(keysBefore - delegate.keys)
    }
}

private data class ImageCacheStats(
    val memoryCacheBytes: Long?,
    val memoryCacheEntries: Int?,
    val memoryCacheKeys: Set<String>,
    val diskCacheBytes: Long?,
    val diskCacheEntries: Int?,
)

private class RobiaCoilEventListener(
    private val request: CoilImageRequest,
    private val telemetry: ImageTelemetrySink,
    private val cacheStats: () -> ImageCacheStats,
    private val activeDecodeCount: AtomicInteger,
) : EventListener() {
    private val requestId = request.memoryCacheKey ?: request.diskCacheKey ?: "unknown"
    private val purpose = request.getExtra(IMAGE_PURPOSE_EXTRA)
    private val priority = request.getExtra(ImagePipeline.IMAGE_PRIORITY_EXTRA)
    private val target = request.getExtra(IMAGE_TARGET_EXTRA)


    override fun resolveSizeStart(request: CoilImageRequest, sizeResolver: coil3.size.SizeResolver) {
        // The resolved bounds are recorded by resolveSizeEnd below.
    }

    override fun resolveSizeEnd(request: CoilImageRequest, size: coil3.size.Size) {
        record("resolve")
    }

    override fun keyEnd(request: CoilImageRequest, output: String?) {
        val hit = request.memoryCacheKey?.let { key ->
            cacheStats().memoryCacheKeys.contains(key)
        } == true
        record(stage = if (hit) "memory_hit" else "memory_miss", cache = if (hit) "memory" else "miss")
    }

    override fun fetchStart(request: CoilImageRequest, fetcher: coil3.fetch.Fetcher, options: coil3.request.Options) {
        // Fetch timing is represented by the resolve/decode stages used by the summary contract.
    }

    override fun decodeStart(request: CoilImageRequest, decoder: coil3.decode.Decoder, options: coil3.request.Options) {
        val active = activeDecodeCount.incrementAndGet()
        record("decode", activeDecodeCount = active)
    }

    override fun decodeEnd(
        request: CoilImageRequest,
        decoder: coil3.decode.Decoder,
        options: coil3.request.Options,
        result: coil3.decode.DecodeResult?,
    ) {
        val active = activeDecodeCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
        record("decode", activeDecodeCount = active)
    }

    override fun onCancel(request: CoilImageRequest) {
        record("cancel")
    }

    override fun onError(request: CoilImageRequest, result: coil3.request.ErrorResult) {
        record("error", error = result.throwable::class.simpleName ?: "image_error")
    }

    override fun onSuccess(request: CoilImageRequest, result: SuccessResult) {
        val cache = when (result.dataSource) {
            DataSource.MEMORY_CACHE -> "memory"
            DataSource.DISK -> "disk"
            DataSource.MEMORY -> "memory_decode"
            DataSource.NETWORK -> "network"
        }
        record(
            stage = when (result.dataSource) {
                DataSource.MEMORY_CACHE -> "memory_hit"
                DataSource.DISK -> "disk_hit"
                else -> "resolve"
            },
            cache = cache,
        )
    }

    private fun record(
        stage: String,
        cache: String? = null,
        activeDecodeCount: Int? = null,
        error: String? = null,
    ) {
        val stats = cacheStats()
        telemetry.record(
            ImageTelemetryEvent(
                stage = stage,
                requestId = requestId,
                purpose = purpose,
                priority = priority,
                target = target,
                cache = cache,
                activeDecodeCount = activeDecodeCount,
                memoryCacheBytes = stats.memoryCacheBytes,
                memoryCacheEntries = stats.memoryCacheEntries,
                diskCacheBytes = stats.diskCacheBytes,
                diskCacheEntries = stats.diskCacheEntries,
                error = error,
            ),
        )
    }
}

private enum class InFlightOwner {
    Visible,
    Prefetch,
}

private class InFlightRequest(
    val owner: InFlightOwner,
) {
    val completion = CompletableDeferred<Boolean>()
}

private class RequestLease(
    val key: String,
    val request: InFlightRequest,
    val isOwner: Boolean,
) {
    suspend fun await(): Boolean = request.completion.await()
}

private val IMAGE_PURPOSE_EXTRA = Extras.Key(ImagePurpose.Browse)
private val IMAGE_TARGET_EXTRA = Extras.Key(ImageTargetBounds(0, 0))
