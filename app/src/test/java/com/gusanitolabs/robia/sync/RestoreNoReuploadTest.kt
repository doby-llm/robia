package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.EmbeddingSyncRecord
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RestoreNoReuploadTest {
    @Test
    fun metadataAndEmbeddingDifferencesDoNotRequireAFinalUpload() {
        val remote = snapshot()
        val merged = remote.copy(
            metadata = remote.metadata.copy(revision = 42L, writerDeviceId = "another-phone"),
            garments = remote.garments.map { garment ->
                garment.copy(
                    embeddings = listOf(
                        EmbeddingSyncRecord(
                            kind = "image",
                            modelId = "model",
                            recipeId = "recipe",
                            dim = 2,
                            inputHash = "input",
                            computedAtEpochMillis = 1L,
                            b64 = "AAAA",
                        ),
                    ),
                )
            },
        )

        assertTrue(merged.equivalentForUpload(remote))
    }

    @Test
    fun userDataChangesRequireAFinalUpload() {
        val remote = snapshot()

        assertFalse(remote.copy(garments = listOf(garment(name = "Renamed"))).equivalentForUpload(remote))
        assertFalse(remote.copy(photos = listOf(photo(contentHash = "different-hash"))).equivalentForUpload(remote))
        assertFalse(remote.copy(photos = emptyList()).equivalentForUpload(remote))
    }

    @Test
    fun restoreOnlyUploadsWhenTheMergedSnapshotDiffersFromRemote() {
        // The predicate is checked above; this guards the wiring in the restore path.
        val processor = File("src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt").readText()
        val guard = processor.substringAfter("val finalUploadGuardReason = mergedSnapshot.finalUploadGuardReasonAfterRestore()")
            .substringBefore("diagnostics.recordFinalUploadAttempted()")

        assertTrue(guard.contains("if (mergedSnapshot.equivalentForUpload(remoteSnapshot))"))
        assertTrue(guard.contains("remote_equivalent"))
    }

    private fun snapshot(): WardrobeSyncSnapshot = WardrobeSyncSnapshot(
        garments = listOf(garment(name = "Blue shirt")),
        photos = listOf(photo(contentHash = "hash-1")),
    )

    private fun garment(name: String): GarmentSyncRecord = GarmentSyncRecord(
        id = "garment-1",
        name = name,
        notes = "",
        fitValue = null,
        isFavorite = false,
        isArchived = false,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
        revision = 3L,
    )

    private fun photo(contentHash: String): GarmentPhotoRecord = GarmentPhotoRecord(
        garmentId = "garment-1",
        localUri = "content://local/1",
        blobPath = "photos/garment-1/0123456789abcdef.webp",
        mimeType = "image/webp",
        contentHash = contentHash,
    )
}
