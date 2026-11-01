package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.COLLECTION_CATEGORY_ID
import com.gusanitolabs.robia.core.model.GarmentTag
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-C02-1 and V-C02-2 (REQ-COL-02, REQ-COL-03; R4-D05, R4-D07): the Collections rules and their wiring in Manage. */
class CollectionManageRulesTest {
    private val otherCategories = listOf("category", "season", "occasion", "location")

    @Test
    fun membersActionIsShownOnlyForCollectionRowsWithACallback() {
        assertTrue(showsMembersAction(COLLECTION_CATEGORY_ID, hasCallback = true))
        assertFalse(showsMembersAction(COLLECTION_CATEGORY_ID, hasCallback = false))
        otherCategories.forEach { categoryId ->
            assertFalse(categoryId, showsMembersAction(categoryId, hasCallback = true))
        }
    }

    @Test
    fun collectionEditorHidesTheDescriptionFieldAndOtherCategoriesKeepIt() {
        assertFalse(showsDescriptionField(COLLECTION_CATEGORY_ID))
        otherCategories.forEach { categoryId ->
            assertTrue(categoryId, showsDescriptionField(categoryId))
        }
    }

    @Test
    fun onlyCollectionsNeedDeleteConfirmation() {
        assertTrue(needsDeleteConfirmation(tag(COLLECTION_CATEGORY_ID)))
        otherCategories.forEach { categoryId ->
            assertFalse(categoryId, needsDeleteConfirmation(tag(categoryId)))
        }
    }

    @Test
    fun restoreDefaultIsNeverOfferedForCollectionsAndSuggestOccasionsStaysOnCategoryOnly() {
        val manage = source("app/src/main/java/com/gusanitolabs/robia/ui/ManageTagsScreen.kt")

        assertTrue(manage.contains("private val RestorableTagCategoryIds = setOf(\"category\", \"season\", \"occasion\")"))
        assertTrue(manage.contains("if (state.categoryId == \"category\") {"))
    }

    @Test
    fun editorAndRowsUseTheRulesAndTheConfirmedDeletePathCallsOnDeleteTag() {
        val manage = source("app/src/main/java/com/gusanitolabs/robia/ui/ManageTagsScreen.kt")

        assertTrue(manage.contains("showsDescriptionField(state.categoryId)"))
        assertTrue(manage.contains("showsMembersAction(tag.categoryId, hasCallback = onEditMembers != null)"))
        assertTrue(manage.contains("if (needsDeleteConfirmation(tag)) pendingCollectionDelete = tag else onDeleteTag(tag)"))
        assertTrue(Regex("onConfirm = \\{\\s*onDeleteTag\\(tag\\)").containsMatchIn(manage))
        assertFalse(manage.contains("Card("))
    }

    private fun tag(categoryId: String) = GarmentTag(
        id = "$categoryId-test",
        categoryId = categoryId,
        name = "Test",
        sortOrder = 10,
        isSystem = false,
    )

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
