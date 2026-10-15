package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StoredTagDescriptionTest {
    private val dressesDefault = EmbeddingRecipe.DEFAULT_LABEL_DESCRIPTIONS.getValue("category-dresses")

    @Test
    fun unchangedDefaultDescriptionIsStoredAsNull() {
        assertNull(storedTagDescription(dressesDefault, dressesDefault))
        assertNull(storedTagDescription("  $dressesDefault \n", dressesDefault))
    }

    @Test
    fun unchangedDefaultTagIsNotMarkedModified() {
        val dresses = DefaultTags.tags.first { tag -> tag.id == "category-dresses" }
        val saved = dresses.copy(description = storedTagDescription(dressesDefault, dressesDefault))

        assertNull(saved.description)
        assertFalse(DefaultTags.isCustomOrModifiedDefault(saved))
    }

    @Test
    fun blankDescriptionIsStoredAsNull() {
        assertNull(storedTagDescription("   ", dressesDefault))
        assertNull(storedTagDescription("", null))
    }

    @Test
    fun editedDescriptionIsTrimmedAndStored() {
        assertEquals("a long evening gown", storedTagDescription("  a long evening gown ", dressesDefault))
        assertEquals("a long evening gown", storedTagDescription("  a long evening gown ", null))
    }
}
