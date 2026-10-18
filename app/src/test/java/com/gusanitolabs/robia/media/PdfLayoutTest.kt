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
