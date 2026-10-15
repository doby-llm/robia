package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.TagOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagChangeSetTest {
    private val tops = tag("category-tops", "category", "Tops")
    private val dresses = tag("category-dresses", "category", "Dresses")
    private val formal = tag("occasion-formal", "occasion", "Formal")
    private val before = listOf(tops, dresses, formal)

    @Test
    fun addedCategoryBuildsAddedChangeSet() {
        val polos = tag("category-polos", "category", "Polos")

        val changeSet = savedTagChangeSet(before, polos)

        assertEquals(TagOperation.Added, changeSet?.operation)
        assertEquals("category", changeSet?.categoryId)
        assertEquals(setOf("category-polos"), changeSet?.touchedTagIds)
        assertEquals(before + polos, changeSet?.afterTags)
    }

    @Test
    fun renamedOccasionBuildsRenamedChangeSet() {
        val changeSet = savedTagChangeSet(before, formal.copy(name = "Wedding"))

        assertEquals(TagOperation.Renamed, changeSet?.operation)
        assertEquals("occasion", changeSet?.categoryId)
        assertEquals(setOf("occasion-formal"), changeSet?.touchedTagIds)
        assertEquals(before.map { tag -> tag.id }, changeSet?.afterTags?.map { tag -> tag.id })
    }

    @Test
    fun descriptionOnlyEditBuildsNoChangeSet() {
        assertNull(savedTagChangeSet(before, tops.copy(description = "a light top")))
    }

    @Test
    fun suggestOccasionsSwitchBuildsNoChangeSet() {
        assertNull(savedTagChangeSet(before, tops.copy(suggestOccasions = false)))
    }

    @Test
    fun seasonTagBuildsNoChangeSet() {
        val season = tag("season-summer", "season", "Summer")

        assertNull(savedTagChangeSet(before + season, season.copy(name = "Hot")))
    }

    @Test
    fun deletedCategoryBuildsDeletedChangeSet() {
        val changeSet = deletedTagChangeSet(before, dresses)

        assertEquals(TagOperation.Deleted, changeSet?.operation)
        assertEquals(setOf("category-dresses"), changeSet?.touchedTagIds)
        assertEquals(listOf(tops, formal), changeSet?.afterTags)
    }

    @Test
    fun deletedOccasionBuildsNoChangeSet() {
        assertNull(deletedTagChangeSet(before, formal))
    }

    @Test
    fun restoredDefaultsTouchOnlyAddedOrRenamedTags() {
        val defaults = listOf(tops, dresses.copy(name = "Vestidos"), tag("category-coats", "category", "Coats"))

        val changeSet = restoredTagChangeSet(before, "category", defaults)

        assertEquals(TagOperation.RestoredDefault, changeSet?.operation)
        assertEquals(setOf("category-dresses", "category-coats"), changeSet?.touchedTagIds)
        assertEquals(listOf(formal, tops, dresses.copy(name = "Vestidos"), tag("category-coats", "category", "Coats")), changeSet?.afterTags)
    }

    @Test
    fun restoreWithNoDifferencesBuildsNoChangeSet() {
        assertNull(restoredTagChangeSet(before, "category", listOf(tops, dresses)))
    }

    @Test
    fun restoreOfSeasonBuildsNoChangeSet() {
        assertNull(restoredTagChangeSet(before, "season", listOf(tag("season-summer", "season", "Summer"))))
    }

    private fun tag(id: String, categoryId: String, name: String) =
        GarmentTag(id = id, categoryId = categoryId, name = name, sortOrder = 0)
}
