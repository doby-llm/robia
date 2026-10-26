package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DismissedPair
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

/**
 * True when [other] is the same phone state as this one: the records [equivalentForUpload] compares, and the photo records exactly.
 * For two local exports, which carry no upload hashes, so [equivalentForUpload] cannot match their photos. A photo swap that keeps
 * the garment revision (same garment, different URI) is a change here.
 */
internal fun WardrobeSyncSnapshot.unchangedSince(other: WardrobeSyncSnapshot): Boolean =
    equivalenceKey() == other.equivalenceKey() && sortedDeterministically().photos == other.sortedDeterministically().photos

/**
 * Ordinary upload may be skipped only when [remote] already holds exactly this snapshot. Every doubt keeps the upload:
 * pending vectors, a pending cloud deletion, a guarded photo on either side, or a legacy photo key (the upload re-keys it) always upload.
 *
 * Photo-free wardrobes only. A local photo carries no content hash until it is uploaded (see [photosEquivalent]), so a
 * wardrobe with any photo on either side always takes the normal upload path. Schema version and wardrobe id must match too,
 * so an older-schema or other-wardrobe remote is rewritten.
 */
internal fun WardrobeSyncSnapshot.remoteAlreadyHolds(
    remote: WardrobeSyncSnapshot,
    hasPendingEmbeddings: Boolean,
    hasPendingCloudDeletion: Boolean,
): Boolean =
    !hasPendingEmbeddings &&
        !hasPendingCloudDeletion &&
        photos.isEmpty() &&
        remote.photos.isEmpty() &&
        metadata.schemaVersion == remote.metadata.schemaVersion &&
        metadata.wardrobeId == remote.metadata.wardrobeId &&
        guardedPhotoRestoreIssues().isEmpty() &&
        remote.guardedPhotoRestoreIssues().isEmpty() &&
        equivalentForUpload(remote)

private data class SnapshotEquivalenceKey(
    val taxonomies: WardrobeTaxonomySnapshot,
    val garments: List<GarmentSyncRecord>,
    val garmentTags: List<GarmentTagMappingRecord>,
    val garmentColors: List<GarmentColorMappingRecord>,
    val tombstones: List<SyncTombstoneRecord>,
    val dismissedDuplicates: List<DismissedPair>,
)

private fun WardrobeSyncSnapshot.equivalenceKey(): SnapshotEquivalenceKey {
    val sorted = sortedDeterministically()
    return SnapshotEquivalenceKey(
        taxonomies = sorted.taxonomies,
        garments = sorted.garments.map { garment -> garment.copy(embeddings = emptyList()) },
        garmentTags = sorted.garmentTags,
        garmentColors = sorted.garmentColors,
        tombstones = sorted.tombstones,
        dismissedDuplicates = sorted.dismissedDuplicates.map(DismissedPair::withOrderedIds).sortedWith(
            compareBy(DismissedPair::garmentA, DismissedPair::garmentB),
        ),
    )
}

/** A pair is the same pair whichever id comes first: the key orders the two ids, so a reversed pair compares equal. */
private fun DismissedPair.withOrderedIds(): DismissedPair =
    if (garmentA <= garmentB) this else copy(garmentA = garmentB, garmentB = garmentA)

/**
 * Local photos carry only [GarmentPhotoRecord.contentHash] until they are uploaded, while remote photos also carry
 * [GarmentPhotoRecord.sourceHash]. A pair is the same image when both source hashes are set and equal, or content hashes match.
 * blobPath, mimeType and byteSize are not compared: the blob key derives from the content hash, so equal hashes mean equal blobs.
 *
 * A phone photo with no hash never matches: two photos with no hash are not known to be the same image. A placeholder (no phone
 * file, no hash) is compared as before. Known deviation (not fixed): the phone's export carries no hash, so a local photo never
 * matches a remote photo by hash. A photo-bearing wardrobe therefore never records the base, and any photo the phone holds with
 * a remote photo for the same garment is not equivalent (the merge keeps the newer record, see mergePhotos). Carrying the
 * upload hash into the export would remove the difference.
 */
private fun photosEquivalent(local: List<GarmentPhotoRecord>, remote: List<GarmentPhotoRecord>): Boolean =
    local.size == remote.size && local.zip(remote).all { (localPhoto, remotePhoto) ->
        localPhoto.garmentId == remotePhoto.garmentId &&
            (localPhoto.sourceHash != null && localPhoto.sourceHash == remotePhoto.sourceHash ||
                localPhoto.contentHash == remotePhoto.contentHash && (localPhoto.contentHash != null || localPhoto.localUri.isBlank()))
    }
