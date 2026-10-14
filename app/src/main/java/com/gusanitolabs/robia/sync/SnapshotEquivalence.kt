package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.GarmentTagMappingRecord
import com.gusanitolabs.robia.core.model.SyncTombstoneRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot

/**
 * True when uploading [this] would not change what [other] already holds in Drive.
 *
 * Metadata (revision, writer device, generation time) and embeddings are ignored: embeddings are
 * never a reason to upload during restore, and a metadata-only difference is not user data.
 */
internal fun WardrobeSyncSnapshot.equivalentForUpload(other: WardrobeSyncSnapshot): Boolean =
    equivalenceKey() == other.equivalenceKey()

private data class SnapshotEquivalenceKey(
    val taxonomies: WardrobeTaxonomySnapshot,
    val garments: List<GarmentSyncRecord>,
    val garmentTags: List<GarmentTagMappingRecord>,
    val garmentColors: List<GarmentColorMappingRecord>,
    val photos: List<Pair<String, String?>>,
    val tombstones: List<SyncTombstoneRecord>,
)

private fun WardrobeSyncSnapshot.equivalenceKey(): SnapshotEquivalenceKey {
    val sorted = sortedDeterministically()
    return SnapshotEquivalenceKey(
        taxonomies = sorted.taxonomies,
        garments = sorted.garments.map { garment -> garment.copy(embeddings = emptyList()) },
        garmentTags = sorted.garmentTags,
        garmentColors = sorted.garmentColors,
        photos = sorted.photos.map { photo -> photo.garmentId to (photo.sourceHash ?: photo.contentHash) },
        tombstones = sorted.tombstones,
    )
}
