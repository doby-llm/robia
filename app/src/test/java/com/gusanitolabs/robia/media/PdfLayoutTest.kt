package com.gusanitolabs.robia.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class PdfLayoutTest {
    /** Chars per line = width / (0.5 × size), so lines = ceil(chars × 0.5 × size / width). */
    private val measurer = object : PdfTextMeasurer {
        override fun lineCount(text: String, sizePt: Float, widthPt: Float): Int =
            ceil(text.length * 0.5f * sizePt / widthPt).toInt()

        override fun lineHeight(sizePt: Float): Float = sizePt * 1.25f
    }

    private val typical = PdfContent(
        title = "Burgundy wrap dress for summer evenings",
        notes = "n".repeat(250),
        metadata = listOf(
            "Category" to listOf("Dresses"),
            "Season" to listOf("Summer"),
            "Occasions" to listOf("Dressed-up", "Everyday", "Work"),
            "Fit" to listOf("Good fit"),
        ),
        colors = listOf("Primary" to "Burgundy", "Secondary" to "Cream"),
    )

    @Test
    fun typicalGarment_fitsOnOnePageInsideMarginsAndFooter() {
        val layout = computePdfLayout(typical, measurer)

        assertEquals(1, layout.pages.size)
        val page = layout.pages.single()
        val notes = page.notes!!
        assertEquals(measurer.lineCount(typical.notes, notes.sizePt, notes.box.w), notes.lineCount)
        assertInsideMarginsAndFooter(page)
        assertNoBandAboveFooter(page)
    }

    @Test
    fun blankNotes_swatchesStartWithin24PtOfTitle() {
        val page = computePdfLayout(typical.copy(notes = ""), measurer).pages.single()

        assertNull(page.notes)
        val titleBottom = page.title!!.box.bottom
        assertTrue("swatches start ${page.swatches.first().cell.y - titleBottom} pt below the title", page.swatches.first().cell.y - titleBottom <= 24f)
    }

    @Test
    fun longTitleAndNotes_overflowContinuesOnSecondPageWithoutLosingLines() {
        val content = typical.copy(title = "t".repeat(200), notes = "n".repeat(2000))
        val layout = computePdfLayout(content, measurer)

        assertTrue("notes should continue on page 2", layout.pages.size >= 2)
        layout.pages.forEach { assertInsideMarginsAndFooter(it) }
        assertTrue(layout.pages.first().title!!.sizePt == 22f)

        val blocks = layout.pages.mapNotNull { it.notes }
        var nextLine = 0
        blocks.forEach { block ->
            assertEquals(nextLine, block.firstLine)
            nextLine += block.lineCount
        }
        assertEquals(measurer.lineCount(content.notes, blocks.first().sizePt, blocks.first().box.w), nextLine)
    }

    @Test
    fun categoryOnly_titleIsCategoryAndNoNotesBlock() {
        val content = PdfContent(
            title = pdfTitle(name = "", categoryValues = listOf("Dresses"), fallback = "Garment"),
            notes = "",
            metadata = listOf("Category" to listOf("Dresses")),
            colors = emptyList(),
        )
        val page = computePdfLayout(content, measurer).pages.single()

        assertEquals("Dresses", content.title)
        assertNull(page.notes)
        assertInsideMarginsAndFooter(page)
        assertNoBandAboveFooter(page)
    }

    @Test
    fun noNameNoCategory_titleIsFallbackAndNameWins() {
        assertEquals("Garment", pdfTitle(name = "   ", categoryValues = emptyList(), fallback = "Garment"))
        assertEquals("Garment", pdfTitle(name = "", categoryValues = listOf(" "), fallback = "Garment"))
        assertEquals("Blue shirt", pdfTitle(name = "  Blue shirt ", categoryValues = listOf("Shirts"), fallback = "Garment"))
    }

    @Test
    fun noNotes_photoGrowsAboveMinimumAndNoNotesBlock() {
        val page = computePdfLayout(typical.copy(notes = ""), measurer).pages.single()

        assertNull(page.notes)
        val photo = page.photo!!
        assertTrue("photo height ${photo.h} should exceed 430 pt", photo.h > 430f)
        assertInsideMarginsAndFooter(page)
    }

    @Test
    fun noColours_noSwatchRow() {
        val page = computePdfLayout(typical.copy(colors = emptyList()), measurer).pages.single()

        assertTrue(page.swatches.isEmpty())
        assertInsideMarginsAndFooter(page)
        assertNoBandAboveFooter(page)
    }

    @Test
    fun nothingButPhoto_textUsesFullWidthPhotoGrowsToCapAndNoBandAboveFooter() {
        val content = PdfContent(title = "Garment", notes = "", metadata = emptyList(), colors = emptyList())
        val page = computePdfLayout(content, measurer).pages.single()

        assertTrue(page.metadata.isEmpty())
        assertTrue(page.title!!.box.w == 507f)
        assertTrue(page.photo!!.h == 600f)
        assertInsideMarginsAndFooter(page)
        assertNoBandAboveFooter(page)
    }

    @Test
    fun metadataRowsWithoutValues_areOmittedAndOrderIsKept() {
        val content = typical.copy(
            metadata = listOf(
                "Category" to listOf("Dresses"),
                "Season" to emptyList(),
                "Occasions" to listOf(" "),
                "Fit" to listOf("Good fit"),
            ),
        )
        val page = computePdfLayout(content, measurer).pages.single()

        assertEquals(listOf("Category", "Fit"), page.metadata.map { it.label })
    }

    /** V-EX1-2: the PDF shows only the fields that were chosen; a field with no value leaves no row. */
    @Test
    fun chosenLocation_isOnTheSecondRowAfterFitAndBlankRowsStayOut() {
        val content = typical.copy(
            metadata = listOf(
                "Category" to listOf("Dresses"),
                "Fit" to emptyList(),
                "Location" to listOf("Wardrobe 2"),
            ),
            notes = "",
        )
        val page = computePdfLayout(content, measurer).pages.single()

        assertEquals(listOf("Category", "Location"), page.metadata.map { it.label })
        assertNull(page.notes)
        assertInsideMarginsAndFooter(page)
    }

    @Test
    fun allMetadataRowsChosen_fitInsideMarginsAndFooter() {
        val content = typical.copy(
            metadata = listOf(
                "Category" to listOf("Dresses"),
                "Season" to listOf("Summer"),
                "Occasions" to listOf("Dressed-up", "Everyday", "Work"),
                "Fit" to listOf("Good fit"),
                "Location" to listOf("Wardrobe 2"),
            ),
        )
        val page = computePdfLayout(content, measurer).pages.single()

        assertEquals(5, page.metadata.size)
        assertInsideMarginsAndFooter(page)
        assertNoBandAboveFooter(page)
    }

    /** V-EX1-3: the image card is 1200 x 1600 px (long edge 1600) whatever the fields. */
    @Test
    fun imageCard_isPortraitWithLongEdge1600() {
        assertEquals(1600f, CARD_HEIGHT, 0f)
        assertEquals(1200f, CARD_WIDTH, 0f)
        val card = computeCardLayout(cardContent(sections = emptyList()), measurer)

        assertTrue(card.photo.bottom <= card.mark.y)
        assertEquals(CARD_HEIGHT - 72f, card.mark.bottom, 0f)
    }

    /** V-EX1-3: the field block grows with the chosen fields only; the photo gives up the difference. */
    @Test
    fun fieldBlock_growsWithChosenFieldsOnly() {
        val none = computeCardLayout(cardContent(sections = emptyList()), measurer)
        val two = computeCardLayout(cardContent(sections = cardSections(2)), measurer)
        val four = computeCardLayout(cardContent(sections = cardSections(4)), measurer)
        val withBlank = computeCardLayout(
            cardContent(sections = cardSections(2) + CardSection("Notes", emptyList(), chips = false)),
            measurer,
        )

        assertTrue(two.photo.h < none.photo.h)
        assertTrue(four.photo.h < two.photo.h)
        assertEquals(two.photo.h, withBlank.photo.h, 0f)
        assertEquals(2, withBlank.sections.size)
    }

    @Test
    fun cardBoxes_stayInsideCardAndBelowThePhoto() {
        val card = computeCardLayout(cardContent(sections = cardSections(4), notes = "n".repeat(300)), measurer)

        val texts = listOfNotNull(card.title?.box, card.notes?.box) + card.sections.flatMap { it.boxes + it.labelBox }
        texts.forEach { box ->
            assertTrue("box $box is outside the card margins", box.x >= 72f && box.x + box.w <= 1128f)
            assertTrue("box $box overlaps the photo", box.y >= card.photo.bottom)
            assertTrue("box $box overlaps the mark", box.bottom <= card.mark.y)
        }
    }

    @Test
    fun longNotes_areCutToKeepTheMinimumPhoto() {
        val card = computeCardLayout(cardContent(sections = cardSections(4), notes = "n".repeat(20_000)), measurer)

        assertTrue("photo ${card.photo.h} keeps 640 px", card.photo.h >= 640f)
        assertTrue(card.notes!!.lineCount < measurer.lineCount("n".repeat(20_000), 30f, 1056f))
    }

    @Test
    fun chipsWrapWithinTheCardWidth() {
        val values = List(12) { "Occasion $it" }
        val card = computeCardLayout(cardContent(sections = listOf(CardSection("Occasions", values, chips = true))), measurer)

        val chips = card.sections.single().boxes
        assertEquals(12, chips.size)
        assertTrue("chips should take more than one row", chips.map { it.y }.distinct().size > 1)
        chips.forEach { assertTrue(it.x + it.w <= 1128f) }
    }

    private fun cardSections(count: Int): List<CardSection> = listOf(
        CardSection("Category", listOf("Dresses"), chips = false),
        CardSection("Colors", listOf("Burgundy", "Cream"), chips = true, swatchHex = listOf("#800020", "#FFFDD0")),
        CardSection("Occasions", listOf("Dressed-up", "Everyday"), chips = true),
        CardSection("Fit", listOf("Good fit"), chips = false),
    ).take(count)

    private fun cardContent(sections: List<CardSection>, notes: String = ""): CardContent =
        CardContent(title = "Burgundy wrap dress", sections = sections, notes = notes)

    private fun boxesOf(page: PdfPage): List<PdfBox> {
        val boxes = mutableListOf<PdfBox>()
        page.photo?.let { boxes += it }
        page.title?.let { boxes += it.box }
        page.notes?.let { boxes += it.box }
        page.swatches.forEach { boxes += it.cell }
        page.metadata.forEach { boxes += it.box }
        return boxes
    }

    private fun assertInsideMarginsAndFooter(page: PdfPage) {
        boxesOf(page).forEach { box ->
            assertTrue("box $box is outside the 44 pt margins", box.x >= 44f && box.x + box.w <= 551f)
            assertTrue("box $box overlaps the footer", box.bottom <= page.footer.y)
        }
    }

    private fun assertNoBandAboveFooter(page: PdfPage) {
        val contentBottom = boxesOf(page).maxOf { it.bottom }
        val band = page.footer.y - contentBottom
        assertTrue("empty band of $band pt above the footer", band <= 40f)
    }
}
