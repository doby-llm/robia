package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.CalibrationPair
import com.gusanitolabs.robia.suggestions.DuplicatePairKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DuplicateCalibrationScreenTest {
    private fun garment(
        id: String,
        photoUri: String? = "photo-$id",
        category: String? = "category-dresses",
        archived: Boolean = false,
        name: String = id,
    ) = ClothingItem(
        id = id,
        name = name,
        photoUri = photoUri,
        tags = listOfNotNull(category?.let { GarmentTag(id = it, categoryId = "category", name = it, sortOrder = 0) }),
        isArchived = archived,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    @Test
    fun signatureChangesWhenPhotoCategoryOrArchiveStateChanges_andNotForOtherFields() {
        val before = calibrationSignatures(listOf(garment("a")))

        assertNotEquals(before, calibrationSignatures(listOf(garment("a", photoUri = "photo-new"))))
        assertNotEquals(before, calibrationSignatures(listOf(garment("a", category = "category-skirts"))))
        assertNotEquals(before, calibrationSignatures(listOf(garment("a", archived = true))))
        assertEquals(before, calibrationSignatures(listOf(garment("a", name = "Renamed"))))
    }

    @Test
    fun archivingAGarmentResetsTheMarks_whileAReorderAloneKeepsThem() {
        val before = calibrationSignatures(listOf(garment("a"), garment("b")))

        assertNotEquals(before, calibrationSignatures(listOf(garment("a"), garment("b", archived = true))))
        assertEquals(before, calibrationSignatures(listOf(garment("b"), garment("a"))))
    }

    @Test
    fun reorderingThreeGarmentsKeepsTheSignature_soWardrobeWritesKeepTheMarks() {
        val before = calibrationSignatures(listOf(garment("a"), garment("b"), garment("c", category = "category-skirts")))

        assertEquals(before, calibrationSignatures(listOf(garment("c", category = "category-skirts"), garment("a"), garment("b"))))
        assertEquals(before, calibrationSignatures(listOf(garment("b"), garment("c", category = "category-skirts"), garment("a"))))
    }

    @Test
    fun changedPhotoOrCategoryOfOneOfThreeGarmentsResetsTheSignature_evenWhenReordered() {
        val before = calibrationSignatures(listOf(garment("a"), garment("b"), garment("c")))

        assertNotEquals(before, calibrationSignatures(listOf(garment("c"), garment("a"), garment("b", photoUri = "photo-new"))))
        assertNotEquals(before, calibrationSignatures(listOf(garment("a"), garment("b", category = "category-skirts"), garment("c"))))
        assertNotEquals(before, calibrationSignatures(listOf(garment("c", category = "category-skirts"), garment("b"), garment("a"))))
    }

    @Test
    fun marksFollowTheGarmentPairNotTheRow() {
        val pairs = listOf(CalibrationPair("a", "b", 0.9f), CalibrationPair("c", "d", 0.8f))
        val marks = mapOf(DuplicatePairKey.of("d", "c") to true)

        assertEquals(mapOf(1 to true), calibrationMarkRows(pairs, marks))

        // After a rescan the same pair sits on another row: the mark moves with it.
        val rescanned = listOf(CalibrationPair("c", "d", 0.95f), CalibrationPair("a", "b", 0.9f))
        assertEquals(mapOf(0 to true), calibrationMarkRows(rescanned, marks))
    }

    @Test
    fun togglingAPairMarkClearsOnRepeatAndSwitchesOnTheOtherValue() {
        val key = DuplicatePairKey.of("a", "b")

        val same = toggledMark(emptyMap<DuplicatePairKey, Boolean>(), key, true)
        assertEquals(mapOf(key to true), same)
        assertEquals(mapOf(key to false), toggledMark(same, key, false))
        assertEquals(emptyMap<DuplicatePairKey, Boolean>(), toggledMark(same, key, true))
    }
}
