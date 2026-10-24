package com.gusanitolabs.robia.sync

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentColorRole
import com.gusanitolabs.robia.core.model.EmbeddingSyncRecord
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.GarmentTagMappingRecord
import com.gusanitolabs.robia.core.model.MainColorSyncRecord
import com.gusanitolabs.robia.core.model.SyncTombstoneRecord
import com.gusanitolabs.robia.core.model.TagCategorySyncRecord
import com.gusanitolabs.robia.core.model.TagSyncRecord
import com.gusanitolabs.robia.core.model.WardrobeSnapshotMetadata
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot
import com.gusanitolabs.robia.data.local.ClothingItemEntity
import com.gusanitolabs.robia.data.local.ClothingItemTagCrossRef
import com.gusanitolabs.robia.data.local.ColorMetricsEntity
import com.gusanitolabs.robia.data.local.EmbeddingKind
import com.gusanitolabs.robia.data.local.GarmentEmbeddingEntity
import com.gusanitolabs.robia.data.local.GarmentTagEntity
import com.gusanitolabs.robia.data.local.MainColorEntity
import com.gusanitolabs.robia.data.local.RobiaDatabase
import com.gusanitolabs.robia.data.local.RestoredPhotoApplyStateEntity
import com.gusanitolabs.robia.data.local.SyncTombstoneDao
import com.gusanitolabs.robia.data.local.SyncTombstoneEntity
import com.gusanitolabs.robia.data.local.TagCategoryEntity
import com.gusanitolabs.robia.data.local.TagDao
import com.gusanitolabs.robia.data.local.WardrobeDao
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import java.util.Base64

/** Local snapshot operations the sync processor needs; JVM tests fake this seam instead of Room. */
interface WardrobeSnapshotStore {
    suspend fun exportSnapshot(generatedAtEpochMillis: Long = System.currentTimeMillis()): WardrobeSyncSnapshot
    suspend fun importSnapshot(snapshot: WardrobeSyncSnapshot): ImportSnapshotResult

    /**
     * [importSnapshot] only while the phone still holds [expectedLocal], the export the cycle merged against. The check and
     * the write share one transaction. Returns null, and writes nothing, when a local edit landed after that export.
     */
    suspend fun importSnapshotIfLocalUnchanged(expectedLocal: WardrobeSyncSnapshot, snapshot: WardrobeSyncSnapshot): ImportSnapshotResult?
    suspend fun applyRestoredPhoto(photo: GarmentPhotoRecord, expectedRevision: Long): RestoredPhotoApplyResult
    suspend fun hasGuardedPhotoRestoreIssues(): Boolean
    suspend fun replaceLocalWithRemote(snapshot: WardrobeSyncSnapshot): ImportSnapshotResult
}

