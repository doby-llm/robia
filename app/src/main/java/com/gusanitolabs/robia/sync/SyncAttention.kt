package com.gusanitolabs.robia.sync

/** Things only the user can fix. Derived from the database and settings; rendered by the UI (S05). */
enum class SyncAttentionKind { SignInRequired, DriveFull, PhotoUnrecoverable, PhotoUploadFailed, BackupConflict, MergeConfirmation }

data class SyncAttention(
    val kind: SyncAttentionKind,
    val garmentIds: List<String> = emptyList(),
)

/** Counts shown before the user confirms connecting Drive on a phone that already has garments. */
data class MergeSummary(
    val remoteGarmentCount: Int,
    val localGarmentCount: Int,
    val mergedGarmentCount: Int,
)
