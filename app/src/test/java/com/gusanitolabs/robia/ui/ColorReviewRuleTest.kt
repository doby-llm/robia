package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorReviewRuleTest {
    private val palette = setOf("black", "brown", "white")
    private val paletteWithBlue = setOf("black", "blue", "brown", "white")

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

    @Test
    fun blackDeletedAndBlueDetectedDropsTheOldBlueSecondary() {
        // Reported case: (Black, Blue) -> (Blue, Blue) before the fix. The old secondary equals the new primary, so it is dropped.
        val pair = colorReviewPair(
            proposedPrimaryId = "blue",
            currentPrimaryId = "black",
            proposedSecondaryId = null,
            currentSecondaryId = "blue",
            paletteIds = paletteWithBlue - "black",
            fallbackPrimaryId = "blue",
        )

        assertEquals(ColorReviewPair(primaryId = "blue", secondaryId = null), pair)
    }

    @Test
    fun brownDetectedAsSecondaryIsKeptWhenBlueBecomesPrimary() {
        val pair = colorReviewPair(
            proposedPrimaryId = "blue",
            currentPrimaryId = "black",
            proposedSecondaryId = "brown",
            currentSecondaryId = "blue",
            paletteIds = paletteWithBlue - "black",
            fallbackPrimaryId = "blue",
        )

        assertEquals(ColorReviewPair(primaryId = "blue", secondaryId = "brown"), pair)
    }

    @Test
    fun onlySecondaryChangesWhenPrimaryIsUnchanged() {
        val current = ClothingColorMetrics(primaryPaletteColorId = "black", secondaryPaletteColorId = "blue")
        val pair = colorReviewPair(
            proposedPrimaryId = "black",
            currentPrimaryId = "black",
            proposedSecondaryId = "brown",
            currentSecondaryId = "blue",
            paletteIds = paletteWithBlue,
            fallbackPrimaryId = "black",
        )

        assertEquals(ColorReviewPair(primaryId = "black", secondaryId = "brown"), pair)
        assertTrue(
            colorReviewNeedsProposal(
                currentPrimaryId = current.primaryPaletteColorId,
                currentSecondaryId = current.secondaryPaletteColorId,
                targetPrimaryId = pair.primaryId,
                targetSecondaryId = pair.secondaryId,
                paletteIds = paletteWithBlue,
            ),
        )
    }

    @Test
    fun blackRenamedGreyKeepsBlackAndBlue() {
        // The colour keeps its id when renamed, so the garment stays (black, blue) and is not listed.
        val pair = colorReviewPair(
            proposedPrimaryId = "black",
            currentPrimaryId = "black",
            proposedSecondaryId = "blue",
            currentSecondaryId = "blue",
            paletteIds = paletteWithBlue,
            fallbackPrimaryId = "black",
        )

        assertEquals(ColorReviewPair(primaryId = "black", secondaryId = "blue"), pair)
        assertFalse(
            colorReviewNeedsProposal(
                currentPrimaryId = "black",
                currentSecondaryId = "blue",
                targetPrimaryId = pair.primaryId,
                targetSecondaryId = pair.secondaryId,
                paletteIds = paletteWithBlue,
            ),
        )
    }

    @Test
    fun storedSecondaryIsKeptWhenAnalyzerIsSilentAndItDiffersFromPrimary() {
        val pair = colorReviewPair(
            proposedPrimaryId = "black",
            currentPrimaryId = "black",
            proposedSecondaryId = null,
            currentSecondaryId = "brown",
            paletteIds = paletteWithBlue,
            fallbackPrimaryId = "black",
        )

        assertEquals(ColorReviewPair(primaryId = "black", secondaryId = "brown"), pair)
    }

    @Test
    fun deletedSecondaryDroppedByPairCountsAsResolved() {
        // A deleted secondary with no analyzer replacement is dropped; that must not block Accept all.
        val current = ClothingColorMetrics(primaryPaletteColorId = "black", secondaryPaletteColorId = "yellow")
        val proposed = ClothingColorMetrics(primaryPaletteColorId = "black")

        assertTrue(colorReviewHasConcreteTarget(current, proposed, palette))
    }

    @Test
    fun pairNeverEqualsPrimaryAndAlwaysComesFromThePalette() {
        val ids = listOf(null, "black", "blue", "brown", "yellow")
        val paletteVariants = listOf(palette, paletteWithBlue, paletteWithBlue - "black", setOf("blue"))
        for (proposedPrimary in ids) for (currentPrimary in ids) for (proposedSecondary in ids) {
            for (currentSecondary in ids) for (paletteIds in paletteVariants) {
                val fallback = paletteIds.firstOrNull()
                val pair = colorReviewPair(
                    proposedPrimaryId = proposedPrimary,
                    currentPrimaryId = currentPrimary,
                    proposedSecondaryId = proposedSecondary,
                    currentSecondaryId = currentSecondary,
                    paletteIds = paletteIds,
                    fallbackPrimaryId = fallback,
                )
                val context = "primary=$proposedPrimary/$currentPrimary secondary=$proposedSecondary/$currentSecondary palette=$paletteIds"
                assertTrue(context, pair.primaryId == null || pair.primaryId in paletteIds)
                assertTrue(context, pair.secondaryId == null || pair.secondaryId in paletteIds)
                assertTrue(context, pair.primaryId == null || pair.primaryId != pair.secondaryId)
            }
        }
    }
}