/** Builds deterministic snapshots of the full local wardrobe graph for Drive sync. */
class LocalWardrobeSyncSnapshotRepository(
    private val database: RobiaDatabase,
    private val wardrobeDao: WardrobeDao,
    private val tagDao: TagDao,
    private val syncTombstoneDao: SyncTombstoneDao,
) : WardrobeSnapshotStore {
    override suspend fun exportSnapshot(generatedAtEpochMillis: Long): WardrobeSyncSnapshot {
        val items = wardrobeDao.getAllItemsForSync()
            .map { itemWithTags -> itemWithTags.item }
            .filterNot(ClothingItemEntity::isArchived)
        val activeItemIds = items.map(ClothingItemEntity::id).toSet()
        val itemTagRefs = wardrobeDao.getItemTagRefsForSync()
            .filter { ref -> ref.clothingItemId in activeItemIds }
        val categories = tagDao.getCategoriesForSync()
        val tags = tagDao.getTagsForSync()
        val colors = tagDao.getMainColorsForSync()
        val tombstones = syncTombstoneDao.getAllForSync()
        val embeddingsByGarmentId = database.embeddingDao()
            .getForRecipe(EmbeddingRecipe.MODEL_ID, EmbeddingRecipe.RECIPE_ID)
            .groupBy(GarmentEmbeddingEntity::garmentId)
        return buildWardrobeSyncSnapshot(
            items = items,
            itemTagRefs = itemTagRefs,
            categories = categories,
            tags = tags,
            colors = colors,
            tombstones = tombstones,
            embeddingsByGarmentId = embeddingsByGarmentId,
            generatedAtEpochMillis = generatedAtEpochMillis,
        )
    }

    /**
     * Applies a fetched Drive snapshot as one Room transaction. Photo rows only receive private-storage
     * URIs that were materialized from Drive blobs during fetch; failed blob hydration aborts before this
     * transaction so a fresh install is never committed with placeholder-only garments.
     *
     * Targeted guarded-photo retry uses [applyRestoredPhoto] instead of this bulk import because it
     * must resolve exactly one pre-existing guarded row that has already been claimed as Running.
     */
    override suspend fun importSnapshot(snapshot: WardrobeSyncSnapshot): ImportSnapshotResult = database.withTransaction {
        importRecords(snapshot, phoneHeldPhotoUris = emptyMap(), localItemsById = localItemsFor(snapshot))
    }

    /**
     * The local rows of the snapshot's garments. Read inside the import's transaction; "Use backup" reads them before it
     * clears the wardrobe, because the rows are gone after the delete.
     */
    private suspend fun localItemsFor(snapshot: WardrobeSyncSnapshot): Map<String, ClothingItemEntity> {
        if (snapshot.garments.isEmpty()) return emptyMap()
        return tagDao.clothingItemsByIds(snapshot.garments.map(GarmentSyncRecord::id)).associateBy(ClothingItemEntity::id)
    }

    private suspend fun importRecords(
        snapshot: WardrobeSyncSnapshot,
        phoneHeldPhotoUris: Map<String, String>,
        localItemsById: Map<String, ClothingItemEntity>,
    ): ImportSnapshotResult {
        val deterministicSnapshot = snapshot.sortedDeterministically().withoutTombstonedTaxonomy()
        val restoredItems = restoredClothingItems(deterministicSnapshot, phoneHeldPhotoUris, localItemsById)
        val itemIds = restoredItems.map(ClothingItemEntity::id)
        val itemIdSet = itemIds.toSet()
        val tagRefs = deterministicSnapshot.garmentTags
            .filter { record -> record.garmentId in itemIdSet }
            .map { record -> ClothingItemTagCrossRef(record.garmentId, record.tagId) }
        val tombstones = deterministicSnapshot.tombstones.map(SyncTombstoneRecord::toEntity)
        val embeddings = deterministicSnapshot.garments
            .filter { garment -> garment.id in itemIdSet }
            .flatMap { garment -> garment.embeddings.mapNotNull { record -> record.toImportEntity(garment.id) } }

        database.withTransaction {
            deterministicSnapshot.taxonomies.categories.forEach { record -> tagDao.upsertCategory(record.toEntity()) }
            tagDao.upsertTags(deterministicSnapshot.taxonomies.tags.map(TagSyncRecord::toEntity))
            tagDao.upsertMainColors(deterministicSnapshot.taxonomies.mainColors.map(MainColorSyncRecord::toEntity))
            if (restoredItems.isNotEmpty()) {
                tagDao.upsertClothingItems(restoredItems)
                tagDao.clearTagsForItems(itemIds)
                tagDao.insertItemTagRefs(tagRefs)
            }
            if (tombstones.isNotEmpty()) tagDao.upsertSyncTombstones(tombstones)
            // A vector re-computed while the download ran is newer than the imported one: keep it, still pending.
            val embeddingDao = database.embeddingDao()
            val localEmbeddings = embeddings.mapNotNull { imported -> embeddingDao.get(imported.garmentId, imported.kind) }
            val embeddingsToWrite = embeddings.withoutOlderThanLocal(localEmbeddings)
            if (embeddingsToWrite.isNotEmpty()) embeddingDao.upsertAll(embeddingsToWrite)
        }

        return ImportSnapshotResult(
            restoredGarmentCount = restoredItems.count { item -> !item.isArchived },
            restoredPhotoCount = deterministicSnapshot.photos.count { photo -> photo.restorableLocalUri() != null },
            guardedPhotoCount = deterministicSnapshot.photos.count { photo -> photo.restorableLocalUri() == null },
        )
    }

    override suspend fun importSnapshotIfLocalUnchanged(expectedLocal: WardrobeSyncSnapshot, snapshot: WardrobeSyncSnapshot): ImportSnapshotResult? =
        database.withTransaction<ImportSnapshotResult?> {
            // Re-exported in the same transaction as the writes, so the comparison and the writes see one database state. A write
            // that commits concurrently makes this transaction fail; it cannot slip in unnoticed. Embeddings are not compared
            // (importSnapshot keeps a newer vector), so a vector recomputed during the download does not block the import.
            // Both sides are local exports. unchangedSince compares the photo list exactly: a photo swap keeps the garment revision.
            if (!exportSnapshot().unchangedSince(expectedLocal)) return@withTransaction null
            // The phone's own files are kept where the merged record has no restorable file (see [importedPhotoUri]).
            importRecords(
                snapshot,
                phoneHeldPhotoUris = expectedLocal.photos.filter { it.localUri.isNotBlank() }.associate { it.garmentId to it.localUri },
                localItemsById = localItemsFor(snapshot),
            )
        }

    /** Persists only the successfully rehydrated image; garment metadata remains untouched. */
    override suspend fun applyRestoredPhoto(photo: GarmentPhotoRecord, expectedRevision: Long): RestoredPhotoApplyResult =
        database.withTransaction {
            val rowBefore = wardrobeDao.restoredPhotoApplyState(photo.garmentId)
            val restoredUri = photo.restoredLocalUri?.takeIf(String::isNotBlank)
            val guardedReason = photo.restoreFailureCategory?.takeIf(String::isNotBlank)
            if (restoredUri == null || guardedReason != null) {
                return@withTransaction RestoredPhotoApplyResult.NotApplied(
                    garmentId = photo.garmentId,
                    expectedRevision = expectedRevision,
                    restoredUriPresent = restoredUri != null,
                    importedPhotoUsable = restoredUri != null && guardedReason == null,
                    daoRowsUpdated = 0,
                    reason = when {
                        restoredUri == null -> "restored_uri_absent"
                        else -> "import_guarded:$guardedReason"
                    },
                    rowBefore = rowBefore,
                    rowAfter = rowBefore,
                    blobPath = photo.blobPath,
                )
            }

            val rowsUpdated = wardrobeDao.applyRestoredPhoto(
                itemId = photo.garmentId,
                photoUri = restoredUri,
                revision = expectedRevision,
                syncedAtEpochMillis = System.currentTimeMillis(),
            )
            if (rowsUpdated == 1) applyRestoredOriginal(photo)
            val rowAfter = wardrobeDao.restoredPhotoApplyState(photo.garmentId)
            val persisted = rowAfter?.let { row ->
                row.photoUri == restoredUri &&
                    !row.photoRestoreGuarded &&
                    row.syncStatus in setOf(GarmentSyncStatus.Synced, GarmentSyncStatus.Queued)
            } == true
            if (rowsUpdated == 1 && persisted) {
                RestoredPhotoApplyResult.Applied(
                    garmentId = photo.garmentId,
                    expectedRevision = expectedRevision,
                    restoredUriPresent = true,
                    importedPhotoUsable = true,
                    daoRowsUpdated = rowsUpdated,
                    rowBefore = rowBefore,
                    rowAfter = rowAfter,
                    blobPath = photo.blobPath,
                )
            } else {
                RestoredPhotoApplyResult.NotApplied(
                    garmentId = photo.garmentId,
                    expectedRevision = expectedRevision,
                    restoredUriPresent = true,
                    importedPhotoUsable = true,
                    daoRowsUpdated = rowsUpdated,
                    reason = rowBefore.zeroUpdateReason(expectedRevision, persisted),
                    rowBefore = rowBefore,
                    rowAfter = rowAfter,
                    blobPath = photo.blobPath,
                )
            }
        }

    /**
     * The original a targeted retry downloaded (REQ-OR-04). The photo row was just written in this transaction; this writes the
     * original columns of that same row, so a retried garment gets its original as a bulk restore gives it.
     */
    private suspend fun applyRestoredOriginal(photo: GarmentPhotoRecord) {
        val originalUri = photo.restoredOriginalLocalUri?.takeIf(String::isNotBlank) ?: return
        val item = tagDao.clothingItemsByIds(listOf(photo.garmentId)).singleOrNull() ?: return
        tagDao.upsertClothingItems(listOf(item.copy(originalUri = originalUri, originalRecipe = photo.recipe)))
    }

    override suspend fun hasGuardedPhotoRestoreIssues(): Boolean = wardrobeDao.observeGuardedPhotoRestoreCount().first() > 0

    /**
     * "Use backup": the caller has already fetched and decoded [snapshot], so nothing is cleared before this
     * point. Clearing and importing share one Room transaction; any exception rolls both back and leaves the
     * local wardrobe untouched. Local photo files are deliberately not deleted here. The local original photo and
     * recipe of each garment the backup still has are written back; the originals of garments the backup lacks are
     * not deleted and stay as orphan files until cleanup (CLN).
     */
    override suspend fun replaceLocalWithRemote(snapshot: WardrobeSyncSnapshot): ImportSnapshotResult =
        database.withTransaction {
            val localItemsById = localItemsFor(snapshot)
            database.embeddingDao().deleteEverything()
            wardrobeDao.deleteAllItemTagRefs()
            wardrobeDao.deleteAllItems()
            importRecords(snapshot, phoneHeldPhotoUris = emptyMap(), localItemsById = localItemsById)
        }
}

