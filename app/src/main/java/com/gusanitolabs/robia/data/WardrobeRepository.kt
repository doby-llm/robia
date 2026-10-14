package com.gusanitolabs.robia.data

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.core.model.TagCategory
import kotlinx.coroutines.flow.Flow

interface WardrobeRepository {
    fun observeActiveItems(): Flow<List<ClothingItem>>
    fun observeItem(id: String): Flow<ClothingItem?>
    fun observePendingGarmentSyncCount(): Flow<Int>
    fun observeGarmentSyncAttentionCount(): Flow<Int>
    fun observeGuardedPhotoRestoreCount(): Flow<Int>
    fun observePendingMetadataSyncCount(): Flow<Int>
    fun observeMetadataSyncAttentionCount(): Flow<Int>
    suspend fun pendingGarmentSyncWork(): List<PendingGarmentSyncWork>
    suspend fun pendingMetadataSyncWork(): List<PendingMetadataSyncWork>
    suspend fun nextRunnableSyncRetryEpochMillis(): Long?
    /** A pending Drive-side garment deletion must win over any restored-photo retry. */
    suspend fun hasPendingCloudDeletion(): Boolean
    suspend fun recoverStaleRunningSyncWork(staleBeforeEpochMillis: Long): Int
    suspend fun upsertItem(item: ClothingItem)
    suspend fun upsertItems(items: List<ClothingItem>)
    suspend fun archiveItem(id: String, updatedAtEpochMillis: Long)
    suspend fun archiveItems(ids: List<String>, updatedAtEpochMillis: Long)
    suspend fun markGarmentSyncing(id: String, revision: Long, startedAtEpochMillis: Long): Boolean
    suspend fun markGarmentSynced(id: String, revision: Long, syncedAtEpochMillis: Long): Boolean
    suspend fun markGarmentSyncFailedRetryable(id: String, revision: Long, message: String? = null, now: Long = System.currentTimeMillis()): Boolean
    suspend fun markGarmentSyncAuthBlocked(id: String, message: String? = null): Boolean
    /** Transient failure: the row stays pending as Queued, without attempt counting or backoff. */
    suspend fun releaseGarmentSyncToQueued(id: String, revision: Long): Boolean
    suspend fun markGarmentNeedsUserAction(id: String, revision: Long, reasonCode: String): Boolean
    suspend fun claimGarmentPhotoRestoreRetry(id: String, startedAtEpochMillis: Long = System.currentTimeMillis(), now: Long = startedAtEpochMillis): Long?
    suspend fun markGarmentPhotoRestoreFailed(id: String, message: String, now: Long = System.currentTimeMillis()): Boolean
    /** Transient guarded-photo failure: stays guarded and retryable without counting an attempt. */
    suspend fun markGarmentPhotoRestoreTransient(id: String, message: String): Boolean
    suspend fun guardedPhotoRetryCandidateIds(now: Long = System.currentTimeMillis()): List<String>
    /** Garments parked for the user with a given reason code (for example `drive_full`). */
    fun observeGarmentNeedsUserActionCount(reasonCode: String): Flow<Int>
    suspend fun markMetadataSyncing(work: PendingMetadataSyncWork): Boolean
    suspend fun markMetadataSynced(work: PendingMetadataSyncWork, syncedAtEpochMillis: Long): Boolean
    suspend fun markMetadataSyncFailedRetryable(work: PendingMetadataSyncWork, message: String? = null): Boolean
    suspend fun markMetadataSyncAuthBlocked(work: PendingMetadataSyncWork, message: String? = null): Boolean
    suspend fun releaseMetadataSyncToQueued(work: PendingMetadataSyncWork): Boolean
}

data class PendingGarmentSyncWork(
    val id: String,
    val revision: Long,
)

data class PendingMetadataSyncWork(
    val entityType: String,
    val id: String,
    val revision: Long,
)

interface TagRepository {
    fun observeCategories(): Flow<List<TagCategory>>
    fun observeTags(): Flow<List<GarmentTag>>
    fun observeMainColors(): Flow<List<MainColor>>
    suspend fun upsertCategory(category: TagCategory)
    suspend fun upsertTag(tag: GarmentTag)
    suspend fun upsertMainColor(color: MainColor)
    suspend fun applyMainColorChange(
        upsertColors: List<MainColor>,
        deleteColorIds: List<String>,
        updatedItems: List<ClothingItem>,
    )
    suspend fun deleteCustomTag(id: String)
    suspend fun deleteMainColor(id: String)
    suspend fun restoreDefaultTags(categoryId: String)
    suspend fun resetMainColorsToDefaults()
    suspend fun seedDefaultsIfNeeded()
}
