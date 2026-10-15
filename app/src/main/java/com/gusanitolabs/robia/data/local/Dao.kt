package com.gusanitolabs.robia.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.gusanitolabs.robia.core.model.DedupeTag
import com.gusanitolabs.robia.core.model.GarmentSyncStatus
import com.gusanitolabs.robia.core.model.TagDedupePlanner
import com.gusanitolabs.robia.core.model.TagMerge
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

    @Query("SELECT COUNT(*) FROM clothing_items WHERE photo_restore_guarded = 1")
    fun observeGuardedPhotoRestoreCount(): Flow<Int>

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

    @Query("SELECT MIN(retry_after_epoch_millis) FROM (SELECT retry_after_epoch_millis FROM clothing_items WHERE sync_status = 'FailedRetryable' AND retry_after_epoch_millis IS NOT NULL UNION ALL SELECT retry_after_epoch_millis FROM tag_categories WHERE sync_status = 'FailedRetryable' AND retry_after_epoch_millis IS NOT NULL UNION ALL SELECT retry_after_epoch_millis FROM garment_tags WHERE sync_status = 'FailedRetryable' AND retry_after_epoch_millis IS NOT NULL UNION ALL SELECT retry_after_epoch_millis FROM main_colors WHERE sync_status = 'FailedRetryable' AND retry_after_epoch_millis IS NOT NULL UNION ALL SELECT retry_after_epoch_millis FROM sync_tombstones WHERE sync_status = 'FailedRetryable' AND retry_after_epoch_millis IS NOT NULL)")
    suspend fun nextRunnableSyncRetryEpochMillis(): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM sync_tombstones WHERE entity_type IN ('garment', 'clothing_item', 'item') AND sync_status != 'Synced')")
    suspend fun hasPendingCloudDeletion(): Boolean

    @Query("UPDATE clothing_items SET sync_status = 'Running', sync_started_at_epoch_millis = :startedAtEpochMillis WHERE id = :itemId AND sync_revision = :revision")
    suspend fun markGarmentSyncing(itemId: String, revision: Long, startedAtEpochMillis: Long): Int

    @Transaction
    suspend fun recoverStaleRunningSyncWork(staleBeforeEpochMillis: Long): Int =
        recoverStaleUnguardedGarmentSyncWork(staleBeforeEpochMillis) +
            recoverStaleTagCategorySyncWork(staleBeforeEpochMillis) +
            recoverStaleGarmentTagSyncWork(staleBeforeEpochMillis) +
            recoverStaleMainColorSyncWork(staleBeforeEpochMillis) +
            recoverStaleTombstoneSyncWork(staleBeforeEpochMillis)

    /** An interrupted claim is transient work: it goes back to Queued with no attempt counted and no attention needed. */
    @Query("UPDATE clothing_items SET sync_status = 'Queued', retry_after_epoch_millis = NULL, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleGarmentSyncWork(staleBeforeEpochMillis: Long): Int

    /**
     * The aggregate's garment query. Guarded rows are excluded: they belong to the photo-restore retry (which also runs
     * under the sync mutex) and have their own recovery, so an ordinary cycle must not requeue them as plain uploads.
     */
    @Query("UPDATE clothing_items SET sync_status = 'Queued', retry_after_epoch_millis = NULL, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND photo_restore_guarded = 0 AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleUnguardedGarmentSyncWork(staleBeforeEpochMillis: Long): Int

    /**
     * A guarded claim older than the photo retry timeout was left by a process that died mid-retry: the retry is bounded by that
     * timeout. It parks as NeedsUserAction, not Queued; the guard and the remote blob stay in place. Guarded NeedsUserAction rows are
     * picked up again by the bounded automatic photo retry on the next background run (retry_attempt_count < 3).
     * Called at processor init only.
     */
    @Query("UPDATE clothing_items SET sync_status = 'NeedsUserAction', sync_started_at_epoch_millis = NULL, retry_after_epoch_millis = NULL, sync_failure_message = 'photo_restore_interrupted' WHERE sync_status = 'Running' AND photo_restore_guarded = 1 AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleGuardedPhotoSyncWork(staleBeforeEpochMillis: Long): Int

    @Query("UPDATE tag_categories SET sync_status = 'Queued', retry_after_epoch_millis = NULL, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleTagCategorySyncWork(staleBeforeEpochMillis: Long): Int

    @Query("UPDATE garment_tags SET sync_status = 'Queued', retry_after_epoch_millis = NULL, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleGarmentTagSyncWork(staleBeforeEpochMillis: Long): Int

    @Query("UPDATE main_colors SET sync_status = 'Queued', retry_after_epoch_millis = NULL, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleMainColorSyncWork(staleBeforeEpochMillis: Long): Int

    @Query("UPDATE sync_tombstones SET sync_status = 'Queued', retry_after_epoch_millis = NULL, sync_started_at_epoch_millis = NULL WHERE sync_status = 'Running' AND COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis")
    suspend fun recoverStaleTombstoneSyncWork(staleBeforeEpochMillis: Long): Int

    @Query("UPDATE clothing_items SET sync_status = 'Synced', last_synced_at_epoch_millis = :syncedAtEpochMillis, sync_dirty_at_epoch_millis = NULL, sync_failure_message = NULL WHERE id = :itemId AND sync_revision = :revision")
    suspend fun markGarmentSynced(itemId: String, revision: Long, syncedAtEpochMillis: Long): Int

    @Query("UPDATE clothing_items SET sync_status = CASE WHEN retry_attempt_count + 1 >= 3 THEN 'NeedsUserAction' ELSE 'FailedRetryable' END, retry_attempt_count = MIN(retry_attempt_count + 1, 3), retry_after_epoch_millis = CASE WHEN retry_attempt_count + 1 >= 3 THEN NULL ELSE :now + CASE retry_attempt_count WHEN 0 THEN 60000 WHEN 1 THEN 300000 ELSE 900000 END END, sync_started_at_epoch_millis = NULL, sync_failure_message = :message WHERE id = :itemId AND sync_revision = :revision")
    suspend fun markGarmentSyncFailedRetryable(itemId: String, revision: Long, message: String?, now: Long): Int

    /** Transient network/Drive trouble keeps the row pending: no attempt counter, no backoff, no attention. */
    @Query("UPDATE clothing_items SET sync_status = 'Queued', sync_started_at_epoch_millis = NULL, retry_after_epoch_millis = NULL, sync_failure_message = NULL WHERE id = :itemId AND sync_revision = :revision")
    suspend fun releaseGarmentToQueued(itemId: String, revision: Long): Int

    @Query("UPDATE clothing_items SET sync_status = 'NeedsUserAction', sync_started_at_epoch_millis = NULL, retry_after_epoch_millis = NULL, sync_failure_message = :reasonCode WHERE id = :itemId AND sync_revision = :revision")
    suspend fun markGarmentNeedsUserAction(itemId: String, revision: Long, reasonCode: String): Int

    @Query("UPDATE tag_categories SET sync_status = 'Queued', sync_started_at_epoch_millis = NULL, retry_after_epoch_millis = NULL, sync_failure_message = NULL WHERE id = :id AND sync_revision = :revision")
    suspend fun releaseTagCategoryToQueued(id: String, revision: Long): Int

    @Query("UPDATE garment_tags SET sync_status = 'Queued', sync_started_at_epoch_millis = NULL, retry_after_epoch_millis = NULL, sync_failure_message = NULL WHERE id = :id AND sync_revision = :revision")
    suspend fun releaseGarmentTagToQueued(id: String, revision: Long): Int

    @Query("UPDATE main_colors SET sync_status = 'Queued', sync_started_at_epoch_millis = NULL, retry_after_epoch_millis = NULL, sync_failure_message = NULL WHERE id = :id AND sync_revision = :revision")
    suspend fun releaseMainColorToQueued(id: String, revision: Long): Int

    @Query("UPDATE sync_tombstones SET sync_status = 'Queued', sync_started_at_epoch_millis = NULL, retry_after_epoch_millis = NULL, sync_failure_message = NULL WHERE id = :id AND revision = :revision")
    suspend fun releaseTombstoneToQueued(id: String, revision: Long): Int

    @Transaction
    suspend fun releaseMetadataToQueued(work: PendingMetadataSyncWorkEntity): Boolean {
        val updated = when (work.metadataSyncTable()) {
            MetadataSyncTable.TagCategory -> releaseTagCategoryToQueued(work.id, work.revision)
            MetadataSyncTable.GarmentTag -> releaseGarmentTagToQueued(work.id, work.revision)
            MetadataSyncTable.MainColor -> releaseMainColorToQueued(work.id, work.revision)
            MetadataSyncTable.Tombstone -> releaseTombstoneToQueued(work.id, work.revision)
        }
        return updated > 0
    }

    /** Used only by the forced snapshot replace ("Use backup"): garments, mappings, photo refs and embeddings. */
    @Query("DELETE FROM clothing_item_tags")
    suspend fun deleteAllItemTagRefs(): Int

    @Query("DELETE FROM clothing_items")
    suspend fun deleteAllItems(): Int

    @Query("UPDATE clothing_items SET sync_status = 'AuthBlocked', sync_failure_message = :message WHERE id = :itemId")
    suspend fun markGarmentSyncAuthBlocked(itemId: String, message: String?): Int

    @Query("SELECT sync_revision FROM clothing_items WHERE id = :itemId AND photo_restore_guarded = 1 AND sync_status IN ('NeedsUserAction', 'Queued') AND retry_attempt_count < 3 AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now) AND photo_restore_retry_deadline_epoch_millis >= :now")
    suspend fun eligibleGarmentPhotoRestoreRetryRevision(itemId: String, now: Long): Long?

    @Query("UPDATE clothing_items SET sync_status = 'Running', sync_started_at_epoch_millis = :startedAtEpochMillis WHERE id = :itemId AND sync_revision = :revision AND photo_restore_guarded = 1 AND sync_status IN ('NeedsUserAction', 'Queued') AND retry_attempt_count < 3 AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now) AND photo_restore_retry_deadline_epoch_millis >= :now")
    suspend fun markGarmentPhotoRestoreRetrying(itemId: String, revision: Long, startedAtEpochMillis: Long, now: Long): Int

    @Query("UPDATE clothing_items SET sync_status = 'NeedsUserAction', retry_attempt_count = MIN(retry_attempt_count + 1, 3), retry_after_epoch_millis = CASE WHEN retry_attempt_count + 1 >= 3 THEN NULL ELSE :now + CASE retry_attempt_count WHEN 0 THEN 60000 WHEN 1 THEN 300000 ELSE 900000 END END, sync_started_at_epoch_millis = NULL, sync_failure_message = :message WHERE id = :itemId AND photo_restore_guarded = 1 AND sync_status = 'Running'")
    suspend fun markGarmentPhotoRestoreFailed(itemId: String, message: String, now: Long): Int

    /** Transient photo download failure: the guard stays, the attempt counter does not move, and the next run retries. */
    @Query("UPDATE clothing_items SET sync_status = 'NeedsUserAction', retry_after_epoch_millis = NULL, sync_started_at_epoch_millis = NULL, sync_failure_message = :message WHERE id = :itemId AND photo_restore_guarded = 1 AND sync_status = 'Running'")
    suspend fun markGarmentPhotoRestoreTransient(itemId: String, message: String): Int

    /** Guarded photos the next run may retry on its own; permanent failures stop at the attempt cap. */
    @Query("SELECT id FROM clothing_items WHERE photo_restore_guarded = 1 AND sync_status = 'NeedsUserAction' AND retry_attempt_count < 3 AND (retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now) AND photo_restore_retry_deadline_epoch_millis >= :now")
    suspend fun guardedPhotoRetryCandidateIds(now: Long): List<String>

    @Query("SELECT COUNT(*) FROM clothing_items WHERE sync_status = 'NeedsUserAction' AND sync_failure_message = :reasonCode")
    fun observeGarmentNeedsUserActionCount(reasonCode: String): Flow<Int>

    /** Explicit Retry only (TS§4.5): releases parked drive_full / photo_upload_failed rows. Guarded photos keep their own retry. */
    @Query("UPDATE clothing_items SET sync_status = 'Queued', sync_started_at_epoch_millis = NULL, retry_after_epoch_millis = NULL, sync_failure_message = NULL WHERE sync_status = 'NeedsUserAction' AND sync_failure_message = :reasonCode AND photo_restore_guarded = 0")
    suspend fun releaseNeedsUserActionToQueued(reasonCode: String): Int

    /** "Keep without photo": clears the guard of one guarded garment that has no local photo. Never touches remote blobs. */
    @Query("UPDATE clothing_items SET sync_status = 'Queued', sync_dirty_at_epoch_millis = COALESCE(sync_dirty_at_epoch_millis, :now), photo_restore_guarded = 0, photo_restore_retry_deadline_epoch_millis = NULL, retry_attempt_count = 0, retry_after_epoch_millis = NULL, sync_started_at_epoch_millis = NULL, sync_failure_message = NULL WHERE id = :itemId AND photo_restore_guarded = 1 AND sync_status = 'NeedsUserAction' AND photo_uri IS NULL")
    suspend fun keepGarmentWithoutRestoredPhoto(itemId: String, now: Long): Int

    @Query("UPDATE clothing_items SET photo_uri = :photoUri, sync_status = CASE WHEN sync_dirty_at_epoch_millis IS NULL THEN 'Synced' ELSE 'Queued' END, sync_failure_message = NULL, retry_attempt_count = 0, retry_after_epoch_millis = NULL, sync_started_at_epoch_millis = NULL, photo_restore_guarded = 0, photo_restore_retry_deadline_epoch_millis = NULL, last_synced_at_epoch_millis = :syncedAtEpochMillis WHERE id = :itemId AND sync_revision = :revision AND photo_restore_guarded = 1 AND sync_status = 'Running'")
    suspend fun applyRestoredPhoto(itemId: String, photoUri: String, revision: Long, syncedAtEpochMillis: Long): Int

    @Query("SELECT id AS itemId, sync_revision AS revision, sync_status AS syncStatus, photo_restore_guarded AS photoRestoreGuarded, retry_attempt_count AS retryAttemptCount, retry_after_epoch_millis AS retryAfterEpochMillis, photo_restore_retry_deadline_epoch_millis AS retryDeadlineEpochMillis, sync_dirty_at_epoch_millis AS syncDirtyAtEpochMillis, photo_uri AS photoUri FROM clothing_items WHERE id = :itemId")
    suspend fun restoredPhotoApplyState(itemId: String): RestoredPhotoApplyStateEntity?

    @Transaction
    suspend fun markMetadataSyncing(work: PendingMetadataSyncWorkEntity): Boolean =
        claimMetadataSync(work, System.currentTimeMillis()) > 0

    private suspend fun claimMetadataSync(work: PendingMetadataSyncWorkEntity, startedAtEpochMillis: Long): Int = when (work.metadataSyncTable()) {
        MetadataSyncTable.TagCategory -> claimTagCategoryMetadataSync(work.id, work.revision, startedAtEpochMillis)
        MetadataSyncTable.GarmentTag -> claimGarmentTagMetadataSync(work.id, work.revision, startedAtEpochMillis)
        MetadataSyncTable.MainColor -> claimMainColorMetadataSync(work.id, work.revision, startedAtEpochMillis)
        MetadataSyncTable.Tombstone -> claimTombstoneMetadataSync(work.id, work.revision, startedAtEpochMillis)
    }

    @Transaction
    suspend fun markMetadataSynced(work: PendingMetadataSyncWorkEntity, syncedAtEpochMillis: Long): Boolean =
        updateMetadataSyncStatus(
            work = work,
            status = GarmentSyncStatus.Synced,
            syncedAtEpochMillis = syncedAtEpochMillis,
            clearDirty = true,
        ) > 0

    @Transaction
    suspend fun markMetadataSyncFailedRetryable(work: PendingMetadataSyncWorkEntity, message: String?): Boolean {
        val now = System.currentTimeMillis()
        return updateMetadataSyncFailedRetryable(work, message, now) > 0
    }

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
    ): Int = when (work.metadataSyncTable()) {
        MetadataSyncTable.TagCategory -> updateTagCategorySyncStatus(work.id, work.revision, status, syncedAtEpochMillis, clearDirty, message, matchRevision)
        MetadataSyncTable.GarmentTag -> updateGarmentTagSyncStatus(work.id, work.revision, status, syncedAtEpochMillis, clearDirty, message, matchRevision)
        MetadataSyncTable.MainColor -> updateMainColorSyncStatus(work.id, work.revision, status, syncedAtEpochMillis, clearDirty, message, matchRevision)
        MetadataSyncTable.Tombstone -> updateTombstoneSyncStatus(work.id, work.revision, status, syncedAtEpochMillis, clearDirty, message, matchRevision)
    }

    private suspend fun updateMetadataSyncFailedRetryable(
        work: PendingMetadataSyncWorkEntity,
        message: String?,
        now: Long,
    ): Int = when (work.metadataSyncTable()) {
        MetadataSyncTable.TagCategory -> updateTagCategorySyncFailedRetryable(work.id, work.revision, message, now)
        MetadataSyncTable.GarmentTag -> updateGarmentTagSyncFailedRetryable(work.id, work.revision, message, now)
        MetadataSyncTable.MainColor -> updateMainColorSyncFailedRetryable(work.id, work.revision, message, now)
        MetadataSyncTable.Tombstone -> updateTombstoneSyncFailedRetryable(work.id, work.revision, message, now)
    }

    @Query("UPDATE tag_categories SET sync_status = 'Running', sync_started_at_epoch_millis = :startedAtEpochMillis WHERE id = :id AND sync_revision = :revision")
    suspend fun claimTagCategoryMetadataSync(id: String, revision: Long, startedAtEpochMillis: Long): Int

    @Query("UPDATE garment_tags SET sync_status = 'Running', sync_started_at_epoch_millis = :startedAtEpochMillis WHERE id = :id AND sync_revision = :revision")
    suspend fun claimGarmentTagMetadataSync(id: String, revision: Long, startedAtEpochMillis: Long): Int

    @Query("UPDATE main_colors SET sync_status = 'Running', sync_started_at_epoch_millis = :startedAtEpochMillis WHERE id = :id AND sync_revision = :revision")
    suspend fun claimMainColorMetadataSync(id: String, revision: Long, startedAtEpochMillis: Long): Int

    @Query("UPDATE sync_tombstones SET sync_status = 'Running', sync_started_at_epoch_millis = :startedAtEpochMillis WHERE id = :id AND revision = :revision")
    suspend fun claimTombstoneMetadataSync(id: String, revision: Long, startedAtEpochMillis: Long): Int

    @Query("UPDATE tag_categories SET sync_status = CASE WHEN retry_attempt_count + 1 >= 3 THEN 'NeedsUserAction' ELSE 'FailedRetryable' END, retry_attempt_count = MIN(retry_attempt_count + 1, 3), retry_after_epoch_millis = CASE WHEN retry_attempt_count + 1 >= 3 THEN NULL ELSE :now + CASE retry_attempt_count WHEN 0 THEN 60000 WHEN 1 THEN 300000 ELSE 900000 END END, sync_started_at_epoch_millis = NULL, sync_failure_message = :message WHERE id = :id AND sync_revision = :revision")
    suspend fun updateTagCategorySyncFailedRetryable(id: String, revision: Long, message: String?, now: Long): Int

    @Query("UPDATE garment_tags SET sync_status = CASE WHEN retry_attempt_count + 1 >= 3 THEN 'NeedsUserAction' ELSE 'FailedRetryable' END, retry_attempt_count = MIN(retry_attempt_count + 1, 3), retry_after_epoch_millis = CASE WHEN retry_attempt_count + 1 >= 3 THEN NULL ELSE :now + CASE retry_attempt_count WHEN 0 THEN 60000 WHEN 1 THEN 300000 ELSE 900000 END END, sync_started_at_epoch_millis = NULL, sync_failure_message = :message WHERE id = :id AND sync_revision = :revision")
    suspend fun updateGarmentTagSyncFailedRetryable(id: String, revision: Long, message: String?, now: Long): Int

    @Query("UPDATE main_colors SET sync_status = CASE WHEN retry_attempt_count + 1 >= 3 THEN 'NeedsUserAction' ELSE 'FailedRetryable' END, retry_attempt_count = MIN(retry_attempt_count + 1, 3), retry_after_epoch_millis = CASE WHEN retry_attempt_count + 1 >= 3 THEN NULL ELSE :now + CASE retry_attempt_count WHEN 0 THEN 60000 WHEN 1 THEN 300000 ELSE 900000 END END, sync_started_at_epoch_millis = NULL, sync_failure_message = :message WHERE id = :id AND sync_revision = :revision")
    suspend fun updateMainColorSyncFailedRetryable(id: String, revision: Long, message: String?, now: Long): Int

    @Query("UPDATE sync_tombstones SET sync_status = CASE WHEN retry_attempt_count + 1 >= 3 THEN 'NeedsUserAction' ELSE 'FailedRetryable' END, retry_attempt_count = MIN(retry_attempt_count + 1, 3), retry_after_epoch_millis = CASE WHEN retry_attempt_count + 1 >= 3 THEN NULL ELSE :now + CASE retry_attempt_count WHEN 0 THEN 60000 WHEN 1 THEN 300000 ELSE 900000 END END, sync_started_at_epoch_millis = NULL, sync_failure_message = :message WHERE id = :id AND revision = :revision")
    suspend fun updateTombstoneSyncFailedRetryable(id: String, revision: Long, message: String?, now: Long): Int

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

    @Query("SELECT photo_restore_guarded AS guarded, retry_after_epoch_millis AS retryAfterEpochMillis, photo_restore_retry_deadline_epoch_millis AS deadlineEpochMillis FROM clothing_items WHERE id = :itemId")
    suspend fun guardedPhotoRestoreState(itemId: String): GuardedPhotoRestoreState?

    @Transaction
    suspend fun upsertItemWithTags(item: ClothingItemEntity, tagIds: List<String>) {
        upsertItem(item.queuedForSync(guardedPhotoRestoreState(item.id)))
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

    @Query("SELECT * FROM garment_tags WHERE :categoryId IS NULL OR category_id = :categoryId")
    suspend fun tagsForDedupe(categoryId: String?): List<GarmentTagEntity>

    @Query("SELECT * FROM clothing_item_tags WHERE tag_id = :tagId")
    suspend fun itemRefsForTag(tagId: String): List<ClothingItemTagCrossRef>

    @Query("SELECT * FROM clothing_items WHERE id IN (:ids)")
    suspend fun clothingItemsByIds(ids: List<String>): List<ClothingItemEntity>

    @Query("SELECT id FROM garment_tags WHERE id IN (:ids)")
    suspend fun existingTagIds(ids: List<String>): List<String>

    @Query("SELECT * FROM sync_tombstones WHERE entity_type = 'garment_tag'")
    suspend fun garmentTagTombstones(): List<SyncTombstoneEntity>

    /**
     * Moves every garment on a dropped duplicate to its kept tag, then deletes the duplicate with its tombstone.
     * The plan was read before this transaction, so the plan is run again here on the live rows and tombstones. Only a
     * merge that the fresh plan still approves is applied: a renamed row, a row that has gone, or a keep that became a
     * ghost is skipped and the next launch plans again. Returns the drop ids that were deleted.
     */
    @Transaction
    suspend fun applyTagMerges(
        plannedMerges: List<TagMerge>,
        tombstoneByDropId: Map<String, SyncTombstoneEntity>,
    ): List<String> {
        val approved = TagDedupePlanner.plan(
            tagsForDedupe(null).map(GarmentTagEntity::toDedupeTag),
            garmentTagTombstones().associate { tombstone -> tombstone.entityId to tombstone.revision },
        ).toSet()
        val merges = plannedMerges.filter { merge -> merge in approved }
        val repointedRefs = merges.flatMap { merge ->
            itemRefsForTag(merge.dropId).map { ref -> ClothingItemTagCrossRef(ref.clothingItemId, merge.keepId) }
        }
        if (repointedRefs.isNotEmpty()) {
            insertItemTagRefs(repointedRefs)
            val itemIds = repointedRefs.map(ClothingItemTagCrossRef::clothingItemId).distinct()
            upsertClothingItems(
                clothingItemsByIds(itemIds).map { item ->
                    item.queuedForSync(
                        GuardedPhotoRestoreState(
                            guarded = item.photoRestoreGuarded,
                            retryAfterEpochMillis = item.retryAfterEpochMillis,
                            deadlineEpochMillis = item.photoRestoreRetryDeadlineEpochMillis,
                        ),
                    )
                },
            )
        }
        return merges.map(TagMerge::dropId).filter { dropId ->
            deleteEditableTagAndReferences(dropId, tombstoneByDropId.getValue(dropId)) > 0
        }
    }
}

internal fun GarmentTagEntity.toDedupeTag(): DedupeTag = DedupeTag(
    id = id,
    categoryId = categoryId,
    name = name,
    isSystem = isSystem,
    syncRevision = syncRevision,
)

@Dao
interface SyncTombstoneDao {
    @Query("SELECT * FROM sync_tombstones ORDER BY entity_type, entity_id")
    suspend fun getAllForSync(): List<SyncTombstoneEntity>

    @Upsert
    suspend fun upsert(tombstone: SyncTombstoneEntity)

    // Discard = undo: a tombstone written by a deletion that is being undone is removed before the entity is re-saved.
    @Query("DELETE FROM sync_tombstones WHERE entity_type = :entityType AND entity_id = :entityId")
    suspend fun deleteTombstone(entityType: String, entityId: String): Int
}

private fun ClothingItemEntity.queuedForSync(guardedPhotoRestoreState: GuardedPhotoRestoreState? = null): ClothingItemEntity {
    val revision = updatedAtEpochMillis.coerceAtLeast(System.currentTimeMillis())
    return copy(
        syncStatus = GarmentSyncStatus.Queued,
        syncRevision = revision,
        syncDirtyAtEpochMillis = revision,
        syncFailureMessage = null,
        photoRestoreGuarded = guardedPhotoRestoreState?.guarded == true,
        retryAfterEpochMillis = guardedPhotoRestoreState?.retryAfterEpochMillis,
        photoRestoreRetryDeadlineEpochMillis = guardedPhotoRestoreState?.deadlineEpochMillis,
    )
}

data class GuardedPhotoRestoreState(
    val guarded: Boolean,
    val retryAfterEpochMillis: Long?,
    val deadlineEpochMillis: Long?,
)

data class RestoredPhotoApplyStateEntity(
    val itemId: String,
    val revision: Long,
    val syncStatus: GarmentSyncStatus,
    val photoRestoreGuarded: Boolean,
    val retryAttemptCount: Int,
    val retryAfterEpochMillis: Long?,
    val retryDeadlineEpochMillis: Long?,
    val syncDirtyAtEpochMillis: Long?,
    val photoUri: String?,
)

/** Table a metadata sync row is claimed, settled and released in. Decided from the row's entity type alone (see [metadataSyncTable]). */
internal enum class MetadataSyncTable { TagCategory, GarmentTag, MainColor, Tombstone }

internal fun PendingMetadataSyncWorkEntity.metadataSyncTable(): MetadataSyncTable = when (entityType) {
    "tag_category", "category" -> MetadataSyncTable.TagCategory
    "garment_tag", "tag" -> MetadataSyncTable.GarmentTag
    "main_color", "palette_color", "color" -> MetadataSyncTable.MainColor
    else -> MetadataSyncTable.Tombstone
}