data class ImportSnapshotResult(
    val restoredGarmentCount: Int,
    val restoredPhotoCount: Int,
    val guardedPhotoCount: Int,
)

sealed interface RestoredPhotoApplyResult {
    val garmentId: String
    val expectedRevision: Long
    val restoredUriPresent: Boolean
    val importedPhotoUsable: Boolean
    val daoRowsUpdated: Int
    val rowBefore: RestoredPhotoApplyStateEntity?
    val rowAfter: RestoredPhotoApplyStateEntity?
    val blobPath: String
    val applied: Boolean

    data class Applied(
        override val garmentId: String,
        override val expectedRevision: Long,
        override val restoredUriPresent: Boolean,
        override val importedPhotoUsable: Boolean,
        override val daoRowsUpdated: Int,
        override val rowBefore: RestoredPhotoApplyStateEntity?,
        override val rowAfter: RestoredPhotoApplyStateEntity?,
        override val blobPath: String,
    ) : RestoredPhotoApplyResult {
        override val applied: Boolean = true
    }

    data class NotApplied(
        override val garmentId: String,
        override val expectedRevision: Long,
        override val restoredUriPresent: Boolean,
        override val importedPhotoUsable: Boolean,
        override val daoRowsUpdated: Int,
        val reason: String,
        override val rowBefore: RestoredPhotoApplyStateEntity?,
        override val rowAfter: RestoredPhotoApplyStateEntity?,
        override val blobPath: String,
    ) : RestoredPhotoApplyResult {
        override val applied: Boolean = false
    }
}

