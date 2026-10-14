package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
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
    equivalenceKey() == other.equivalenceKey() && photosEquivalent(sortedDeterministically().photos, other.sortedDeterministically().photos)

private data class SnapshotEquivalenceKey(
    val taxonomies: WardrobeTaxonomySnapshot,
    val garments: List<GarmentSyncRecord>,
    val garmentTags: List<GarmentTagMappingRecord>,
    val garmentColors: List<GarmentColorMappingRecord>,
    val tombstones: List<SyncTombstoneRecord>,
)

private fun WardrobeSyncSnapshot.equivalenceKey(): SnapshotEquivalenceKey {
    val sorted = sortedDeterministically()
    return SnapshotEquivalenceKey(
        taxonomies = sorted.taxonomies,
        garments = sorted.garments.map { garment -> garment.copy(embeddings = emptyList()) },
        garmentTags = sorted.garmentTags,
        garmentColors = sorted.garmentColors,
        tombstones = sorted.tombstones,
    )
}

/**
 * Local photos carry only [GarmentPhotoRecord.contentHash] until they are uploaded, while remote photos also carry
 * [GarmentPhotoRecord.sourceHash]. A pair is the same image when both source hashes are set and equal, or content hashes match.
 */
private fun photosEquivalent(local: List<GarmentPhotoRecord>, remote: List<GarmentPhotoRecord>): Boolean =
    local.size == remote.size && local.zip(remote).all { (localPhoto, remotePhoto) ->
        localPhoto.garmentId == remotePhoto.garmentId &&
            (localPhoto.sourceHash != null && localPhoto.sourceHash == remotePhoto.sourceHash ||
                localPhoto.contentHash == remotePhoto.contentHash)
    }
