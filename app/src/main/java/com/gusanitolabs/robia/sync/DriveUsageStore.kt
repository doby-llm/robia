package com.gusanitolabs.robia.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Backs `filesDir/sync/drive_usage.json`. A missing or corrupt file reads as null (not measured yet).
 * [latest] follows every successful [write], so the sync state shows a new measurement without a restart.
 */
class DriveUsageStore(private val file: File) {
    private val mutableLatest = MutableStateFlow(read())
    val latest: StateFlow<DriveUsage?> = mutableLatest.asStateFlow()

    fun read(): DriveUsage? = runCatching { JSONObject(file.readText()).toDriveUsage() }.getOrNull()

    /** Writes a temp file and renames it over the target, so a crash never leaves a half-written file. */
    @Synchronized
    fun write(usage: DriveUsage) {
        file.parentFile?.mkdirs()
        val temp = File("${file.path}.tmp")
        temp.writeText(usage.toJson().toString())
        check(temp.renameTo(file)) { "Unable to replace ${file.name}." }
        mutableLatest.value = usage
    }
}

private fun DriveUsage.toJson(): JSONObject = JSONObject()
    .put("totalBytes", totalBytes)
    .put("photoCount", photoCount)
    .put("photoBytes", photoBytes)
    .put("backupCount", backupCount)
    .put("measuredAtEpochMillis", measuredAtEpochMillis)

private fun JSONObject.toDriveUsage(): DriveUsage = DriveUsage(
    totalBytes = getLong("totalBytes"),
    photoCount = getInt("photoCount"),
    photoBytes = getLong("photoBytes"),
    backupCount = getInt("backupCount"),
    measuredAtEpochMillis = getLong("measuredAtEpochMillis"),
)
