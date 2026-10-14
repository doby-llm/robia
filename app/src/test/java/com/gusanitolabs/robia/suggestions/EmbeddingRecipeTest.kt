package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.GarmentTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddingRecipeTest {
    @Test
    fun hasOneDescriptionPerDefaultCategoryAndOccasion() {
        val defaultIds = DefaultTags.tags
            .map(GarmentTag::id)
            .filter { it.startsWith("category-") || it.startsWith("occasion-") }

        assertEquals(25, EmbeddingRecipe.DEFAULT_LABEL_DESCRIPTIONS.size)
        assertEquals(defaultIds.toSet(), EmbeddingRecipe.DEFAULT_LABEL_DESCRIPTIONS.keys)
    }

    @Test
    fun defaultTagUsesBundledDescriptionWhenNotRenamed() {
        val tag = DefaultTags.tags.first { it.id == "category-dresses" }

        assertEquals(
            EmbeddingRecipe.CLASSIFICATION_PREFIX + "a dress: one-piece garment with a bodice and a skirt",
            labelTextFor(tag),
        )
    }

    @Test
    fun renamedDefaultUsesItsStoredName() {
        val renamed = GarmentTag(id = "category-dresses", categoryId = "category", name = "Vestidos", sortOrder = 50)

        assertEquals(EmbeddingRecipe.CLASSIFICATION_PREFIX + "Vestidos", labelTextFor(renamed))
    }

    @Test
    fun customTagUsesItsDescriptionThenItsName() {
        val described = GarmentTag(id = "category-polos", categoryId = "category", name = "Polos", sortOrder = 0, description = "polo shirt with a collar")
        val blankDescription = GarmentTag(id = "category-polos", categoryId = "category", name = "Polos", sortOrder = 0, description = "  ")

        assertEquals(EmbeddingRecipe.CLASSIFICATION_PREFIX + "polo shirt with a collar", labelTextFor(described))
        assertEquals(EmbeddingRecipe.CLASSIFICATION_PREFIX + "Polos", labelTextFor(blankDescription))
    }

    @Test
    fun garmentTextInputJoinsNonBlankNameAndNotes() {
        assertEquals(
            EmbeddingRecipe.DOCUMENT_PREFIX + "Red summer dress. linen",
            garmentTextInput(" Red summer dress ", "linen "),
        )
        assertEquals(EmbeddingRecipe.DOCUMENT_PREFIX + "notes only", garmentTextInput("  ", "notes only"))
        assertNull(garmentTextInput("", "  "))
    }
}