/**
 * The pure assembly behind [exportSnapshot]. Room does not run on the JVM, so the sync tests build their
 * local side and remote backups through this function and the real mappers: no hand-made snapshots.
 */
internal fun buildWardrobeSyncSnapshot(
    items: List<ClothingItemEntity>,
    itemTagRefs: List<ClothingItemTagCrossRef>,
    categories: List<TagCategoryEntity>,
    tags: List<GarmentTagEntity>,
    colors: List<MainColorEntity>,
    tombstones: List<SyncTombstoneEntity>,
    embeddingsByGarmentId: Map<String, List<GarmentEmbeddingEntity>>,
    generatedAtEpochMillis: Long,
): WardrobeSyncSnapshot {
    val itemUpdatedAtById = items.associate { item -> item.id to item.syncRevision }
    val taxonomyRevisions = categories.map(TagCategoryEntity::syncRevision) +
        tags.map(GarmentTagEntity::syncRevision) +
        colors.map(MainColorEntity::syncRevision)
    val snapshotRevision = maxOf(
        itemUpdatedAtById.values.maxOrNull() ?: 0L,
        taxonomyRevisions.maxOrNull() ?: 0L,
        tombstones.maxOfOrNull(SyncTombstoneEntity::revision) ?: 0L,
        generatedAtEpochMillis,
    )

    return WardrobeSyncSnapshot(
        metadata = WardrobeSnapshotMetadata(
            generatedAtEpochMillis = generatedAtEpochMillis,
            revision = snapshotRevision,
        ),
        taxonomies = WardrobeTaxonomySnapshot(
            categories = categories.map(TagCategoryEntity::toSyncRecord),
            tags = tags.map(GarmentTagEntity::toSyncRecord),
            mainColors = colors.map(MainColorEntity::toSyncRecord),
        ),
        garments = items.map { item ->
            item.toGarmentRecord(embeddingsByGarmentId[item.id].orEmpty().map(GarmentEmbeddingEntity::toSyncRecord))
        },
        garmentTags = itemTagRefs.map { ref ->
            ref.toSyncRecord(revision = itemUpdatedAtById[ref.clothingItemId] ?: 0L)
        },
        garmentColors = items.flatMap(ClothingItemEntity::toColorRecords),
        photos = items.mapNotNull(ClothingItemEntity::toPhotoRecord),
        tombstones = tombstones.map(SyncTombstoneEntity::toSyncRecord),
    ).sortedDeterministically()
}

