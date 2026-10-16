package com.gusanitolabs.robia.sync.fakes

import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.data.local.ClothingItemEntity
import com.gusanitolabs.robia.data.local.ClothingItemTagCrossRef
import com.gusanitolabs.robia.data.local.ColorMetricsEntity
import com.gusanitolabs.robia.data.local.GarmentTagEntity
import com.gusanitolabs.robia.data.local.MainColorEntity
import com.gusanitolabs.robia.data.local.SyncTombstoneEntity
import com.gusanitolabs.robia.data.local.TagCategoryEntity
import com.gusanitolabs.robia.sync.DriveSnapshotJson
import com.gusanitolabs.robia.sync.buildWardrobeSyncSnapshot

/**
 * A garment row as the phone stores it (Room entity, no colour). Its sync revision is [updatedAt], as the migration sets it.
 * Pass [syncStatus] for a row that has not reached Drive; the default is a row that has.
 */
internal fun garmentEntity(
    id: String,
    photoUri: String? = null,
    updatedAt: Long = 100L,
    syncStatus: GarmentSyncStatus = GarmentSyncStatus.Synced,
): ClothingItemEntity = ClothingItemEntity(
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
    syncStatus = syncStatus,
)

/** A garment-to-tag mapping row, as the phone stores it. */
internal fun garmentTagRef(garmentId: String, tagId: String): ClothingItemTagCrossRef = ClothingItemTagCrossRef(garmentId, tagId)

/** A user tag row in [categoryId]. [syncStatus] `Synced` is the state after a Drive round trip. */
internal fun garmentTagEntity(
    id: String,
    categoryId: String = "occasion",
    syncStatus: GarmentSyncStatus = GarmentSyncStatus.Synced,
    revision: Long = 100L,
): GarmentTagEntity = GarmentTagEntity(id = id, categoryId = categoryId, name = id, sortOrder = 0, isSystem = false, syncStatus = syncStatus, syncRevision = revision)

internal fun tagCategoryEntity(id: String, syncStatus: GarmentSyncStatus = GarmentSyncStatus.Synced): TagCategoryEntity =
    TagCategoryEntity(id = id, name = id, sortOrder = 0, isSystem = false, syncStatus = syncStatus, syncRevision = 100L)

internal fun mainColorEntity(id: String, syncStatus: GarmentSyncStatus = GarmentSyncStatus.Synced): MainColorEntity =
    MainColorEntity(id = id, name = id, hex = "#000000", sortOrder = 0, isDefault = false, syncStatus = syncStatus, syncRevision = 100L)

/** A deletion row. The id follows the phone's convention, `<entityType>:<entityId>`. */
internal fun tombstoneEntity(
    entityType: String,
    entityId: String,
    revision: Long = 200L,
    syncStatus: GarmentSyncStatus = GarmentSyncStatus.Queued,
): SyncTombstoneEntity = SyncTombstoneEntity(
    id = "$entityType:$entityId",
    entityType = entityType,
    entityId = entityId,
    deletedAtEpochMillis = revision,
    revision = revision,
    syncStatus = syncStatus,
)

/** What the phone exports for the given rows, through the same mappers as `exportSnapshot`. */
internal fun exportedSnapshot(
    vararg items: ClothingItemEntity,
    tagRefs: List<ClothingItemTagCrossRef> = emptyList(),
    tags: List<GarmentTagEntity> = emptyList(),
    categories: List<TagCategoryEntity> = emptyList(),
    colors: List<MainColorEntity> = emptyList(),
    tombstones: List<SyncTombstoneEntity> = emptyList(),
): WardrobeSyncSnapshot = buildWardrobeSyncSnapshot(
    items = items.toList(),
    itemTagRefs = tagRefs,
    categories = categories,
    tags = tags,
    colors = colors,
    tombstones = tombstones,
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
