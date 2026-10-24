package com.gusanitolabs.robia.media

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** A file of the image folder, as the cleanup planner sees it. */
data class FileInfo(val name: String, val lastModifiedEpochMillis: Long, val sizeBytes: Long)

data class OrphanCleanupResult(val deletedCount: Int, val deletedBytes: Long)

/**
 * Photo URIs held by batch drafts and editor state that are not saved in the database yet. Registered URIs count as
 * referenced for the orphan cleanup. In memory only.
 */
object ActivePhotoRefs {
    private val uris = ConcurrentHashMap.newKeySet<String>()

    fun register(uri: String) {
        uris.add(uri)
    }

    fun unregister(uri: String) {
        uris.remove(uri)
    }

    fun snapshot(): Set<String> = uris.toSet()
}

/** One-time cleanup of the raw copies and subject PNGs that older builds left unreferenced (REQ-OR-03). */
object OrphanPhotoCleanup {
    internal const val LOG_TAG = "RobiaOrphanCleanup"
    private const val MIN_AGE_MILLIS = 24L * 60L * 60L * 1000L

    /** Raw copies, subjects and cropped cutouts of the image folder. Originals, restored files and other names are never listed. */
    private val ORPHAN_PREFIXES = listOf("gallery-", "camera-", "subject-", "cropped-subject-")

    /**
     * The files to delete: prefix in [ORPHAN_PREFIXES], not referenced, and older than 24 h. [referenced] holds photo URIs,
     * compared by last path segment.
     */
    fun planOrphanDeletion(files: List<FileInfo>, referenced: Set<String>, now: Long): List<FileInfo> {
        val referencedNames = referenced.mapTo(HashSet()) { uri -> uri.substringBefore('?').substringAfterLast('/') }
        return files.filter { file ->
            ORPHAN_PREFIXES.any { prefix -> file.name.startsWith(prefix) } &&
                file.name !in referencedNames &&
                now - file.lastModifiedEpochMillis > MIN_AGE_MILLIS
        }
    }

    /** Deletes the planned files directly inside [imageDir] and logs one line with the count and bytes. */
    fun deleteOrphans(imageDir: File, referenced: Set<String>, now: Long): OrphanCleanupResult {
        val filesByName = imageDir.listFiles().orEmpty()
            .filter { file -> file.isFile }
            .associateBy { file -> file.name }
        val planned = planOrphanDeletion(
            files = filesByName.values.map { file -> FileInfo(file.name, file.lastModified(), file.length()) },
            referenced = referenced,
            now = now,
        )
        var deletedCount = 0
        var deletedBytes = 0L
        planned.forEach { info ->
            if (filesByName.getValue(info.name).delete()) {
                deletedCount++
                deletedBytes += info.sizeBytes
            }
        }
        Log.i(LOG_TAG, "orphan_cleanup deleted=$deletedCount bytes=$deletedBytes")
        return OrphanCleanupResult(deletedCount = deletedCount, deletedBytes = deletedBytes)
    }
}
