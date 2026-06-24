package com.gusanitolabs.robia.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagDisplayPolicyTest {
    @Test
    fun defaultTagsUseLocalizedResourcePathUntilRenamed() {
        val canonicalTag = GarmentTag(id = "category-tops", categoryId = "category", name = "Tops", sortOrder = 100)
        val renamedTag = canonicalTag.copy(name = "apuntar")

        assertFalse(TagDisplayPolicy.shouldUseStoredName(canonicalTag))
        assertTrue(TagDisplayPolicy.shouldUseStoredName(renamedTag))
    }

    @Test
    fun customTagsAlwaysUseStoredName() {
        val customTag = GarmentTag(id = "category-apuntar-custom", categoryId = "category", name = "apuntar", sortOrder = 210)

        assertTrue(TagDisplayPolicy.shouldUseStoredName(customTag))
    }
}
