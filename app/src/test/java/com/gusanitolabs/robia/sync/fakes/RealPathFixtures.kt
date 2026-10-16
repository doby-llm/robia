package com.gusanitolabs.robia.sync.fakes

import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.data.local.ClothingItemEntity
import com.gusanitolabs.robia.data.local.ColorMetricsEntity
import com.gusanitolabs.robia.sync.DriveSnapshotJson
import com.gusanitolabs.robia.sync.buildWardrobeSyncSnapshot

/** A garment row as the phone stores it (Room entity, no colour). */
internal fun garmentEntity(id: String, photoUri: String? = null, updatedAt: Long = 100L): ClothingItemEntity = ClothingItemEntity(
    id = id,
    name = id,
    notes = "",
    photoUri = photoUri,
    fitValue = null,
    colorMetrics = ColorMetricsEntity(null, null, null, null, null, null, null, null, null, null),
    isFavorite = false,
    isArchived = false,
    createdAtEpochMillis = updatedAt,
    updatedAtEpochMillis = updatedAt,
)

/** What the phone exports for [items], through the same mappers as `exportSnapshot`. */
internal fun exportedSnapshot(vararg items: ClothingItemEntity): WardrobeSyncSnapshot = buildWardrobeSyncSnapshot(
    items = items.toList(),
    itemTagRefs = emptyList(),
    categories = emptyList(),
    tags = emptyList(),
    colors = emptyList(),
    tombstones = emptyList(),
    embeddingsByGarmentId = emptyMap(),
    generatedAtEpochMillis = 1L,
)

/**
 * The backup Drive holds for [snapshot]: stamped with the writer and revision, then round-tripped through the JSON codec
 * the Drive repository uses, so every field the processor compares has been through the real encode/decode.
 */
internal fun driveBackup(snapshot: WardrobeSyncSnapshot, writer: String?, revision: Long): WardrobeSyncSnapshot {
    val stamped = snapshot.copy(metadata = snapshot.metadata.copy(writerDeviceId = writer, revision = revision))
    return DriveSnapshotJson.decode(DriveSnapshotJson.encode(stamped))
}

/** A writer other than [FAKE_DEVICE_ID]: another phone, or this account before a reinstall. */
internal const val OTHER_DEVICE_ID = "other-device"
