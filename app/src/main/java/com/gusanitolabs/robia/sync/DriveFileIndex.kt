package com.gusanitolabs.robia.sync

/**
 * In-memory name → Drive file lookup, built once per sync cycle from a single listing.
 *
 * When Drive holds several files with the same name, the most recently modified one wins,
 * matching the ordering of the single-file lookup it replaces.
 *
 * [complete] is true only for a listing of every file in the app data folder; then a name the index lacks is absent in Drive.
 */
class DriveFileIndex(files: List<DriveFileMetadata>, private val complete: Boolean = false) {
    private val filesByName = HashMap<String, DriveFileMetadata>()

    init {
        files.sortedBy { file -> file.modifiedTime.orEmpty() }.forEach(::put)
    }

    fun idOf(name: String): String? = filesByName[name]?.id

    /** The size Drive lists for this file; null when the listing does not carry one. */
    fun sizeOf(name: String): Long? = filesByName[name]?.size

    /** True when the full listing lacks [name], so a lookup in Drive would find nothing. */
    fun knownAbsent(name: String): Boolean = complete && idOf(name) == null

    fun namesWithPrefix(prefix: String): List<String> = filesByName.keys
        .filter { name -> name.startsWith(prefix) }
        .sorted()

    fun put(meta: DriveFileMetadata) {
        if (meta.name.isEmpty()) return
        filesByName[meta.name] = meta
    }

    fun remove(name: String) {
        filesByName.remove(name)
    }
}
