package com.gusanitolabs.robia.media

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure A4 layout for the garment PDF card (TS§9). Units are PDF points.
 * Page geometry mirrors PDF_PAGE_WIDTH / PDF_PAGE_HEIGHT in GarmentShareExporter.
 * No android.* types here: the exporter measures text and draws the boxes returned.
 */

data class PdfContent(
    val title: String,
    val notes: String,
    val metadata: List<Pair<String, List<String>>>,
    val colors: List<Pair<String, String>>,
)

data class PdfBox(val x: Float, val y: Float, val w: Float, val h: Float) {
    val bottom: Float get() = y + h
}

data class PdfTextBlock(
    val text: String,
    val box: PdfBox,
    val sizePt: Float,
    val firstLine: Int,
    val lineCount: Int,
)

data class PdfSwatch(
    val role: String,
    val name: String,
    val cell: PdfBox,
    val roleBox: PdfBox,
    val nameBox: PdfBox,
)

data class PdfMetadataRow(
    val label: String,
    val value: String,
    val box: PdfBox,
    val labelBox: PdfBox,
    val valueBox: PdfBox,
)

data class PdfPage(
    val photo: PdfBox?,
    val title: PdfTextBlock?,
    val notes: PdfTextBlock?,
    val swatches: List<PdfSwatch>,
    val metadata: List<PdfMetadataRow>,
    val footer: PdfBox,
)

data class PdfLayout(val pages: List<PdfPage>)

interface PdfTextMeasurer {
    fun lineCount(text: String, sizePt: Float, widthPt: Float): Int

    fun lineHeight(sizePt: Float): Float

    /** Width of one line of [text]; the image card sizes its chips with it. */
    fun widthOf(text: String, sizePt: Float): Float = text.length * sizePt * 0.5f
}

