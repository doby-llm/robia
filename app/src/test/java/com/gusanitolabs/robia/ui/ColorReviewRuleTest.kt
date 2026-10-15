package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorReviewRuleTest {
    private val palette = setOf("black", "brown", "white")

    @Test
    fun unchangedGarmentIsNotListed() {
        // The reported case: Black, Brown -> Black, Brown after deleting Yellow.
        assertFalse(
            colorReviewNeedsProposal(
                currentPrimaryId = "black",
                currentSecondaryId = "brown",
                targetPrimaryId = "black",
                targetSecondaryId = "brown",
                paletteIds = palette,
            ),
        )
    }

    @Test
    fun garmentWhoseStoredColourIsDeletedIsListed() {
        assertTrue(
            colorReviewNeedsProposal(
                currentPrimaryId = "black",
                currentSecondaryId = "yellow",
                targetPrimaryId = "black",
                targetSecondaryId = null,
                paletteIds = palette,
            ),
        )
    }

    @Test
    fun changedTargetIsListed() {
        assertTrue(
            colorReviewNeedsProposal(
                currentPrimaryId = "black",
                currentSecondaryId = null,
                targetPrimaryId = "white",
                targetSecondaryId = null,
                paletteIds = palette,
            ),
        )
    }

    @Test
    fun weakNullSecondaryWithoutStoredSecondaryIsNotAnOpRow() {
        // The analyzer found no secondary and the garment has none: nothing to change.
        val secondary = colorReviewTargetId(
            proposedId = null,
            currentId = null,
            paletteIds = palette,
            fallbackId = null,
        )
        assertNull(secondary)
        assertFalse(
            colorReviewNeedsProposal(
                currentPrimaryId = "black",
                currentSecondaryId = null,
                targetPrimaryId = "black",
                targetSecondaryId = secondary,
                paletteIds = palette,
            ),
        )
    }

    @Test
    fun missingProposalKeepsStoredSecondaryWhenItIsStillInThePalette() {
        // A weak analyzer result (null) must not clear a secondary that is still valid.
        val secondary = colorReviewTargetId(
            proposedId = null,
            currentId = "brown",
            paletteIds = palette,
            fallbackId = null,
        )
        assertEquals("brown", secondary)
        assertFalse(
            colorReviewNeedsProposal(
                currentPrimaryId = "black",
                currentSecondaryId = "brown",
                targetPrimaryId = "black",
                targetSecondaryId = secondary,
                paletteIds = palette,
            ),
        )
    }

    @Test
    fun deletedPrimaryWithoutAnalyzerTargetNeedsManualChoice() {
        val current = ClothingColorMetrics(primaryPaletteColorId = "yellow")
        val proposed = ClothingColorMetrics()

        assertFalse(colorReviewHasConcreteTarget(current, proposed, palette))
    }

    @Test
    fun deletedPrimaryWithAnalyzerTargetIsConcrete() {
        val current = ClothingColorMetrics(primaryPaletteColorId = "yellow")
        val proposed = ClothingColorMetrics(primaryPaletteColorId = "white")

        assertTrue(colorReviewHasConcreteTarget(current, proposed, palette))
        assertEquals(
            "white",
            colorReviewTargetId(
                proposedId = "white",
                currentId = "yellow",
                paletteIds = palette,
                fallbackId = "black",
            ),
        )
    }

    @Test
    fun deletedPrimaryWithoutAnalyzerTargetFallsBackOnlyForManualChoice() {
        assertEquals(
            "black",
            colorReviewTargetId(
                proposedId = null,
                currentId = "yellow",
                paletteIds = palette,
                fallbackId = "black",
            ),
        )
        assertNull(
            colorReviewTargetId(
                proposedId = null,
                currentId = null,
                paletteIds = palette,
                fallbackId = "black",
            ),
        )
    }
}