private fun TagCategoryEntity.toSyncRecord(): TagCategorySyncRecord = TagCategorySyncRecord(
    id = id,
    name = name,
    sortOrder = sortOrder,
    isSystem = isSystem,
    revision = syncRevision,
    updatedAtEpochMillis = syncRevision,
)

private fun GarmentTagEntity.toSyncRecord(): TagSyncRecord = TagSyncRecord(
    id = id,
    categoryId = categoryId,
    name = name,
    sortOrder = sortOrder,
    isSystem = isSystem,
    revision = syncRevision,
    updatedAtEpochMillis = syncRevision,
    description = description,
    suggestOccasions = suggestOccasions,
)

private fun MainColorEntity.toSyncRecord(): MainColorSyncRecord = MainColorSyncRecord(
    id = id,
    name = name,
    hex = hex,
    sortOrder = sortOrder,
    isDefault = isDefault,
    revision = syncRevision,
    updatedAtEpochMillis = syncRevision,
)

private fun ClothingItemEntity.toGarmentRecord(embeddings: List<EmbeddingSyncRecord>): GarmentSyncRecord = GarmentSyncRecord(
    id = id,
    name = name,
    notes = notes,
    fitValue = fitValue,
    isFavorite = isFavorite,
    isArchived = isArchived,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    revision = syncRevision,
    embeddings = embeddings,
)

private fun GarmentEmbeddingEntity.toSyncRecord(): EmbeddingSyncRecord = EmbeddingSyncRecord(
    kind = kind,
    modelId = modelId,
    recipeId = recipeId,
    dim = dim,
    inputHash = inputHash,
    computedAtEpochMillis = computedAtEpochMillis,
    b64 = Base64.getEncoder().encodeToString(vector),
)

/** Drops imported vectors that a local row computed later would overwrite; equal or missing local rows are written. */
internal fun List<GarmentEmbeddingEntity>.withoutOlderThanLocal(
    localRows: List<GarmentEmbeddingEntity>,
): List<GarmentEmbeddingEntity> {
    val localComputedAt = localRows.associate { row -> (row.garmentId to row.kind) to row.computedAtEpochMillis }
    return filter { imported ->
        (localComputedAt[imported.garmentId to imported.kind] ?: Long.MIN_VALUE) <= imported.computedAtEpochMillis
    }
}

/** Accepts only the current model+recipe with a complete 768-dim float32 payload; anything else is re-computed. */
internal fun EmbeddingSyncRecord.toImportEntity(garmentId: String): GarmentEmbeddingEntity? {
    if (kind != EmbeddingKind.IMAGE && kind != EmbeddingKind.TEXT) return null
    if (modelId != EmbeddingRecipe.MODEL_ID || recipeId != EmbeddingRecipe.RECIPE_ID) return null
    if (dim != EmbeddingRecipe.DIMENSION) return null
    val bytes = runCatching { Base64.getDecoder().decode(b64) }.getOrNull() ?: return null
    if (bytes.size != EmbeddingRecipe.DIMENSION * Float.SIZE_BYTES) return null
    return GarmentEmbeddingEntity(
        garmentId = garmentId,
        kind = kind,
        modelId = modelId,
        recipeId = recipeId,
        dim = dim,
        vector = bytes,
        inputHash = inputHash,
        computedAtEpochMillis = computedAtEpochMillis,
        durationMillis = 0L,
        pendingUpload = false,
    )
}

private fun ClothingItemTagCrossRef.toSyncRecord(revision: Long): GarmentTagMappingRecord = GarmentTagMappingRecord(
    garmentId = clothingItemId,
    tagId = tagId,
    revision = revision,
    updatedAtEpochMillis = revision,
)