/** Title for the card: trimmed name, else first Category value, else the localized fallback. */
fun pdfTitle(name: String, categoryValues: List<String>, fallback: String): String =
    name.trim()
        .ifBlank { categoryValues.firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
        .ifBlank { fallback }

private const val PAGE_WIDTH = 595f
private const val PAGE_HEIGHT = 842f
private const val MARGIN = 44f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
private const val CONTENT_TOP = 74f
private const val FOOTER_HEIGHT = 56f
private const val FOOTER_TOP = PAGE_HEIGHT - FOOTER_HEIGHT
private const val CONTENT_BOTTOM = FOOTER_TOP - 24f
private const val PHOTO_MIN_HEIGHT = 430f
private const val PHOTO_MAX_HEIGHT = 600f
private const val PHOTO_FLOOR_HEIGHT = 300f
private const val PHOTO_INFO_GAP = 22f
private const val COLUMN_GAP = 28f
private const val LEFT_SHARE = 0.58f
private const val METADATA_INSET = 20f
private const val METADATA_PADDING = 6f
private const val METADATA_LABEL_SIZE = 8.5f
private const val METADATA_VALUE_SIZE = 13f
private const val METADATA_LABEL_GAP = 2f
private const val DIVIDER_HEIGHT = 1f
private const val MAX_METADATA_ROWS = 5
private const val TITLE_NOTES_GAP = 8f
private const val SWATCH_GAP = 16f
private const val SWATCH_SIZE = 28f
private const val SWATCH_TEXT_GAP = 8f
private const val SWATCH_SPACING = 14f
private const val SWATCH_ROLE_SIZE = 10f
private const val SWATCH_NAME_SIZE = 12.5f
private val TITLE_SIZES = (30 downTo 22).map { it.toFloat() }
private val NOTES_SIZES = (25 downTo 20).map { it / 2f }

/** Image card (EX1, REQ-EX-02): pixels, 3:4, long edge [CARD_HEIGHT]. */
const val CARD_WIDTH = 1200f
const val CARD_HEIGHT = 1600f
private const val CARD_MARGIN = 72f
private const val CARD_MARK_HEIGHT = 40f
private const val CARD_MARK_GAP = 40f
private const val CARD_PHOTO_MIN_HEIGHT = 640f
private const val CARD_INFO_GAP = 48f
private const val CARD_TITLE_SIZE = 56f
private const val CARD_LABEL_SIZE = 24f
private const val CARD_LABEL_GAP = 8f
private const val CARD_VALUE_SIZE = 34f
private const val CARD_NOTES_SIZE = 30f
private const val CARD_CHIP_SIZE = 30f
private const val CARD_CHIP_HEIGHT = 56f
private const val CARD_CHIP_PAD = 24f
private const val CARD_CHIP_GAP = 12f
private const val CARD_ROW_GAP = 28f

/**
 * Places the first page (photo, title, notes, swatches, metadata) and any notes that do not fit
 * on continuation pages. Fonts shrink first (title 30 → 22 pt, notes 12.5 → 10 pt); the photo
 * takes the leftover height (up to 600 pt), and the info block is bottom-anchored so the band
 * above the footer stays at 24 pt.
 */
fun computePdfLayout(content: PdfContent, measurer: PdfTextMeasurer): PdfLayout {
    val title = content.title.trim()
    val notes = content.notes.trim()
    val metadata = content.metadata
        .filter { (_, values) -> values.any(String::isNotBlank) }
        .take(MAX_METADATA_ROWS)
    val hasMetadata = metadata.isNotEmpty()
    val columnsWidth = CONTENT_WIDTH - COLUMN_GAP
    val leftWidth = if (hasMetadata) columnsWidth * LEFT_SHARE else CONTENT_WIDTH
    val rightWidth = if (hasMetadata) columnsWidth - leftWidth else 0f
    val rightHeight = metadata.map { (_, values) ->
        metadataRowHeight(metadataText(values), rightWidth - METADATA_INSET, measurer)
    }.sum()
    val swatchBlock = if (content.colors.isEmpty()) 0f else SWATCH_GAP + swatchRowHeight(content.colors, leftWidth, measurer)

    // Budget for the info block while the photo keeps its minimum height.
    val budget = CONTENT_BOTTOM - PHOTO_INFO_GAP - CONTENT_TOP - PHOTO_MIN_HEIGHT
    val fit = fitFirstPage(title, notes, leftWidth, swatchBlock, rightHeight, budget, measurer)

    val titleHeight = blockHeight(title, fit.titleSize, leftWidth, measurer)
    val notesFirstHeight = fit.notesOnFirstPage * measurer.lineHeight(fit.notesSize)
    val leftHeight = titleHeight +
        (if (fit.notesOnFirstPage > 0) TITLE_NOTES_GAP + notesFirstHeight else 0f) +
        swatchBlock
    val infoHeight = max(leftHeight, rightHeight)
    val photoHeight = (CONTENT_BOTTOM - PHOTO_INFO_GAP - CONTENT_TOP - infoHeight)
        .coerceIn(PHOTO_FLOOR_HEIGHT, PHOTO_MAX_HEIGHT)
    val photo = PdfBox(MARGIN, CONTENT_TOP, CONTENT_WIDTH, photoHeight)
    val infoTop = max(photo.bottom + PHOTO_INFO_GAP, CONTENT_BOTTOM - infoHeight)
    val footer = PdfBox(MARGIN, FOOTER_TOP, CONTENT_WIDTH, FOOTER_HEIGHT)

    var cursor = infoTop
    val titleLines = lineCount(title, fit.titleSize, leftWidth, measurer)
    val titleBlock = if (titleLines > 0) {
        PdfTextBlock(title, PdfBox(MARGIN, cursor, leftWidth, titleHeight), fit.titleSize, 0, titleLines)
    } else {
        null
    }
    cursor += titleHeight
    var notesBlock: PdfTextBlock? = null
    if (fit.notesOnFirstPage > 0) {
        cursor += TITLE_NOTES_GAP
        notesBlock = PdfTextBlock(
            notes,
            PdfBox(MARGIN, cursor, leftWidth, notesFirstHeight),
            fit.notesSize,
            0,
            fit.notesOnFirstPage,
        )
        cursor += notesFirstHeight
    }
    val swatches: List<PdfSwatch> = if (content.colors.isEmpty()) {
        emptyList()
    } else {
        swatchCells(content.colors, cursor + SWATCH_GAP, leftWidth, measurer)
    }

    val rightX = MARGIN + leftWidth + COLUMN_GAP
    val valueWidth = rightWidth - METADATA_INSET
    val rows = mutableListOf<PdfMetadataRow>()
    var rowTop = infoTop
    metadata.forEach { (label, values) ->
        val valueText = metadataText(values)
        val valueLines = max(1, measurer.lineCount(valueText, METADATA_VALUE_SIZE, valueWidth))
        val labelHeight = measurer.lineHeight(METADATA_LABEL_SIZE)
        val valueHeight = valueLines * measurer.lineHeight(METADATA_VALUE_SIZE)
        val rowHeight = metadataRowHeight(valueText, valueWidth, measurer)
        rows += PdfMetadataRow(
            label = label,
            value = valueText,
            box = PdfBox(rightX, rowTop, rightWidth, rowHeight),
            labelBox = PdfBox(rightX + METADATA_INSET, rowTop + METADATA_PADDING, valueWidth, labelHeight),
            valueBox = PdfBox(
                rightX + METADATA_INSET,
                rowTop + METADATA_PADDING + labelHeight + METADATA_LABEL_GAP,
                valueWidth,
                valueHeight,
            ),
        )
        rowTop += rowHeight
    }

    val pages = mutableListOf(
        PdfPage(
            photo = photo,
            title = titleBlock,
            notes = notesBlock,
            swatches = swatches,
            metadata = rows,
            footer = footer,
        ),
    )

    // Notes that did not fit continue on A4 pages with the same header and footer.
    val totalNoteLines = lineCount(notes, fit.notesSize, leftWidth, measurer)
    val noteLineHeight = measurer.lineHeight(fit.notesSize)
    val linesPerPage = max(1, floor((CONTENT_BOTTOM - CONTENT_TOP) / noteLineHeight).toInt())
    var nextLine = fit.notesOnFirstPage
    while (nextLine < totalNoteLines) {
        val count = min(linesPerPage, totalNoteLines - nextLine)
        pages += PdfPage(
            photo = null,
            title = null,
            notes = PdfTextBlock(
                notes,
                PdfBox(MARGIN, CONTENT_TOP, leftWidth, count * noteLineHeight),
                fit.notesSize,
                nextLine,
                count,
            ),
            swatches = emptyList(),
            metadata = emptyList(),
            footer = footer,
        )
        nextLine += count
    }
    return PdfLayout(pages)
}

private class TextFit(val titleSize: Float, val notesSize: Float, val notesOnFirstPage: Int)

private fun fitFirstPage(
    title: String,
    notes: String,
    leftWidth: Float,
    swatchBlock: Float,
    rightHeight: Float,
    budget: Float,
    measurer: PdfTextMeasurer,
): TextFit {
    for (titleSize in TITLE_SIZES) {
        val titleHeight = blockHeight(title, titleSize, leftWidth, measurer)
        for (notesSize in NOTES_SIZES) {
            val notesLines = lineCount(notes, notesSize, leftWidth, measurer)
            val notesHeight = notesLines * measurer.lineHeight(notesSize)
            val leftHeight = titleHeight +
                (if (notesLines > 0) TITLE_NOTES_GAP + notesHeight else 0f) +
                swatchBlock
            if (max(leftHeight, rightHeight) <= budget) {
                return TextFit(titleSize, notesSize, notesLines)
            }
        }
    }
    // Smallest sizes still overflow: keep as many note lines on page 1 as the budget allows.
    val titleSize = TITLE_SIZES.last()
    val notesSize = NOTES_SIZES.last()
    val titleHeight = blockHeight(title, titleSize, leftWidth, measurer)
    val spare = budget - titleHeight - swatchBlock - TITLE_NOTES_GAP
    val totalLines = lineCount(notes, notesSize, leftWidth, measurer)
    val lines = floor(spare / measurer.lineHeight(notesSize)).toInt().coerceIn(0, totalLines)
    return TextFit(titleSize, notesSize, lines)
}

private fun swatchRowHeight(colors: List<Pair<String, String>>, leftWidth: Float, measurer: PdfTextMeasurer): Float {
    val textWidth = swatchCellWidth(colors.size, leftWidth) - SWATCH_SIZE - SWATCH_TEXT_GAP
    val tallestText = colors.maxOf { (_, name) ->
        measurer.lineHeight(SWATCH_ROLE_SIZE) + blockHeight(name, SWATCH_NAME_SIZE, textWidth, measurer)
    }
    return max(SWATCH_SIZE, tallestText)
}

private fun swatchCells(
    colors: List<Pair<String, String>>,
    top: Float,
    leftWidth: Float,
    measurer: PdfTextMeasurer,
): List<PdfSwatch> {
    val cellWidth = swatchCellWidth(colors.size, leftWidth)
    val textWidth = cellWidth - SWATCH_SIZE - SWATCH_TEXT_GAP
    val rowHeight = swatchRowHeight(colors, leftWidth, measurer)
    val roleHeight = measurer.lineHeight(SWATCH_ROLE_SIZE)
    return colors.mapIndexed { index, (role, name) ->
        val cellX = MARGIN + index * (cellWidth + SWATCH_SPACING)
        val textX = cellX + SWATCH_SIZE + SWATCH_TEXT_GAP
        PdfSwatch(
            role = role,
            name = name,
            cell = PdfBox(cellX, top, cellWidth, rowHeight),
            roleBox = PdfBox(textX, top, textWidth, roleHeight),
            nameBox = PdfBox(
                textX,
                top + roleHeight,
                textWidth,
                blockHeight(name, SWATCH_NAME_SIZE, textWidth, measurer),
            ),
        )
    }
}

private fun swatchCellWidth(count: Int, leftWidth: Float): Float =
    (leftWidth - SWATCH_SPACING * (count - 1)) / count

private fun metadataText(values: List<String>): String =
    values.filter(String::isNotBlank).joinToString(", ")

private fun metadataRowHeight(valueText: String, valueWidth: Float, measurer: PdfTextMeasurer): Float =
    METADATA_PADDING * 2 +
        measurer.lineHeight(METADATA_LABEL_SIZE) +
        METADATA_LABEL_GAP +
        max(1, measurer.lineCount(valueText, METADATA_VALUE_SIZE, valueWidth)) * measurer.lineHeight(METADATA_VALUE_SIZE) +
        DIVIDER_HEIGHT

private fun lineCount(text: String, sizePt: Float, widthPt: Float, measurer: PdfTextMeasurer): Int =
    if (text.isBlank()) 0 else measurer.lineCount(text, sizePt, widthPt)

private fun blockHeight(text: String, sizePt: Float, widthPt: Float, measurer: PdfTextMeasurer): Float =
    lineCount(text, sizePt, widthPt, measurer) * measurer.lineHeight(sizePt)

/** One labelled group on the card. Chip sections draw one chip per value; text sections join their values. */
data class CardSection(
    val label: String,
    val values: List<String>,
    val chips: Boolean,
    /** Chip fills by index for colour chips; null or missing entries use the neutral chip colour. */
    val swatchHex: List<String?> = emptyList(),
)

data class CardContent(
    val title: String,
    val sections: List<CardSection>,
    val notes: String,
)

data class CardSectionLayout(
    val section: CardSection,
    val labelBox: PdfBox,
    /** One box per chip for chip sections, a single box for text sections. */
    val boxes: List<PdfBox>,
)

data class CardLayout(
    val photo: PdfBox,
    val title: PdfTextBlock?,
    val sections: List<CardSectionLayout>,
    val notes: PdfTextBlock?,
    val mark: PdfBox,
)

/**
 * Places the image card in [CARD_WIDTH] x [CARD_HEIGHT] pixels: photo on top, then the title, the chosen sections and the
 * notes, bottom-anchored above the Robia mark. The photo takes what the text leaves and keeps [CARD_PHOTO_MIN_HEIGHT];
 * notes that do not fit in the remaining space are cut. Empty sections are omitted, so the text block grows with the
 * chosen fields only.
 */
fun computeCardLayout(content: CardContent, measurer: PdfTextMeasurer): CardLayout {
    val width = CARD_WIDTH - 2 * CARD_MARGIN
    val title = content.title.trim()
    val sections = content.sections.filter { it.values.isNotEmpty() }
    val notes = content.notes.trim()
    val contentBottom = CARD_HEIGHT - CARD_MARGIN - CARD_MARK_HEIGHT - CARD_MARK_GAP

    val titleHeight = blockHeight(title, CARD_TITLE_SIZE, width, measurer)
    val sectionHeights = sections.map { section -> cardSectionHeight(section, width, measurer) }
    val fixedHeight = (if (title.isEmpty()) 0f else titleHeight + CARD_ROW_GAP) +
        sectionHeights.fold(0f) { total, height -> total + height + CARD_ROW_GAP }
    val noteLineHeight = measurer.lineHeight(CARD_NOTES_SIZE)
    val budget = contentBottom - CARD_MARGIN - CARD_INFO_GAP - CARD_PHOTO_MIN_HEIGHT
    val noteLines = if (notes.isEmpty()) {
        0
    } else {
        floor((budget - fixedHeight) / noteLineHeight).toInt()
            .coerceIn(0, measurer.lineCount(notes, CARD_NOTES_SIZE, width))
    }
    val notesHeight = noteLines * noteLineHeight
    val infoTop = contentBottom - fixedHeight - notesHeight
    val photo = PdfBox(CARD_MARGIN, CARD_MARGIN, width, infoTop - CARD_INFO_GAP - CARD_MARGIN)

    var cursor = infoTop
    val titleBlock = if (title.isEmpty()) {
        null
    } else {
        PdfTextBlock(
            title,
            PdfBox(CARD_MARGIN, cursor, width, titleHeight),
            CARD_TITLE_SIZE,
            0,
            measurer.lineCount(title, CARD_TITLE_SIZE, width),
        ).also { cursor += titleHeight + CARD_ROW_GAP }
    }
    val sectionLayouts = sections.mapIndexed { index, section ->
        val labelHeight = measurer.lineHeight(CARD_LABEL_SIZE)
        val labelBox = PdfBox(CARD_MARGIN, cursor, width, labelHeight)
        val top = cursor + labelHeight + CARD_LABEL_GAP
        val boxes = if (section.chips) {
            placeChips(section.values, top, width, measurer)
        } else {
            val lines = measurer.lineCount(section.values.joinToString(", "), CARD_VALUE_SIZE, width)
            listOf(PdfBox(CARD_MARGIN, top, width, lines * measurer.lineHeight(CARD_VALUE_SIZE)))
        }
        cursor += sectionHeights[index] + CARD_ROW_GAP
        CardSectionLayout(section, labelBox, boxes)
    }
    val notesBlock = if (noteLines == 0) {
        null
    } else {
        PdfTextBlock(notes, PdfBox(CARD_MARGIN, cursor, width, notesHeight), CARD_NOTES_SIZE, 0, noteLines)
    }
    val mark = PdfBox(CARD_MARGIN, contentBottom + CARD_MARK_GAP, width, CARD_MARK_HEIGHT)
    return CardLayout(photo, titleBlock, sectionLayouts, notesBlock, mark)
}

private fun cardSectionHeight(section: CardSection, width: Float, measurer: PdfTextMeasurer): Float {
    val labelHeight = measurer.lineHeight(CARD_LABEL_SIZE)
    val contentHeight = if (section.chips) {
        placeChips(section.values, 0f, width, measurer).maxOfOrNull { it.bottom } ?: 0f
    } else {
        measurer.lineCount(section.values.joinToString(", "), CARD_VALUE_SIZE, width) * measurer.lineHeight(CARD_VALUE_SIZE)
    }
    return labelHeight + CARD_LABEL_GAP + contentHeight
}

/** Chips wrap to the next row when the next one does not fit; returns their boxes starting at [top]. */
private fun placeChips(values: List<String>, top: Float, width: Float, measurer: PdfTextMeasurer): List<PdfBox> {
    var x = CARD_MARGIN
    var y = top
    return values.map { value ->
        val chipWidth = (measurer.widthOf(value, CARD_CHIP_SIZE) + 2 * CARD_CHIP_PAD).coerceAtMost(width)
        if (x > CARD_MARGIN && x + chipWidth > CARD_MARGIN + width) {
            x = CARD_MARGIN
            y += CARD_CHIP_HEIGHT + CARD_CHIP_GAP
        }
        PdfBox(x, y, chipWidth, CARD_CHIP_HEIGHT).also { x += chipWidth + CARD_CHIP_GAP }
    }
}
