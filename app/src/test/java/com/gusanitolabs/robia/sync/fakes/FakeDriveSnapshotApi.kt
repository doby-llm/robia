package com.gusanitolabs.robia.sync.fakes

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.DriveApiResult
import com.gusanitolabs.robia.sync.DriveBlob
import com.gusanitolabs.robia.sync.DriveFileMetadata
import com.gusanitolabs.robia.sync.DriveSnapshotApi
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory [DriveSnapshotApi]. File ids are `id-<name>`.
 *
 * [failures] replaces the result of [fetchBlobToFile] for one file name. [downloadDelayMillis] keeps
 * each download open so tests can observe how many run at once through [maxInFlight].
 */
class FakeDriveSnapshotApi(
    private val downloadDelayMillis: Long = 0L,
) : DriveSnapshotApi {
    val files = ConcurrentHashMap<String, ByteArray>()
    val failures = ConcurrentHashMap<String, DriveApiResult<Nothing>>()
    val listCalls = AtomicInteger(0)
    val blobUploads = AtomicInteger(0)
    val snapshotUploads = AtomicInteger(0)
    val maxInFlight = AtomicInteger(0)
    private val inFlight = AtomicInteger(0)

    override suspend fun fetchSnapshot(accessToken: String): DriveApiResult<WardrobeSyncSnapshot> = DriveApiResult.NotFound

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
        return DriveApiResult.Success(files.keys.sorted().map { name -> DriveFileMetadata(id = idOf(name), name = name) })
    }

    override suspend fun listBlobPathsWithPrefix(accessToken: String, blobPathPrefix: String): DriveApiResult<List<String>> =
        DriveApiResult.Success(files.keys.filter { name -> name.startsWith(blobPathPrefix) }.sorted())

    override suspend fun upsertBlob(
        accessToken: String,
        blobPath: String,
        bytes: ByteArray,
        mimeType: String?,
        fileId: String?,
    ): DriveApiResult<Unit> {
        blobUploads.incrementAndGet()
        files[blobPath] = bytes
        return DriveApiResult.Success(Unit)
    }

    override suspend fun deleteBlob(accessToken: String, blobPath: String, fileId: String?): DriveApiResult<Unit> {
        files.remove(blobPath)
        return DriveApiResult.Success(Unit)
    }

    override suspend fun listBackupFiles(accessToken: String): DriveApiResult<List<DriveFileMetadata>> =
        DriveApiResult.Success(emptyList<DriveFileMetadata>())

    override suspend fun deleteFile(accessToken: String, fileId: String): DriveApiResult<Unit> = DriveApiResult.Success(Unit)

    override suspend fun upsertSnapshot(accessToken: String, snapshot: WardrobeSyncSnapshot): DriveApiResult<WardrobeSyncSnapshot> {
        snapshotUploads.incrementAndGet()
        return DriveApiResult.Success(snapshot)
    }

    private fun idOf(name: String): String = "id-$name"

    private fun nameOf(fileId: String): String = fileId.removePrefix("id-")
}
