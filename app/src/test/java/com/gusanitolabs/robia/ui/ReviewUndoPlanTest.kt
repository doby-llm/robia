package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.suggestions.TagChangeSet
import com.gusanitolabs.robia.suggestions.TagOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewUndoPlanTest {
    private val dress = tag("dress", name = "Dresses")
    private val shirt = tag("shirt", name = "Shirts")
    private val before = listOf(dress, shirt)

    @Test
    fun addedTagIsRemovedAndNothingIsRestored() {
        val added = tag("skirt", name = "Skirts")
        val plan = tagReviewUndoPlan(request(TagOperation.Added, setOf(added.id), before, before + added), emptyList(), NOW)

        assertEquals(listOf(added), plan.removeTags)
        assertTrue(plan.restoreTags.isEmpty())
        assertTrue(plan.tombstonesToClear.isEmpty())
    }

    @Test
    fun addedTagIsRemovedEvenWhileOtherTagsAreInUse() {
        val added = tag("skirt", name = "Skirts")
        val plan = tagReviewUndoPlan(
            request(TagOperation.Added, setOf(added.id), before, before + added),
            currentGarments = listOf(garment("g1", listOf(shirt))),
            now = NOW,
        )

        assertEquals(listOf(added), plan.removeTags)
    }

    @Test
    fun addedTagThatAGarmentStillReferencesIsKept() {
        val added = tag("skirt", name = "Skirts")
        val plan = tagReviewUndoPlan(
            request(TagOperation.Added, setOf(added.id), before, before + added),
            currentGarments = listOf(garment("g1", listOf(added))),
            now = NOW,
        )

        assertTrue(plan.removeTags.isEmpty())
    }

    @Test
    fun restoreDefaultsMergedIntoBuiltInTagKeepsTheRepointedGarmentsOnIt() {
        // The built-in "Dresses" was gone. The restore merges the custom "Dresses" into it: the garment now carries the
        // built-in id. Undoing the restore must not remove that id, or the garment loses its tag.
        val custom = tag("custom-dresses", name = "Dresses")
        val builtIn = tag("dress", name = "Dresses")
        val changeSet = restoredTagChangeSet(listOf(custom, shirt), "category", listOf(builtIn))!!
        val repointed = garment("g1", tags = listOf(builtIn))

        val plan = tagReviewUndoPlan(
            TagReviewRequest(changeSet, listOf(garment("g1", tags = listOf(custom)))),
            currentGarments = listOf(repointed),
            now = NOW,
        )

        assertTrue(plan.removeTags.isEmpty())
        assertTrue(plan.restoreGarments.isEmpty())
    }

    @Test
    fun renamedTagIsRestoredAndGarmentsReferencingItAreLeftAlone() {
        val renamed = dress.copy(name = "Vestidos")
        val garment = garment("g1", tags = listOf(renamed, shirt))
        val plan = tagReviewUndoPlan(
            request(TagOperation.Renamed, setOf(dress.id), before, listOf(renamed, shirt), garments = listOf(garment)),
            currentGarments = listOf(garment),
            now = NOW,
        )

        assertEquals(listOf(dress), plan.restoreTags)
        assertTrue(plan.removeTags.isEmpty())
        assertTrue(plan.restoreGarments.isEmpty())
    }

    @Test
    fun renamedTagIsWrittenBackWithItsOldName() {
        val renamed = dress.copy(name = "Vestidos")
        val plan = tagReviewUndoPlan(
            request(TagOperation.Renamed, setOf(dress.id), before, listOf(renamed, shirt)),
            emptyList(),
            NOW,
        )

        assertEquals(listOf(dress), plan.restoreTags)
        assertTrue(plan.removeTags.isEmpty())
    }

    @Test
    fun deletedTagIsRestoredWithItsTombstoneClearedAndItsGarmentsGivenBack() {
        val garment = garment("g1", tags = listOf(dress, shirt))
        val plan = tagReviewUndoPlan(
            request(TagOperation.Deleted, setOf(dress.id), before, listOf(shirt), garments = listOf(garment)),
            currentGarments = listOf(garment.copy(tags = listOf(shirt))),
            now = NOW,
        )

        assertEquals(listOf(dress), plan.restoreTags)
        assertEquals(listOf("garment_tag" to dress.id), plan.tombstonesToClear)
        assertEquals(listOf(garment.copy(tags = listOf(dress, shirt), updatedAtEpochMillis = NOW)), plan.restoreGarments)
    }

    @Test
    fun garmentChangedSinceTheDeleteIsNotRestored() {
        val garment = garment("g1", tags = listOf(dress, shirt))
        val skirt = tag("skirt", name = "Skirts")
        val plan = tagReviewUndoPlan(
            request(TagOperation.Deleted, setOf(dress.id), before, listOf(shirt), garments = listOf(garment)),
            currentGarments = listOf(garment.copy(tags = listOf(shirt, skirt))),
            now = NOW,
        )

        assertTrue(plan.restoreGarments.isEmpty())
    }

    @Test
    fun garmentThatNeverLostTheDeletedTagIsNotTouched() {
        val garment = garment("g1", tags = listOf(shirt))
        val plan = tagReviewUndoPlan(
            request(TagOperation.Deleted, setOf(dress.id), before, listOf(shirt), garments = listOf(garment)),
            currentGarments = listOf(garment),
            now = NOW,
        )

        assertTrue(plan.restoreGarments.isEmpty())
    }

    @Test
    fun deletedGarmentIsNotRestoredWhenItIsNoLongerInTheWardrobe() {
        val garment = garment("g1", tags = listOf(dress))
        val restored = restoredGarmentTags(garment, currentGarments = emptyList(), deletedTagIds = setOf(dress.id), now = NOW)

        assertNull(restored)
    }

    @Test
    fun restoreDefaultsUndoesRenamesAndRemovesTheDefaultsItAdded() {
        val oldDress = dress.copy(name = "Old dresses")
        val oldBefore = listOf(oldDress, shirt)
        val defaults = listOf(dress, tag("coat", name = "Coats"))
        val changeSet = restoredTagChangeSet(oldBefore, "category", defaults)!!

        val plan = tagReviewUndoPlan(TagReviewRequest(changeSet, emptyList()), emptyList(), NOW)

        assertEquals(listOf(oldDress), plan.restoreTags)
        assertEquals(listOf(tag("coat", name = "Coats")), plan.removeTags)
    }

    @Test
    fun addedColourIsRemoved() {
        val red = color("red")
        val plan = colorReviewUndoPlan(palette(ColorPaletteOperation.Added, listOf(color("blue")), listOf(color("blue"), red), red.id))

        assertEquals(listOf(red), plan.removeColors)
        assertTrue(plan.restoreColors.isEmpty())
    }

    @Test
    fun editedColourIsWrittenBackAsItWas() {
        val blue = color("blue")
        val edited = blue.copy(hex = "#0000FF")
        val plan = colorReviewUndoPlan(palette(ColorPaletteOperation.Edited, listOf(blue), listOf(edited), blue.id))

        assertEquals(listOf(blue), plan.restoreColors)
        assertTrue(plan.removeColors.isEmpty())
    }

    @Test
    fun deletedColourIsRestoredWithItsTombstoneCleared() {
        val blue = color("blue")
        val plan = colorReviewUndoPlan(palette(ColorPaletteOperation.Deleted, listOf(blue), emptyList(), blue.id))

        assertEquals(listOf(blue), plan.restoreColors)
        assertEquals(listOf("main_color" to blue.id), plan.tombstonesToClear)
    }

    @Test
    fun restoreDefaultColoursSaveNothingBeforeTheCommitSoThereIsNothingToUndo() {
        val changeSet = ColorPaletteChangeSet(
            beforePalette = listOf(color("blue")),
            afterPalette = DefaultTags.mainColors,
            touchedColorId = null,
            operation = ColorPaletteOperation.RestoredDefault,
            requiresCommitOnSave = true,
        )

        assertTrue(colorReviewUndoPlan(changeSet).tombstonesToClear.isEmpty())
        assertEquals(ReviewUndoPlan(), colorReviewUndoPlan(changeSet))
    }

    private fun request(
        operation: TagOperation,
        touched: Set<String>,
        beforeTags: List<GarmentTag>,
        afterTags: List<GarmentTag>,
        garments: List<ClothingItem> = emptyList(),
    ) = TagReviewRequest(
        changeSet = TagChangeSet(
            id = "change",
            operation = operation,
            categoryId = "category",
            touchedTagIds = touched,
            beforeTags = beforeTags,
            afterTags = afterTags,
        ),
        garments = garments,
    )

    private fun palette(
        operation: ColorPaletteOperation,
        before: List<MainColor>,
        after: List<MainColor>,
        touchedId: String,
    ) = ColorPaletteChangeSet(
        beforePalette = before,
        afterPalette = after,
        touchedColorId = touchedId,
        operation = operation,
    )

    private fun tag(id: String, name: String) = GarmentTag(id = id, categoryId = "category", name = name, sortOrder = 0)

    private fun color(id: String) = MainColor(id = id, name = id, hex = "#112233", sortOrder = 0)

    private fun garment(id: String, tags: List<GarmentTag>) =
        ClothingItem(id = id, name = id, tags = tags, createdAtEpochMillis = 0, updatedAtEpochMillis = 0)

    private companion object {
        const val NOW = 1_000L
    }
}
