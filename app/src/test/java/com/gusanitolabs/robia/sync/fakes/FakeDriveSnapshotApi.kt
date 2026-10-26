package com.gusanitolabs.robia.sync.fakes

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.DriveApiResult
import com.gusanitolabs.robia.sync.DriveBlob
import com.gusanitolabs.robia.sync.DriveFileMetadata
import com.gusanitolabs.robia.sync.DriveRateLimitGate
import com.gusanitolabs.robia.sync.DriveSnapshotApi
import com.gusanitolabs.robia.sync.UploadedBlobInfo
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory [DriveSnapshotApi]. File ids are `id-<name>`.
 *
 * [failures] replaces the result of [fetchBlobToFile] for one file name. [downloadDelayMillis] keeps
 * each download open so tests can observe how many run at once through [maxInFlight].
 *
 * [script] answers the next calls of one method with the given results, in order, before the normal behaviour.
 * [refreshAccessToken] stands in for authorize(): it counts in [authorizeCalls] and returns [FRESH_TOKEN].
 * [tokensSeen] records the access token of every call, in order.
 */
class FakeDriveSnapshotApi(
    private val downloadDelayMillis: Long = 0L,
    private val uploadDelayMillis: Long = 0L,
) : DriveSnapshotApi {
    val files = ConcurrentHashMap<String, ByteArray>()
    val failures = ConcurrentHashMap<String, DriveApiResult<Nothing>>()
    val listCalls = AtomicInteger(0)
    val blobUploads = AtomicInteger(0)
    val snapshotUploads = AtomicInteger(0)
    val maxInFlight = AtomicInteger(0)
    /** Blob writes that create a file (no id known to the caller), per blob name: a value above 1 is a duplicate. */
    val creates = ConcurrentHashMap<String, AtomicInteger>()
    /** Lookups by name that the HTTP layer makes before creating a blob whose id the caller does not know. */
    val findLookups = AtomicInteger(0)
    /** Every blob write fails with this result for that name, and is never stored. */
    val blobFailures = ConcurrentHashMap<String, DriveApiResult<Nothing>>()
    /** The lookup for that name fails with this result (the HTTP layer returns it, never a create). */
    val findFailures = ConcurrentHashMap<String, DriveApiResult<Nothing>>()
    /** The next write of that name stores the bytes, then answers with this result, once: Drive kept the file but the response was lost. */
    val storeThenFail = ConcurrentHashMap<String, DriveApiResult<Nothing>>()
    val maxUploadInFlight = AtomicInteger(0)
    private val uploadsInFlight = AtomicInteger(0)
    /** When set, the first blob write reports a rate limit to the cycle, as a 429 retried inside the HTTP layer would. */
    var rateLimitOnFirstWrite = false
    private val firstWrite = AtomicBoolean(true)
    /** The cycle's upload concurrency seen by each blob write, in call order. */
    val concurrencySeen = CopyOnWriteArrayList<Int>()
    /** When set, the next snapshot JSON upload fails with this result (V-S03c). */
    var snapshotFailure: DriveApiResult<Nothing>? = null
    private val inFlight = AtomicInteger(0)
    private val scripted = ConcurrentHashMap<String, ConcurrentLinkedQueue<DriveApiResult<Nothing>>>()
    val authorizeCalls = AtomicInteger(0)
    val tokensSeen = CopyOnWriteArrayList<String>()

    /** Queues [results] for the next calls of [method] (fetchSnapshot, listAll, upsertBlob, upsertSnapshot, deleteBlob, fetchBlobToFile). */
    fun script(method: String, vararg results: DriveApiResult<Nothing>) {
        scripted.getOrPut(method) { ConcurrentLinkedQueue() }.addAll(results.toList())
    }

    suspend fun refreshAccessToken(): String? {
        authorizeCalls.incrementAndGet()
        return FRESH_TOKEN
    }

    private fun scriptedResult(method: String): DriveApiResult<Nothing>? = scripted[method]?.poll()

    override suspend fun fetchSnapshot(accessToken: String): DriveApiResult<WardrobeSyncSnapshot> {
        tokensSeen += accessToken
        return scriptedResult("fetchSnapshot") ?: DriveApiResult.NotFound
    }

    override suspend fun fetchBlob(
        accessToken: String,
        blobPath: String,
        onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit,
        fileId: String?,
    ): DriveApiResult<DriveBlob> {
        val bytes = files[blobPath] ?: return DriveApiResult.NotFound
        return DriveApiResult.Success(DriveBlob(bytes = bytes))
    }

    override suspend fun fetchBlobToFile(accessToken: String, fileId: String, target: File): DriveApiResult<Long> {
        tokensSeen += accessToken
        scriptedResult("fetchBlobToFile")?.let { result -> return result }
        val name = nameOf(fileId)
        failures[name]?.let { failure -> return failure }
        val bytes = files[name] ?: return DriveApiResult.NotFound
        maxInFlight.accumulateAndGet(inFlight.incrementAndGet()) { a, b -> maxOf(a, b) }
        try {
            delay(downloadDelayMillis)
            target.writeBytes(bytes)
            return DriveApiResult.Success(bytes.size.toLong())
        } finally {
            inFlight.decrementAndGet()
        }
    }

    override suspend fun listAll(accessToken: String): DriveApiResult<List<DriveFileMetadata>> {
        listCalls.incrementAndGet()
        tokensSeen += accessToken
        scriptedResult("listAll")?.let { result -> return result }
        // Like the real listing, each file carries its size: the planner reuses a blob only while the size matches.
        return DriveApiResult.Success(
            files.entries.sortedBy { entry -> entry.key }.map { (name, bytes) ->
                DriveFileMetadata(id = idOf(name), name = name, size = bytes.size.toLong())
            },
        )
    }

    override suspend fun listBlobPathsWithPrefix(accessToken: String, blobPathPrefix: String): DriveApiResult<List<String>> =
        DriveApiResult.Success(files.keys.filter { name -> name.startsWith(blobPathPrefix) }.sorted())

    override suspend fun upsertBlob(
        accessToken: String,
        blobPath: String,
        bytes: ByteArray,
        mimeType: String?,
        fileId: String?,
        knownAbsent: Boolean,
    ): DriveApiResult<UploadedBlobInfo> {
        tokensSeen += accessToken
        scriptedResult("upsertBlob")?.let { result -> return result }
        // The HTTP layer looks a name up before creating it unless the caller knows the id or that the name is absent.
        if (fileId == null && !knownAbsent) {
            findLookups.incrementAndGet()
            findFailures[blobPath]?.let { failure -> return failure }
        }
        blobFailures[blobPath]?.let { failure -> return failure }
        currentCoroutineContext()[DriveRateLimitGate]?.let { gate -> concurrencySeen += gate.concurrency }
        if (rateLimitOnFirstWrite && firstWrite.getAndSet(false)) {
            currentCoroutineContext()[DriveRateLimitGate]?.onRateLimited()
        }
        val inFlight = uploadsInFlight.incrementAndGet()
        try {
            maxUploadInFlight.accumulateAndGet(inFlight) { a, b -> maxOf(a, b) }
            if (uploadDelayMillis > 0L) delay(uploadDelayMillis)
            blobUploads.incrementAndGet()
            if (fileId == null) creates.getOrPut(blobPath) { AtomicInteger(0) }.incrementAndGet()
            files[blobPath] = bytes
            storeThenFail.remove(blobPath)?.let { failure -> return failure }
            return DriveApiResult.Success(UploadedBlobInfo(size = bytes.size.toLong(), sha256 = sha256Hex(bytes)))
        } finally {
            uploadsInFlight.decrementAndGet()
        }
    }

    /** The lookup by name that the HTTP layer makes before a create: the id of the stored blob, null when absent, or [findFailures]. */
    fun lookupId(blobPath: String): DriveApiResult<String?> {
        findLookups.incrementAndGet()
        findFailures[blobPath]?.let { failure -> return failure }
        return DriveApiResult.Success(if (files.containsKey(blobPath)) idOf(blobPath) else null)
    }

    override suspend fun deleteBlob(accessToken: String, blobPath: String, fileId: String?): DriveApiResult<Unit> {
        tokensSeen += accessToken
        scriptedResult("deleteBlob")?.let { result -> return result }
        files.remove(blobPath)
        return DriveApiResult.Success(Unit)
    }

    override suspend fun listBackupFiles(accessToken: String): DriveApiResult<List<DriveFileMetadata>> =
        DriveApiResult.Success(emptyList<DriveFileMetadata>())

    override suspend fun deleteFile(accessToken: String, fileId: String): DriveApiResult<Unit> = DriveApiResult.Success(Unit)

    override suspend fun upsertSnapshot(accessToken: String, snapshot: WardrobeSyncSnapshot): DriveApiResult<WardrobeSyncSnapshot> {
        tokensSeen += accessToken
        scriptedResult("upsertSnapshot")?.let { result -> return result }
        snapshotUploads.incrementAndGet()
        snapshotFailure?.let { failure -> return failure }
        return DriveApiResult.Success(snapshot)
    }

    private fun idOf(name: String): String = "id-$name"

    private fun nameOf(fileId: String): String = fileId.removePrefix("id-")

    companion object {
        const val FRESH_TOKEN = "fresh-token"
    }
}