private fun ClothingItemEntity.toColorRecords(): List<GarmentColorMappingRecord> = listOf(
    GarmentColorMappingRecord(
        garmentId = id,
        role = GarmentColorRole.Primary,
        rawValue = colorMetrics.primaryRawValue,
        displayLabel = colorMetrics.primaryDisplayLabel,
        paletteColorId = colorMetrics.primaryPaletteColorId,
        paletteColorName = colorMetrics.primaryPaletteColorName,
        paletteColorHex = colorMetrics.primaryPaletteColorHex,
        revision = syncRevision,
        updatedAtEpochMillis = updatedAtEpochMillis,
    ),
    GarmentColorMappingRecord(
        garmentId = id,
        role = GarmentColorRole.Secondary,
        rawValue = colorMetrics.secondaryRawValue,
        displayLabel = colorMetrics.secondaryDisplayLabel,
        paletteColorId = colorMetrics.secondaryPaletteColorId,
        paletteColorName = colorMetrics.secondaryPaletteColorName,
        paletteColorHex = colorMetrics.secondaryPaletteColorHex,
        revision = syncRevision,
        updatedAtEpochMillis = updatedAtEpochMillis,
    ),
).filter { record -> record.rawValue != null || record.displayLabel != null || record.paletteColorId != null }

/** Internal so the no-op check tests build the local side exactly as export does. */
internal fun ClothingItemEntity.toPhotoRecord(): GarmentPhotoRecord? = photoUri?.let { uri ->
    GarmentPhotoRecord(
        garmentId = id,
        localUri = uri,
        blobPath = DriveFolderNaming.photoBlobPath(id, uri),
        revision = syncRevision,
        updatedAtEpochMillis = updatedAtEpochMillis,
        // The upload hashes and stores the original; export reads no file (REQ-OR-04).
        localOriginalUri = originalUri?.takeIf(String::isNotBlank),
        recipe = originalRecipe?.takeIf { originalUri?.isNotBlank() == true },
    )
} ?: if (photoRestoreGuarded) {
    // Guarded garment without a local photo: upload copies the remote record for it and never purges that blob.
    GarmentPhotoRecord(
        garmentId = id,
        localUri = "",
        blobPath = DriveFolderNaming.photoBlobPath(id),
        revision = syncRevision,
        updatedAtEpochMillis = updatedAtEpochMillis,
        restoreFailureCategory = "local_photo_missing",
    )
} else {
    null
}

private fun SyncTombstoneEntity.toSyncRecord(): SyncTombstoneRecord = SyncTombstoneRecord(
    entityType = entityType,
    entityId = entityId,
    deletedAtEpochMillis = deletedAtEpochMillis,
    revision = revision,
)

private fun TagCategorySyncRecord.toEntity(): TagCategoryEntity = TagCategoryEntity(
    id = id,
    name = name,
    sortOrder = sortOrder,
    isSystem = isSystem,
    syncStatus = GarmentSyncStatus.Synced,
    syncRevision = revision,
    syncDirtyAtEpochMillis = null,
    lastSyncedAtEpochMillis = System.currentTimeMillis(),
)

private fun TagSyncRecord.toEntity(): GarmentTagEntity = GarmentTagEntity(
    id = id,
    categoryId = categoryId,
    name = name,
    sortOrder = sortOrder,
    isSystem = isSystem,
    syncStatus = GarmentSyncStatus.Synced,
    syncRevision = revision,
    syncDirtyAtEpochMillis = null,
    lastSyncedAtEpochMillis = System.currentTimeMillis(),
    description = description,
    suggestOccasions = suggestOccasions,
)

private fun MainColorSyncRecord.toEntity(): MainColorEntity = MainColorEntity(
    id = id,
    name = name,
    hex = hex,
    sortOrder = sortOrder,
    isDefault = isDefault,
    syncStatus = GarmentSyncStatus.Synced,
    syncRevision = revision,
    syncDirtyAtEpochMillis = null,
    lastSyncedAtEpochMillis = System.currentTimeMillis(),
)

/**
 * The rows an import writes for the snapshot's garments. A sync record carries no original photo, so a garment that
 * exists locally keeps its local original fields; a garment new to the phone gets none. The rows are written with an
 * upsert of every column, so a null here would erase the saved original.
 */
