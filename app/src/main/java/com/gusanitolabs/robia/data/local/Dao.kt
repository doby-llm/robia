package com.gusanitolabs.robia.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WardrobeDao {
    @Transaction
    @Query("SELECT * FROM clothing_items WHERE is_archived = 0 ORDER BY is_favorite DESC, updated_at_epoch_millis DESC")
    fun observeActiveItems(): Flow<List<ClothingItemWithTags>>

    @Transaction
    @Query("SELECT * FROM clothing_items WHERE id = :id")
    fun observeItem(id: String): Flow<ClothingItemWithTags?>

    @Transaction
    @Query("SELECT * FROM clothing_items ORDER BY id")
    suspend fun getAllItemsForSync(): List<ClothingItemWithTags>

    @Query("SELECT * FROM clothing_item_tags ORDER BY clothing_item_id, tag_id")
    suspend fun getItemTagRefsForSync(): List<ClothingItemTagCrossRef>

    @Query("SELECT COUNT(*) FROM clothing_items WHERE sync_status IN ('Dirty', 'Queued') OR (sync_status = 'FailedRetryable' AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now))")
    fun observePendingGarmentSyncCount(now: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT COUNT(*) FROM clothing_items WHERE sync_status IN ('FailedRetryable', 'NeedsUserAction', 'AuthBlocked')")
    fun observeGarmentSyncAttentionCount(): Flow<Int>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM tag_categories WHERE sync_status IN ('Dirty', 'Queued') OR (sync_status = 'FailedRetryable' AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now))) +
            (SELECT COUNT(*) FROM garment_tags WHERE sync_status IN ('Dirty', 'Queued') OR (sync_status = 'FailedRetryable' AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now))) +
            (SELECT COUNT(*) FROM main_colors WHERE sync_status IN ('Dirty', 'Queued') OR (sync_status = 'FailedRetryable' AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now))) +
            (SELECT COUNT(*) FROM sync_tombstones WHERE sync_status IN ('Dirty', 'Queued') OR (sync_status = 'FailedRetryable' AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now)))
        """,
    )
    fun observePendingMetadataSyncCount(now: Long = System.currentTimeMillis()): Flow<Int>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM tag_categories WHERE sync_status IN ('FailedRetryable', 'NeedsUserAction', 'AuthBlocked')) +
            (SELECT COUNT(*) FROM garment_tags WHERE sync_status IN ('FailedRetryable', 'NeedsUserAction', 'AuthBlocked')) +
            (SELECT COUNT(*) FROM main_colors WHERE sync_status IN ('FailedRetryable', 'NeedsUserAction', 'AuthBlocked')) +
            (SELECT COUNT(*) FROM sync_tombstones WHERE sync_status IN ('FailedRetryable', 'NeedsUserAction', 'AuthBlocked'))
        """,
    )
    fun observeMetadataSyncAttentionCount(): Flow<Int>

    @Query("SELECT id, sync_revision AS revision FROM clothing_items WHERE sync_status IN ('Dirty', 'Queued') OR (sync_status = 'FailedRetryable' AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now)) ORDER BY sync_dirty_at_epoch_millis, updated_at_epoch_millis, id")
    suspend fun pendingGarmentSyncWork(now: Long = System.currentTimeMillis()): List<PendingGarmentSyncWorkEntity>

    @Query(
        """
        SELECT 'tag_category' AS entityType, id, sync_revision AS revision FROM tag_categories WHERE sync_status IN ('Dirty', 'Queued') OR (sync_status = 'FailedRetryable' AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now))
        UNION ALL SELECT 'garment_tag' AS entityType, id, sync_revision AS revision FROM garment_tags WHERE sync_status IN ('Dirty', 'Queued') OR (sync_status = 'FailedRetryable' AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now))
        UNION ALL SELECT 'main_color' AS entityType, id, sync_revision AS revision FROM main_colors WHERE sync_status IN ('Dirty', 'Queued') OR (sync_status = 'FailedRetryable' AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now))
        UNION ALL SELECT entity_type AS entityType, id, revision FROM sync_tombstones WHERE sync_status IN ('Dirty', 'Queued') OR (sync_status = 'FailedRetryable' AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now))
        ORDER BY revision, entityType, id
        """,
    )
    suspend fun pendingMetadataSyncWork(now: Long = System.currentTimeMillis()): List<PendingMetadataSyncWorkEntity>

    @Query("UPDATE clothing_items SET sync_status = 'Running', sync_started_at_epoch_millis = :startedAtEpochMillis WHERE id = :itemId AND sync_revision = :revision")
    suspend fun markGarmentSyncing(itemId: String, revision: Long, startedAtEpochMillis: Long): Int

    @Transaction
    suspend fun recoverStaleRunningSyncWork(staleBeforeEpochMillis: Long): Int =
        recoverStaleGarmentSyncWork(staleBeforeEpochMillis) +
            recoverStaleTagCategorySyncWork(staleBeforeEpochMillis) +
            recoverStaleGarmentTagSyncWork(staleBeforeEpochMillis) +
            recoverStaleMainColorSyncWork(staleBeforeEpochMillis) +
            recoverStaleTombstoneSyncWork(staleBeforeEpochMillis)

    @Query("UPDATE clothing_items SET sync_status = 'FailedRetryable', retry_after_epoch_millis = 0, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleGarmentSyncWork(staleBeforeEpochMillis: Long): Int

    @Query("UPDATE tag_categories SET sync_status = 'FailedRetryable', retry_after_epoch_millis = 0, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleTagCategorySyncWork(staleBeforeEpochMillis: Long): Int

    @Query("UPDATE garment_tags SET sync_status = 'FailedRetryable', retry_after_epoch_millis = 0, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleGarmentTagSyncWork(staleBeforeEpochMillis: Long): Int

    @Query("UPDATE main_colors SET sync_status = 'FailedRetryable', retry_after_epoch_millis = 0, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleMainColorSyncWork(staleBeforeEpochMillis: Long): Int

    @Query("UPDATE sync_tombstones SET sync_status = 'FailedRetryable', retry_after_epoch_millis = 0, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleTombstoneSyncWork(staleBeforeEpochMillis: Long): Int

    @Query("UPDATE clothing_items SET sync_status = 'Synced', last_synced_at_epoch_millis = :syncedAtEpochMillis, sync_dirty_at_epoch_millis = NULL, sync_failure_message = NULL WHERE id = :itemId AND sync_revision = :revision")
    suspend fun markGarmentSynced(itemId: String, revision: Long, syncedAtEpochMillis: Long): Int

    @Query("UPDATE clothing_items SET sync_status = CASE WHEN retry_attempt_count + 1 >= 3 THEN 'NeedsUserAction' ELSE 'FailedRetryable' END, retry_attempt_count = MIN(retry_attempt_count + 1, 3), retry_after_epoch_millis = CASE WHEN retry_attempt_count + 1 >= 3 THEN NULL ELSE :now + CASE retry_attempt_count WHEN 0 THEN 60000 WHEN 1 THEN 300000 ELSE 900000 END END, sync_started_at_epoch_millis = NULL, sync_failure_message = :message WHERE id = :itemId AND sync_revision = :revision")
    suspend fun markGarmentSyncFailedRetryable(itemId: String, revision: Long, message: String?, now: Long): Int

    @Query("UPDATE clothing_items SET sync_status = 'AuthBlocked', sync_failure_message = :message WHERE id = :itemId")
    suspend fun markGarmentSyncAuthBlocked(itemId: String, message: String?): Int

    @Transaction
    suspend fun markMetadataSyncing(work: PendingMetadataSyncWorkEntity): Boolean =
        updateMetadataSyncStatus(work, GarmentSyncStatus.Running) > 0

    @Transaction
    suspend fun markMetadataSynced(work: PendingMetadataSyncWorkEntity, syncedAtEpochMillis: Long): Boolean =
        updateMetadataSyncStatus(
            work = work,
            status = GarmentSyncStatus.Synced,
            syncedAtEpochMillis = syncedAtEpochMillis,
            clearDirty = true,
        ) > 0

    @Transaction
    suspend fun markMetadataSyncFailedRetryable(work: PendingMetadataSyncWorkEntity, message: String?): Boolean =
        updateMetadataSyncStatus(work, GarmentSyncStatus.FailedRetryable, message = message) > 0

    @Transaction
    suspend fun markMetadataSyncAuthBlocked(work: PendingMetadataSyncWorkEntity, message: String?): Boolean =
        updateMetadataSyncStatus(work, GarmentSyncStatus.AuthBlocked, message = message, matchRevision = false) > 0

    private suspend fun updateMetadataSyncStatus(
        work: PendingMetadataSyncWorkEntity,
        status: GarmentSyncStatus,
        syncedAtEpochMillis: Long? = null,
        clearDirty: Boolean = false,
        message: String? = null,
        matchRevision: Boolean = true,
    ): Int = when (work.entityType) {
        "tag_category", "category" -> updateTagCategorySyncStatus(work.id, work.revision, status, syncedAtEpochMillis, clearDirty, message, matchRevision)
        "garment_tag", "tag" -> updateGarmentTagSyncStatus(work.id, work.revision, status, syncedAtEpochMillis, clearDirty, message, matchRevision)
        "main_color", "palette_color", "color" -> updateMainColorSyncStatus(work.id, work.revision, status, syncedAtEpochMillis, clearDirty, message, matchRevision)
        else -> updateTombstoneSyncStatus(work.id, work.revision, status, syncedAtEpochMillis, clearDirty, message, matchRevision)
    }

    @Query(
        """
        UPDATE tag_categories
        SET sync_status = :status,
            last_synced_at_epoch_millis = COALESCE(:syncedAtEpochMillis, last_synced_at_epoch_millis),
            sync_dirty_at_epoch_millis = CASE WHEN :clearDirty THEN NULL ELSE sync_dirty_at_epoch_millis END,
            sync_failure_message = :message
        WHERE id = :id AND (:matchRevision = 0 OR sync_revision = :revision)
        """,
    )
    suspend fun updateTagCategorySyncStatus(id: String, revision: Long, status: GarmentSyncStatus, syncedAtEpochMillis: Long?, clearDirty: Boolean, message: String?, matchRevision: Boolean): Int

    @Query(
        """
        UPDATE garment_tags
        SET sync_status = :status,
            last_synced_at_epoch_millis = COALESCE(:syncedAtEpochMillis, last_synced_at_epoch_millis),
            sync_dirty_at_epoch_millis = CASE WHEN :clearDirty THEN NULL ELSE sync_dirty_at_epoch_millis END,
            sync_failure_message = :message
        WHERE id = :id AND (:matchRevision = 0 OR sync_revision = :revision)
        """,
    )
    suspend fun updateGarmentTagSyncStatus(id: String, revision: Long, status: GarmentSyncStatus, syncedAtEpochMillis: Long?, clearDirty: Boolean, message: String?, matchRevision: Boolean): Int

    @Query(
        """
        UPDATE main_colors
        SET sync_status = :status,
            last_synced_at_epoch_millis = COALESCE(:syncedAtEpochMillis, last_synced_at_epoch_millis),
            sync_dirty_at_epoch_millis = CASE WHEN :clearDirty THEN NULL ELSE sync_dirty_at_epoch_millis END,
            sync_failure_message = :message
        WHERE id = :id AND (:matchRevision = 0 OR sync_revision = :revision)
        """,
    )
    suspend fun updateMainColorSyncStatus(id: String, revision: Long, status: GarmentSyncStatus, syncedAtEpochMillis: Long?, clearDirty: Boolean, message: String?, matchRevision: Boolean): Int

    @Query(
        """
        UPDATE sync_tombstones
        SET sync_status = :status,
            last_synced_at_epoch_millis = COALESCE(:syncedAtEpochMillis, last_synced_at_epoch_millis),
            sync_dirty_at_epoch_millis = CASE WHEN :clearDirty THEN NULL ELSE sync_dirty_at_epoch_millis END,
            sync_failure_message = :message
        WHERE id = :id AND (:matchRevision = 0 OR revision = :revision)
        """,
    )
    suspend fun updateTombstoneSyncStatus(id: String, revision: Long, status: GarmentSyncStatus, syncedAtEpochMillis: Long?, clearDirty: Boolean, message: String?, matchRevision: Boolean): Int

    @Upsert
    suspend fun upsertItem(item: ClothingItemEntity)

    @Query("DELETE FROM clothing_item_tags WHERE clothing_item_id = :itemId")
    suspend fun clearTags(itemId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<ClothingItemTagCrossRef>)

    @Upsert
    suspend fun upsertSyncTombstones(tombstones: List<SyncTombstoneEntity>)

    @Query("SELECT id FROM garment_tags WHERE id IN (:ids)")
    suspend fun existingTagIds(ids: List<String>): List<String>

    @Query("UPDATE clothing_items SET is_archived = 1, updated_at_epoch_millis = :updatedAtEpochMillis, sync_status = 'Queued', sync_revision = :updatedAtEpochMillis, sync_dirty_at_epoch_millis = :updatedAtEpochMillis, sync_failure_message = NULL WHERE id = :itemId")
    suspend fun archiveItem(itemId: String, updatedAtEpochMillis: Long)

    @Query("UPDATE clothing_items SET is_archived = 1, updated_at_epoch_millis = :updatedAtEpochMillis, sync_status = 'Queued', sync_revision = :updatedAtEpochMillis, sync_dirty_at_epoch_millis = :updatedAtEpochMillis, sync_failure_message = NULL WHERE id IN (:itemIds)")
    suspend fun archiveItems(itemIds: List<String>, updatedAtEpochMillis: Long)

    @Transaction
    suspend fun archiveItemWithTombstone(itemId: String, updatedAtEpochMillis: Long, tombstone: SyncTombstoneEntity) {
        archiveItem(itemId, updatedAtEpochMillis)
        upsertSyncTombstones(listOf(tombstone))
    }

    @Transaction
    suspend fun archiveItemsWithTombstones(
        itemIds: List<String>,
        updatedAtEpochMillis: Long,
        tombstones: List<SyncTombstoneEntity>,
    ) {
        archiveItems(itemIds, updatedAtEpochMillis)
        upsertSyncTombstones(tombstones)
    }

    @Transaction
    suspend fun upsertItemWithTags(item: ClothingItemEntity, tagIds: List<String>) {
        upsertItem(item.queuedForSync())
        clearTags(item.id)
        val activeTagIds: Set<String> = if (tagIds.isEmpty()) emptySet() else existingTagIds(tagIds).toSet()
        insertTagRefs(
            tagIds
                .filter(activeTagIds::contains)
                .map { tagId -> ClothingItemTagCrossRef(item.id, tagId) },
        )
    }

    @Transaction
    suspend fun upsertItemsWithTags(items: List<ClothingItemEntity>, tagIdsByItemId: Map<String, List<String>>) {
        items.forEach { item ->
            upsertItemWithTags(item, tagIdsByItemId[item.id].orEmpty())
        }
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag_categories ORDER BY sort_order, name")
    fun observeCategories(): Flow<List<TagCategoryEntity>>

    @Query("SELECT * FROM tag_categories ORDER BY sort_order, id")
    suspend fun getCategoriesForSync(): List<TagCategoryEntity>

    @Query("SELECT * FROM garment_tags ORDER BY category_id, sort_order, id")
    suspend fun getTagsForSync(): List<GarmentTagEntity>

    @Query("SELECT * FROM main_colors ORDER BY sort_order, id")
    suspend fun getMainColorsForSync(): List<MainColorEntity>

    @Query("SELECT * FROM garment_tags ORDER BY sort_order, name")
    fun observeTags(): Flow<List<GarmentTagEntity>>

    @Query("SELECT * FROM main_colors ORDER BY sort_order, name")
    fun observeMainColors(): Flow<List<MainColorEntity>>

    @Upsert
    suspend fun upsertCategory(category: TagCategoryEntity)

    @Upsert
    suspend fun upsertTag(tag: GarmentTagEntity)

    @Upsert
    suspend fun upsertTags(tags: List<GarmentTagEntity>)

    @Upsert
    suspend fun upsertMainColor(color: MainColorEntity)

    @Upsert
    suspend fun upsertMainColors(colors: List<MainColorEntity>)

    @Upsert
    suspend fun upsertClothingItems(items: List<ClothingItemEntity>)

    @Query("DELETE FROM clothing_item_tags WHERE clothing_item_id IN (:itemIds)")
    suspend fun clearTagsForItems(itemIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemTagRefs(refs: List<ClothingItemTagCrossRef>)

    @Upsert
    suspend fun upsertSyncTombstones(tombstones: List<SyncTombstoneEntity>)

    @Query("DELETE FROM clothing_item_tags WHERE tag_id = :id")
    suspend fun deleteItemRefsForTag(id: String)

    @Query(
        """
        DELETE FROM garment_tags
        WHERE id = :id
            AND (is_system = 0 OR category_id IN ('category', 'season', 'occasion', 'location'))
        """,
    )
    suspend fun deleteEditableTag(id: String): Int

    @Query("DELETE FROM main_colors WHERE id = :id AND (SELECT COUNT(*) FROM main_colors) > 1")
    suspend fun deleteMainColor(id: String): Int

    @Query("DELETE FROM main_colors WHERE id IN (:ids) AND (SELECT COUNT(*) FROM main_colors) > :deleteCount")
    suspend fun deleteMainColors(ids: List<String>, deleteCount: Int): Int

    @Query("SELECT COUNT(*) FROM main_colors")
    suspend fun mainColorCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedCategories(categories: List<TagCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedTags(tags: List<GarmentTagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedMainColors(colors: List<MainColorEntity>)

    @Query("DELETE FROM main_colors")
    suspend fun deleteAllMainColors()

    @Transaction
    suspend fun replaceMainColors(colors: List<MainColorEntity>) {
        deleteAllMainColors()
        seedMainColors(colors)
    }

    @Transaction
    suspend fun applyMainColorChange(
        upsertColors: List<MainColorEntity>,
        deleteColorIds: List<String>,
        updatedItems: List<ClothingItemEntity>,
        tagIdsByItemId: Map<String, List<String>>,
        tombstones: List<SyncTombstoneEntity>,
    ) {
        if (upsertColors.isNotEmpty()) upsertMainColors(upsertColors)
        if (deleteColorIds.isNotEmpty()) deleteMainColors(deleteColorIds, deleteColorIds.size)
        if (tombstones.isNotEmpty()) upsertSyncTombstones(tombstones)
        if (updatedItems.isNotEmpty()) {
            val itemIds = updatedItems.map(ClothingItemEntity::id)
            upsertClothingItems(updatedItems.map(ClothingItemEntity::queuedForSync))
            clearTagsForItems(itemIds)
            insertItemTagRefs(
                tagIdsByItemId.flatMap { (itemId, tagIds) ->
                    tagIds.map { tagId -> ClothingItemTagCrossRef(itemId, tagId) }
                },
            )
        }
    }

    @Transaction
    suspend fun deleteEditableTagAndReferences(id: String, tombstone: SyncTombstoneEntity): Int {
        deleteItemRefsForTag(id)
        val deletedCount = deleteEditableTag(id)
        if (deletedCount > 0) upsertSyncTombstones(listOf(tombstone))
        return deletedCount
    }
}

@Dao
interface SyncTombstoneDao {
    @Query("SELECT * FROM sync_tombstones ORDER BY entity_type, entity_id")
    suspend fun getAllForSync(): List<SyncTombstoneEntity>

    @Upsert
    suspend fun upsert(tombstone: SyncTombstoneEntity)
}

private fun ClothingItemEntity.queuedForSync(): ClothingItemEntity {
    val revision = updatedAtEpochMillis.coerceAtLeast(System.currentTimeMillis())
    return copy(
        syncStatus = GarmentSyncStatus.Queued,
        syncRevision = revision,
        syncDirtyAtEpochMillis = revision,
        syncFailureMessage = null,
    )
}
