package com.gusanitolabs.robia.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * REQ-DU-02: a garment pair the user marked "Not duplicates". The ids are stored sorted (garment_a < garment_b), so the pair
 * is the same whichever garment it was found from. A row outlives its garments: the snapshot export drops the pairs of
 * tombstoned garments, and nothing else removes a row.
 */
@Entity(tableName = "dismissed_duplicate_pairs", primaryKeys = ["garment_a", "garment_b"])
data class DismissedDuplicatePairEntity(
    @ColumnInfo(name = "garment_a") val garmentA: String,
    @ColumnInfo(name = "garment_b") val garmentB: String,
    @ColumnInfo(name = "dismissed_at") val dismissedAtEpochMillis: Long,
)
