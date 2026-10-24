package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.sync.DriveSnapshotJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * "Download all my data (.zip)" (REQ-ZIP-01): streams the phone's own data into one archive, offline.
 *
 * Entries: `robia-data.json` (the Drive snapshot JSON, with each photo's `localUri` replaced by its archive path, or
 * blank when the file is missing), `photos/<garmentId>.<ext>`, `originals/<garmentId>.<ext>` for garments with an
 * original, and `README.txt`. Models, thumbnails and caches are not included. Photos are copied in fixed-size chunks,
 * never read whole into memory.
 *
 * [loadSnapshot] reads the local wardrobe; [openLocalFile] opens the stored file for a photo's `localUri` and returns
 * null (or throws an [IOException]) when that file is gone.
 */
class WardrobeZipExporter(
    private val loadSnapshot: suspend () -> WardrobeSyncSnapshot,
    private val openLocalFile: (localUri: String) -> InputStream?,
    private val appVersion: String,
) {
    /**
     * Writes the archive to a document opened by [open]. When the export is cancelled or fails, the stream is closed
     * and [discard] deletes the partial document before the exception is rethrown.
     */
    suspend fun writeArchive(
        open: () -> OutputStream,
        discard: () -> Unit,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val output = withContext(Dispatchers.IO) { open() }
        try {
            output.use { export(it, onProgress) }
        } catch (error: Throwable) {
            // NonCancellable: the delete must run even though the export was just cancelled.
            withContext(NonCancellable + Dispatchers.IO) { runCatching(discard) }
            throw error
        }
    }

    /**
     * Writes the archive to [output] and closes it once complete. [onProgress] reports (done, total) over the photo and
     * original files; it runs on the IO thread. Cancellation is checked between chunks, so an export stops within one
     * chunk and writes nothing further.
     */
    suspend fun export(
        output: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        val snapshot = loadSnapshot()
        val files = snapshot.photos.flatMap { record ->
            listOfNotNull(
                record.photoEntryName()?.let { entry -> ArchiveFile(entry, record.localUri) },
                record.originalEntryName()?.let { entry -> ArchiveFile(entry, record.localOriginalUri.orEmpty()) },
            )
        }
        val written = HashSet<String>()
        val missing = ArrayList<String>()
        val zip = ZipOutputStream(output)
        onProgress(0, files.size)
        files.forEachIndexed { index, file ->
            if (writeFile(zip, file)) written += file.entryName else missing += file.entryName
            onProgress(index + 1, files.size)
        }
        val archivedPhotos = snapshot.photos.map { record ->
            val entry = record.photoEntryName()
            record.copy(localUri = if (entry != null && entry in written) entry else "")
        }
        writeEntry(zip, DATA_ENTRY, DriveSnapshotJson.encode(snapshot.copy(photos = archivedPhotos)).toByteArray(Charsets.UTF_8))
        writeEntry(zip, README_ENTRY, readme(snapshot.metadata.schemaVersion, missing).toByteArray(Charsets.UTF_8))
        // finish() writes the central directory; close() then releases the deflater. Neither runs after a cancel.
        zip.finish()
        zip.close()
    }

    /** False, with no entry started, when the local file is gone. */
    private suspend fun writeFile(zip: ZipOutputStream, file: ArchiveFile): Boolean {
        val source = openSourceOrNull(file.localUri) ?: return false
        source.use { input ->
            zip.putNextEntry(ZipEntry(file.entryName))
            copyInChunks(input, zip)
            zip.closeEntry()
        }
        return true
    }

    /** A missing or unreadable file is skipped and listed in the README; the export carries on. */
    private fun openSourceOrNull(localUri: String): InputStream? = try {
        openLocalFile(localUri)
    } catch (error: IOException) {
        null
    }

    private suspend fun copyInChunks(source: InputStream, target: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = source.read(buffer)
            if (read < 0) return
            target.write(buffer, 0, read)
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun readme(schemaVersion: Int, missing: List<String>): String = buildString {
        appendLine("Robia data export")
        appendLine("Exported: ${Instant.now()}")
        appendLine("App version: $appVersion")
        appendLine("Format: schema version $schemaVersion")
        appendLine()
        appendLine("$DATA_ENTRY: the same JSON as the Google Drive backup. Each photo's localUri is its path in this archive.")
        appendLine("photos/: garment photos, one file per garment.")
        appendLine("originals/: original photos, for garments that have one.")
        appendLine()
        if (missing.isEmpty()) {
            appendLine("Missing files: none")
        } else {
            appendLine("Missing files (not on the phone at export time, so not in this archive):")
            missing.forEach { entry -> appendLine("  $entry") }
        }
    }

    private fun GarmentPhotoRecord.photoEntryName(): String? =
        localUri.takeIf(String::isNotBlank)?.let { uri -> "photos/$garmentId.${extensionOf(uri)}" }

    private fun GarmentPhotoRecord.originalEntryName(): String? =
        localOriginalUri?.takeIf(String::isNotBlank)?.let { uri -> "originals/$garmentId.${extensionOf(uri)}" }

    private fun extensionOf(localUri: String): String =
        localUri.substringAfterLast('.', "").lowercase().takeIf { it in PHOTO_EXTENSIONS } ?: DEFAULT_EXTENSION

    private class ArchiveFile(val entryName: String, val localUri: String)

    private companion object {
        const val DATA_ENTRY = "robia-data.json"
        const val README_ENTRY = "README.txt"
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val DEFAULT_EXTENSION = "jpg"
        val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