internal fun restoredClothingItems(
    snapshot: WardrobeSyncSnapshot,
    phoneHeldPhotoUris: Map<String, String>,
    localItemsById: Map<String, ClothingItemEntity>,
): List<ClothingItemEntity> {
    val remotePhotoByGarmentId = snapshot.photos.associateBy(GarmentPhotoRecord::garmentId)
    val guardedPhotoByGarmentId = snapshot.photos
        .mapNotNull { photo -> photo.toImportPhotoRestoreIssue()?.let { issue -> photo.garmentId to issue } }
        .toMap()
    val remoteColorsByGarmentId = snapshot.garmentColors.groupBy(GarmentColorMappingRecord::garmentId)
    return snapshot.garments.map { garment ->
        val localItem = localItemsById[garment.id]
        val remotePhoto = remotePhotoByGarmentId[garment.id]
        // A restored original (REQ-OR-04) replaces the local one only together with its recipe; otherwise the phone keeps its own.
        val restoredOriginalUri = remotePhoto?.restoredOriginalLocalUri?.takeIf(String::isNotBlank)
        garment.toEntity(
            photoUri = importedPhotoUri(remotePhoto, phoneHeldPhotoUris[garment.id]),
            photoRestoreIssue = guardedPhotoByGarmentId[garment.id],
            colorRecords = remoteColorsByGarmentId[garment.id].orEmpty(),
            originalUri = restoredOriginalUri ?: localItem?.originalUri,
            originalRecipe = if (restoredOriginalUri != null) remotePhoto?.recipe else localItem?.originalRecipe,
        )
    }
}

private fun GarmentSyncRecord.toEntity(
    photoUri: String?,
    photoRestoreIssue: ImportPhotoRestoreIssue?,
    colorRecords: List<GarmentColorMappingRecord>,
    originalUri: String?,
    originalRecipe: String?,
): ClothingItemEntity {
    val colorsByRole = colorRecords.associateBy(GarmentColorMappingRecord::role)
    return ClothingItemEntity(
        id = id,
        name = name,
        notes = notes,
        photoUri = photoUri,
        fitValue = fitValue,
        colorMetrics = ColorMetricsEntity(
            primaryRawValue = colorsByRole[GarmentColorRole.Primary]?.rawValue,
            primaryDisplayLabel = colorsByRole[GarmentColorRole.Primary]?.displayLabel,
            primaryPaletteColorId = colorsByRole[GarmentColorRole.Primary]?.paletteColorId,
            primaryPaletteColorName = colorsByRole[GarmentColorRole.Primary]?.paletteColorName,
            primaryPaletteColorHex = colorsByRole[GarmentColorRole.Primary]?.paletteColorHex,
            secondaryRawValue = colorsByRole[GarmentColorRole.Secondary]?.rawValue,
            secondaryDisplayLabel = colorsByRole[GarmentColorRole.Secondary]?.displayLabel,
            secondaryPaletteColorId = colorsByRole[GarmentColorRole.Secondary]?.paletteColorId,
            secondaryPaletteColorName = colorsByRole[GarmentColorRole.Secondary]?.paletteColorName,
            secondaryPaletteColorHex = colorsByRole[GarmentColorRole.Secondary]?.paletteColorHex,
        ),
        isFavorite = isFavorite,
        isArchived = isArchived,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        // A guarded remote photo requires the user to explicitly retry it and must not be omitted
        // from a later local snapshot upload while that recovery remains unresolved.
        syncStatus = if (photoRestoreIssue == null) GarmentSyncStatus.Synced else GarmentSyncStatus.NeedsUserAction,
        syncRevision = revision,
        syncDirtyAtEpochMillis = null,
        lastSyncedAtEpochMillis = System.currentTimeMillis(),
        syncFailureMessage = photoRestoreIssue?.userVisibleMessage,
        photoRestoreGuarded = photoRestoreIssue != null,
        retryAfterEpochMillis = photoRestoreIssue?.let { System.currentTimeMillis() },
        photoRestoreRetryDeadlineEpochMillis = photoRestoreIssue?.let { System.currentTimeMillis() + PHOTO_RESTORE_RETRY_WINDOW_MILLIS },
        originalUri = originalUri,
        originalRecipe = originalRecipe,
    )
}

private const val PHOTO_RESTORE_RETRY_WINDOW_MILLIS = 24 * 60 * 60 * 1000L

