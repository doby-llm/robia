package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.data.local.ClothingItemEntity
import com.gusanitolabs.robia.data.local.ColorMetricsEntity
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A sync import writes every column of the garment row, and the sync record carries no original photo. So the local
 * original fields must be carried over, or the import erases them (R3-DEC-033, B1).
 */
class OriginalPhotoImportTest {
    private val recipeJson = "{\"steps\":[\"precise\"]}"

    private fun localItem(id: String, originalUri: String?, originalRecipe: String?) = ClothingItemEntity(
        id = id,
        name = id,
        notes = "",
        photoUri = "content://cutout-$id",
        fitValue = null,
        colorMetrics = ColorMetricsEntity(null, null, null, null, null, null, null, null, null, null),
        isFavorite = false,
        isArchived = false,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
        originalUri = originalUri,
        originalRecipe = originalRecipe,
    )

    @Test
    fun mergeKeepsTheLocalOriginalOfAnExistingGarment() {
        val local = mapOf("a" to localItem("a", originalUri = "content://original-a", originalRecipe = recipeJson))

        val restored = restoredClothingItems(snapshotWithGarments("a"), emptyMap(), local)

        val row = restored.single { it.id == "a" }
        assertEquals("content://original-a", row.originalUri)
        assertEquals(recipeJson, row.originalRecipe)
    }

    @Test
    fun aGarmentNewToThePhoneGetsNoOriginal() {
        val local = mapOf("a" to localItem("a", originalUri = "content://original-a", originalRecipe = recipeJson))

        val restored = restoredClothingItems(snapshotWithGarments("a", "c"), emptyMap(), local)

        val fresh = restored.single { it.id == "c" }
        assertNull(fresh.originalUri)
        assertNull(fresh.originalRecipe)
    }

    @Test
    fun replaceKeepsOriginalsOfMatchingIdsAndWritesNoRowForOthers() {
        // "Use backup" passes the rows of the backup's ids, read before the wardrobe is cleared. Local "b" is not in the
        // backup: its original is not written, so it stays a file without a row (an orphan for CLN). It is not deleted here.
        val local = mapOf(
            "a" to localItem("a", originalUri = "content://original-a", originalRecipe = recipeJson),
            "b" to localItem("b", originalUri = "content://original-b", originalRecipe = recipeJson),
        )

        val restored = restoredClothingItems(snapshotWithGarments("a", "c"), emptyMap(), local)

        assertEquals(listOf("a", "c"), restored.map { it.id })
        assertEquals("content://original-a", restored.single { it.id == "a" }.originalUri)
        assertNull(restored.single { it.id == "c" }.originalUri)
    }

    @Test
    fun changedPhotoWithoutRestoredOriginalClearsTheRecipe() {
        // R3-DEC-039: the local recipe was recorded against the old cutout; the imported cutout must not keep it.
        val kept = importedOriginal(
            restoredUri = null,
            restoredRecipe = null,
            localUri = "content://original-a",
            localRecipe = recipeJson,
            photoChanged = true,
        )

        assertNull(kept.uri)
        assertNull(kept.recipe)
    }

    @Test
    fun unchangedPhotoKeepsTheLocalRecipe() {
        val kept = importedOriginal(
            restoredUri = null,
            restoredRecipe = null,
            localUri = "content://original-a",
            localRecipe = recipeJson,
            photoChanged = false,
        )

        assertEquals("content://original-a", kept.uri)
        assertEquals(recipeJson, kept.recipe)
    }

    @Test
    fun anImportThatReplacesTheCutoutClearsTheLocalRecipeAndKeepsItWhenTheCutoutIsUnchanged() {
        val local = mapOf("a" to localItem("a", originalUri = "content://original-a", originalRecipe = recipeJson))
        fun importing(restoredPhotoUri: String) = restoredClothingItems(
            snapshotWithGarments("a").copy(
                photos = listOf(GarmentPhotoRecord(garmentId = "a", localUri = restoredPhotoUri, restoredLocalUri = restoredPhotoUri)),
            ),
            emptyMap(),
            local,
        ).single { it.id == "a" }

        val replaced = importing("content://cutout-new-a")
        assertNull(replaced.originalUri)
        assertNull(replaced.originalRecipe)

        val unchanged = importing("content://cutout-a")
        assertEquals("content://original-a", unchanged.originalUri)
        assertEquals(recipeJson, unchanged.originalRecipe)
    }

    @Test
    fun restoredOriginalReplacesTheLocalOneWithItsRecipeWhetherOrNotThePhotoChanged() {
        val restoredRecipe = "{\"steps\":[\"mlkit\"]}"
        for (photoChanged in listOf(true, false)) {
            val taken = importedOriginal(
                restoredUri = "content://restored-original-a",
                restoredRecipe = restoredRecipe,
                localUri = "content://original-a",
                localRecipe = recipeJson,
                photoChanged = photoChanged,
            )

            assertEquals("content://restored-original-a", taken.uri)
            assertEquals(restoredRecipe, taken.recipe)
        }
    }

    @Test
    fun replaceReadsTheLocalOriginalsBeforeTheWardrobeIsCleared() {
        // The Room transaction cannot run on the JVM, so the order is pinned in the source: the read precedes the delete.
        val source = File("src/main/java/com/gusanitolabs/robia/sync/LocalWardrobeSyncSnapshotRepository.kt").readText()
        val replace = source.substringAfter("override suspend fun replaceLocalWithRemote(")
            .substringBefore("data class ImportSnapshotResult")

        assertTrue(replace.indexOf("localItemsFor(snapshot)") in 0 until replace.indexOf("wardrobeDao.deleteAllItems()"))
    }
}