private fun SyncTombstoneRecord.toEntity(): SyncTombstoneEntity = SyncTombstoneEntity(
    id = "$entityType:$entityId",
    entityType = entityType,
    entityId = entityId,
    deletedAtEpochMillis = deletedAtEpochMillis,
    revision = revision,
    syncStatus = GarmentSyncStatus.Synced,
    syncDirtyAtEpochMillis = null,
    lastSyncedAtEpochMillis = System.currentTimeMillis(),
)

private fun WardrobeSyncSnapshot.withoutTombstonedTaxonomy(): WardrobeSyncSnapshot {
    val tombstoneByCategoryId = tombstones
        .filter { tombstone -> tombstone.entityType in categoryEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)
    val tombstoneByTagId = tombstones
        .filter { tombstone -> tombstone.entityType in tagEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)
    val tombstoneByMainColorId = tombstones
        .filter { tombstone -> tombstone.entityType in mainColorEntityTypes }
        .associateBy(SyncTombstoneRecord::entityId)

    val activeCategories = taxonomies.categories
        .filterNot { category -> (tombstoneByCategoryId[category.id]?.revision ?: Long.MIN_VALUE) >= category.revision }
    val activeCategoryIds = activeCategories.map(TagCategorySyncRecord::id).toSet()
    val activeTags = taxonomies.tags
        .filterNot { tag -> (tombstoneByTagId[tag.id]?.revision ?: Long.MIN_VALUE) >= tag.revision }
        .filter { tag -> tag.categoryId in activeCategoryIds }
    val activeTagIds = activeTags.map(TagSyncRecord::id).toSet()
    val activeMainColors = taxonomies.mainColors
        .filterNot { color -> (tombstoneByMainColorId[color.id]?.revision ?: Long.MIN_VALUE) >= color.revision }
    val activeMainColorIds = activeMainColors.map(MainColorSyncRecord::id).toSet()

    return copy(
        taxonomies = WardrobeTaxonomySnapshot(
            categories = activeCategories,
            tags = activeTags,
            mainColors = activeMainColors,
        ),
        garmentTags = garmentTags.filter { record -> record.tagId in activeTagIds },
        garmentColors = garmentColors.filter { record ->
            record.paletteColorId?.let(activeMainColorIds::contains) != false
        },
    ).sortedDeterministically()
}

private val categoryEntityTypes = setOf("tag_category", "category")
private val tagEntityTypes = setOf("garment_tag", "tag")
private val mainColorEntityTypes = setOf("main_color", "palette_color", "color")

private fun GarmentPhotoRecord.restorableLocalUri(): String? = restoredLocalUri?.takeIf(String::isNotBlank)

/**
 * The photo_uri a garment takes on import. A file restored from Drive wins. Without one, the phone keeps the file it already
 * holds: a silent import must never replace a local photo reference with null, which would orphan the file.
 */
internal fun importedPhotoUri(merged: GarmentPhotoRecord?, phoneHeldUri: String?): String? =
    merged?.restorableLocalUri() ?: phoneHeldUri?.takeIf(String::isNotBlank)

private fun RestoredPhotoApplyStateEntity?.zeroUpdateReason(
    expectedRevision: Long,
    persistedAfterUpdate: Boolean,
): String = when {
    this == null -> "garment_not_found"
    revision != expectedRevision -> "revision_mismatch"
    !photoRestoreGuarded -> "photo_not_guarded"
    syncStatus != GarmentSyncStatus.Running -> "status_not_running"
    !persistedAfterUpdate -> "post_update_verification_failed"
    else -> "dao_update_zero_rows"
}

internal data class ImportPhotoRestoreIssue(
    val category: String,
    val userVisibleMessage: String,
)

internal fun GarmentPhotoRecord.toImportPhotoRestoreIssue(): ImportPhotoRestoreIssue? {
    if (restorableLocalUri() != null) return null
    val category = restoreFailureCategory?.takeIf(String::isNotBlank) ?: return null
    return ImportPhotoRestoreIssue(
        category = category,
        userVisibleMessage = MISSING_RESTORED_PHOTO_MESSAGE,
    )
}

internal const val MISSING_RESTORED_PHOTO_MESSAGE =
    "Some images could not be restored from Drive. Robia will retry during the next cloud sync."
