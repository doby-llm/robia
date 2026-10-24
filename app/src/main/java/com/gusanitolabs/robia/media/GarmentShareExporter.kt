package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.ExportField
import java.io.File
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min

object GarmentShareExporter {
    private const val SHARE_DIRECTORY = "robia_shares"
    private const val ROBIA_LOGO_ASSET = "robia_logo.png"
    private const val PDF_PAGE_WIDTH = 595
    private const val PDF_PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 44f
    private const val HEADER_BASELINE = PAGE_MARGIN + 9f
    private const val PHOTO_RADIUS = 24f
    private const val PHOTO_FIT_SHARE = 0.92f
    private const val SWATCH_RADIUS = 14f
    private const val FOOTER_LOGO_HEIGHT = 24f
    private val paper = Color.rgb(251, 250, 248)
    private val photoBackdrop = Color.rgb(243, 241, 238)
    private val fallbackAccent = Color.rgb(181, 162, 136)
    private val ink = Color.rgb(29, 16, 13)
    private val body = Color.rgb(79, 68, 66)
    private val muted = Color.rgb(129, 116, 114)
    private val rule = Color.rgb(227, 226, 224)
    private val rowRule = Color.rgb(238, 236, 235)
    private val chipFill = Color.rgb(241, 220, 201)
    private const val CARD_LABEL_LETTER_SIZE = 24f
    private const val CARD_VALUE_LETTER_SIZE = 34f
    private const val CARD_CHIP_LETTER_SIZE = 30f
    private const val CARD_CHIP_TEXT_PAD = 24f

    /** The share file for the image card (PNG, [CARD_WIDTH] x [CARD_HEIGHT]) with the chosen [fields]. */
    fun createShareCard(
        context: Context,
        item: GarmentShareItem,
        fields: Set<ExportField>,
    ): Uri {
        val bitmap = renderShareCard(context, item, fields)
        return bitmap.use { card ->
            val file = shareFile(context, item.name, "png")
            file.outputStream().use { output ->
                check(card.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode share image" }
            }
            contentUriFor(context, file)
        }
    }

    /** The image card in memory, for the in-screen preview and for [createShareCard]. The caller recycles it. */
    fun renderShareCard(
        context: Context,
        item: GarmentShareItem,
        fields: Set<ExportField>,
    ): Bitmap {
        val source = decodeGarmentImage(context, item.imageUri)
        val logo = context.assets.open(ROBIA_LOGO_ASSET).use(BitmapFactory::decodeStream)
        val fonts = PdfFonts.load(context)
        val swatches = if (ExportField.Colors in fields) item.pdfSwatchColors() else emptyList()
        val layout = computeCardLayout(
            content = item.cardContent(context, fields, swatches),
            measurer = PdfPaintMeasurer(fonts),
        )
        val card = Bitmap.createBitmap(CARD_WIDTH.toInt(), CARD_HEIGHT.toInt(), Bitmap.Config.ARGB_8888)
        try {
            drawCard(
                canvas = Canvas(card),
                layout = layout,
                photo = source,
                logo = logo,
                accent = item.primaryColor.hex?.toAndroidColorOrNull(),
                fonts = fonts,
            )
        } finally {
            source.recycle()
            logo.recycle()
        }
        return card
    }

    /** Page 1 of a PDF made by [createSharePdf], rendered for the in-screen preview. The caller recycles it. */
    fun renderPdfPreview(context: Context, pdfUri: Uri): Bitmap =
        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        } ?: error("Unable to open PDF preview")

    /** The PDF card with the chosen [fields]: the existing A4 layout fed only with what is chosen. */
    fun createSharePdf(
        context: Context,
        item: GarmentShareItem,
        fields: Set<ExportField>,
    ): Uri {
        val bitmap = decodeGarmentImage(context, item.imageUri)
        return bitmap.use { source ->
            val logo = context.assets.open(ROBIA_LOGO_ASSET).use(BitmapFactory::decodeStream)
            val fonts = PdfFonts.load(context)
            val swatches = if (ExportField.Colors in fields) item.pdfSwatchColors() else emptyList()
            val layout = computePdfLayout(
                content = item.pdfContent(context, fields, swatches),
                measurer = PdfPaintMeasurer(fonts),
            )
            val document = PdfDocument()
            layout.pages.forEachIndexed { index, pageLayout ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, index + 1).create())
                drawPdfPage(
                    canvas = page.canvas,
                    page = pageLayout,
                    photo = source,
                    logo = logo,
                    accent = item.primaryColor.hex?.toAndroidColorOrNull(),
                    swatchColors = swatches,
                    fonts = fonts,
                )
                document.finishPage(page)
            }

            val file = shareFile(context, item.name, "pdf")
            file.outputStream().use { output -> document.writeTo(output) }
            document.close()
            logo.recycle()
            contentUriFor(context, file)
        }
    }

    private fun decodeGarmentImage(context: Context, uri: Uri): Bitmap =
        context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: error("Unable to open garment image")

    /** Rows for the fields that are chosen and have a value, in card order: Category, Season, Occasion, Fit, Location. */
    private fun GarmentShareItem.shareRows(context: Context, fields: Set<ExportField>): List<ShareRow> {
        val metadataRows = listOf(
            Triple(ExportField.Category, GarmentShareMetadataIcon.Category, false),
            Triple(ExportField.Seasons, GarmentShareMetadataIcon.Season, true),
            Triple(ExportField.Occasions, GarmentShareMetadataIcon.Occasion, true),
            Triple(ExportField.Fit, GarmentShareMetadataIcon.Fit, false),
        ).mapNotNull { (field, icon, chips) ->
            val row = metadata.firstOrNull { it.icon == icon } ?: return@mapNotNull null
            ShareRow(field, row.label, row.values.filter(String::isNotBlank), chips)
        }
        val locationRow = ShareRow(
            ExportField.Location,
            context.getString(R.string.metadata_location),
            location.filter(String::isNotBlank),
            chips = false,
        )
        return (metadataRows + locationRow).filter { it.field in fields && it.values.isNotEmpty() }
    }

    private fun GarmentShareItem.pdfContent(
        context: Context,
        fields: Set<ExportField>,
        swatches: List<GarmentShareColor>,
    ): PdfContent {
        val rows = shareRows(context, fields)
        return PdfContent(
            title = pdfTitle(
                name = if (ExportField.Name in fields) name else "",
                categoryValues = rows.firstOrNull { it.field == ExportField.Category }?.values.orEmpty(),
                fallback = context.getString(R.string.pdf_title_fallback),
            ),
            notes = if (ExportField.Notes in fields) notes else "",
            metadata = rows.map { it.label to it.values },
            colors = swatches.map { it.role to it.name.ifBlank { noColorLabel } },
        )
    }

    private fun GarmentShareItem.cardContent(
        context: Context,
        fields: Set<ExportField>,
        swatches: List<GarmentShareColor>,
    ): CardContent {
        val rows = shareRows(context, fields)
        val (category, others) = rows.partition { it.field == ExportField.Category }
        val colors = if (swatches.isEmpty()) {
            emptyList()
        } else {
            listOf(
                CardSection(
                    label = colorSectionLabel,
                    values = swatches.map { it.name.ifBlank { noColorLabel } },
                    chips = true,
                    swatchHex = swatches.map { it.hex },
                ),
            )
        }
        return CardContent(
            title = if (ExportField.Name in fields) name.trim() else "",
            sections = category.map { CardSection(it.label, it.values, chips = false) } +
                colors +
                others.map { CardSection(it.label, it.values, it.chips) },
            notes = if (ExportField.Notes in fields) notes else "",
        )
    }

    fun safeFileStem(garmentName: String): String {
        val normalized = Normalizer.normalize(garmentName.trim(), Normalizer.Form.NFKD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
            .trim('-')
        return normalized.ifBlank { "robia-garment" }
    }

    private fun drawCard(
        canvas: Canvas,
        layout: CardLayout,
        photo: Bitmap,
        logo: Bitmap,
        accent: Int?,
        fonts: PdfFonts,
    ) {
        canvas.drawColor(paper)
        drawPhoto(canvas, photo, layout.photo, accent)
        layout.title?.let { drawTextBlock(canvas, it, textPaint(fonts.bold, it.sizePt, ink)) }
        layout.sections.forEach { sectionLayout ->
            val section = sectionLayout.section
            drawWrapped(
                canvas,
                section.label.uppercase(Locale.getDefault()),
                sectionLayout.labelBox,
                textPaint(fonts.bold, CARD_LABEL_LETTER_SIZE, muted).apply { letterSpacing = 0.14f },
            )
            if (section.chips) {
                sectionLayout.boxes.zip(section.values).forEachIndexed { index, (box, value) ->
                    drawChip(canvas, box, value, section.swatchHex.getOrNull(index)?.toAndroidColorOrNull(), fonts)
                }
            } else {
                drawWrapped(
                    canvas,
                    section.values.joinToString(", "),
                    sectionLayout.boxes.single(),
                    textPaint(fonts.semiBold, CARD_VALUE_LETTER_SIZE, ink),
                )
            }
        }
        layout.notes?.let { drawTextBlock(canvas, it, textPaint(fonts.regular, it.sizePt, body)) }
        val markHeight = layout.mark.h
        val markWidth = markHeight * logo.width / logo.height
        canvas.drawBitmap(
            logo,
            null,
            RectF(layout.mark.x, layout.mark.y, layout.mark.x + markWidth, layout.mark.bottom),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }

    /** A chip keeps its colour fill when it is a colour; the text turns white on dark fills. */
    private fun drawChip(canvas: Canvas, box: PdfBox, text: String, fill: Int?, fonts: PdfFonts) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill ?: chipFill }
        val radius = box.h / 2f
        canvas.drawRoundRect(RectF(box.x, box.y, box.x + box.w, box.bottom), radius, radius, background)
        val onFill = if (fill != null && ColorUtils.calculateLuminance(fill) < 0.5) Color.WHITE else ink
        val paint = textPaint(fonts.regular, CARD_CHIP_LETTER_SIZE, onFill)
        val baseline = box.y + box.h / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, box.x + CARD_CHIP_TEXT_PAD, baseline, paint)
    }

    private fun drawPdfPage(
        canvas: Canvas,
        page: PdfPage,
        photo: Bitmap,
        logo: Bitmap,
        accent: Int?,
        swatchColors: List<GarmentShareColor>,
        fonts: PdfFonts,
    ) {
        canvas.drawColor(paper)
        drawHeader(canvas, fonts)
        page.photo?.let { drawPhoto(canvas, photo, it, accent) }
        page.title?.let { drawTextBlock(canvas, it, textPaint(fonts.bold, it.sizePt, ink)) }
        page.notes?.let { drawTextBlock(canvas, it, textPaint(fonts.regular, it.sizePt, body)) }
        page.swatches.zip(swatchColors).forEach { (swatch, color) ->
            drawSwatch(canvas, swatch, color.hex?.toAndroidColorOrNull(), fonts)
        }
        drawMetadata(canvas, page.metadata, fonts)
        drawFooter(canvas, logo, page.footer, fonts)
    }

    private fun drawHeader(canvas: Canvas, fonts: PdfFonts) {
        val date = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            .uppercase(Locale.getDefault())
        val labelPaint = textPaint(fonts.bold, 9f, muted).apply { letterSpacing = 0.18f }
        canvas.drawText("ROBIA · WARDROBE CARD", PAGE_MARGIN, HEADER_BASELINE, labelPaint)
        val datePaint = Paint(labelPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(date, PDF_PAGE_WIDTH - PAGE_MARGIN, HEADER_BASELINE, datePaint)
    }

    private fun drawPhoto(canvas: Canvas, photo: Bitmap, box: PdfBox, accent: Int?) {
        val frame = RectF(box.x, box.y, box.x + box.w, box.bottom)
        val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                frame.left,
                frame.top,
                frame.left,
                frame.bottom,
                photoBackdrop,
                (accent ?: fallbackAccent).withAlpha(51),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(frame, PHOTO_RADIUS, PHOTO_RADIUS, backdrop)
        val inset = box.h * (1f - PHOTO_FIT_SHARE) / 2f
        drawBitmapFitContain(canvas, photo, RectF(frame.left, frame.top + inset, frame.right, frame.bottom - inset))
    }

    private fun drawBitmapFitContain(canvas: Canvas, bitmap: Bitmap, target: RectF) {
        val scale = min(target.width() / bitmap.width, target.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = target.left + (target.width() - width) / 2f
        val top = target.top + (target.height() - height) / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(left, top, left + width, top + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }

    private fun drawSwatch(canvas: Canvas, swatch: PdfSwatch, hex: Int?, fonts: PdfFonts) {
        val centerX = swatch.cell.x + SWATCH_RADIUS
        val centerY = swatch.cell.y + SWATCH_RADIUS
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex ?: Color.TRANSPARENT }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(34, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawCircle(centerX, centerY, SWATCH_RADIUS, fill)
        canvas.drawCircle(centerX, centerY, SWATCH_RADIUS, outline)
        if (hex == null) {
            canvas.drawLine(centerX - 10f, centerY - 10f, centerX + 10f, centerY + 10f, outline)
            canvas.drawLine(centerX + 10f, centerY - 10f, centerX - 10f, centerY + 10f, outline)
        }
        drawWrapped(canvas, swatch.role.uppercase(Locale.getDefault()), swatch.roleBox, textPaint(fonts.regular, 10f, muted))
        drawWrapped(canvas, swatch.name, swatch.nameBox, textPaint(fonts.semiBold, 12.5f, ink))
    }

    private fun drawMetadata(canvas: Canvas, rows: List<PdfMetadataRow>, fonts: PdfFonts) {
        val first = rows.firstOrNull() ?: return
        val columnRule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rule; strokeWidth = 1f }
        val rowRulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rowRule; strokeWidth = 1f }
        canvas.drawLine(first.box.x, first.box.y, first.box.x, rows.last().box.bottom, columnRule)
        rows.forEach { row ->
            val dividerY = row.box.bottom - 0.5f
            canvas.drawLine(row.box.x, dividerY, row.box.x + row.box.w, dividerY, rowRulePaint)
            val labelPaint = textPaint(fonts.bold, 8.5f, muted).apply { letterSpacing = 0.14f }
            drawWrapped(canvas, row.label.uppercase(Locale.getDefault()), row.labelBox, labelPaint)
            drawWrapped(canvas, row.value, row.valueBox, textPaint(fonts.semiBold, 13f, ink))
        }
    }

    private fun drawFooter(canvas: Canvas, logo: Bitmap, footer: PdfBox, fonts: PdfFonts) {
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rule; strokeWidth = 1f }
        canvas.drawLine(footer.x, footer.y, footer.x + footer.w, footer.y, rulePaint)

        val logoWidth = FOOTER_LOGO_HEIGHT * logo.width / logo.height
        val logoTop = footer.y + (footer.h - FOOTER_LOGO_HEIGHT) / 2f
        canvas.drawBitmap(
            logo,
            null,
            RectF(footer.x, logoTop, footer.x + logoWidth, logoTop + FOOTER_LOGO_HEIGHT),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        val creditPaint = textPaint(fonts.regular, 9f, muted).apply {
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.12f
        }
        canvas.drawText("Created with Robia", footer.x + footer.w, footer.y + footer.h / 2f + 3f, creditPaint)
    }

    private fun GarmentShareItem.pdfSwatchColors(): List<GarmentShareColor> {
        val colors = listOf(primaryColor) + listOf(secondaryColor).filter { it.isAvailable(noColorLabel) }
        return if (colors.any { it.hex != null }) colors else emptyList()
    }

    private fun String.toAndroidColorOrNull(): Int? = runCatching { Color.parseColor(this) }.getOrNull()

    private fun Int.withAlpha(alpha: Int): Int = Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))

    private fun GarmentShareColor.isAvailable(noColorLabel: String): Boolean =
        hex != null || name.isNotBlank() && !name.equals(noColorLabel, ignoreCase = true)

    private fun shareFile(context: Context, garmentName: String, extension: String): File {
        val dir = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        return File(dir, "${safeFileStem(garmentName)}-${UUID.randomUUID()}.$extension")
    }

    private fun contentUriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private inline fun <T> Bitmap.use(block: (Bitmap) -> T): T = try {
        block(this)
    } finally {
        recycle()
    }
}

private const val PDF_LINE_HEIGHT_FACTOR = 1.25f

private class PdfFonts(val regular: Typeface, val semiBold: Typeface, val bold: Typeface) {
    companion object {
        fun load(context: Context): PdfFonts = PdfFonts(
            regular = ResourcesCompat.getFont(context, R.font.inter_regular) ?: Typeface.DEFAULT,
            semiBold = ResourcesCompat.getFont(context, R.font.inter_semibold) ?: Typeface.DEFAULT_BOLD,
            bold = ResourcesCompat.getFont(context, R.font.inter_bold) ?: Typeface.DEFAULT_BOLD,
        )
    }
}

/** Measures in the bold face, the widest of the three, so a box never holds fewer lines than the drawn text. */
private class PdfPaintMeasurer(private val fonts: PdfFonts) : PdfTextMeasurer {
    override fun lineCount(text: String, sizePt: Float, widthPt: Float): Int =
        textLayout(text, textPaint(fonts.bold, sizePt, Color.BLACK), widthPt).lineCount

    override fun lineHeight(sizePt: Float): Float = sizePt * PDF_LINE_HEIGHT_FACTOR

    /** Chip text is drawn in the regular face, so it is measured in that face. */
    override fun widthOf(text: String, sizePt: Float): Float = textPaint(fonts.regular, sizePt, Color.BLACK).measureText(text)
}

private fun textPaint(typeface: Typeface, sizePt: Float, color: Int): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = sizePt
        this.color = color
    }

private fun textLayout(text: String, paint: TextPaint, widthPt: Float): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, ceil(widthPt).toInt().coerceAtLeast(1))
        .setIncludePad(false)
        .setMaxLines(Int.MAX_VALUE)
        .setLineSpacing(paint.textSize * PDF_LINE_HEIGHT_FACTOR - paint.fontSpacing, 1f)
        .build()

/** Draws wrapped text inside its box, starting at [firstLine]; no ellipsis, the box is clipped. */
private fun drawWrapped(canvas: Canvas, text: String, box: PdfBox, paint: TextPaint, firstLine: Int = 0) {
    val layout = textLayout(text, paint, box.w)
    canvas.save()
    canvas.clipRect(box.x, box.y, box.x + box.w, box.bottom)
    canvas.translate(box.x, box.y - layout.getLineTop(firstLine.coerceAtMost(layout.lineCount)))
    layout.draw(canvas)
    canvas.restore()
}

private fun drawTextBlock(canvas: Canvas, block: PdfTextBlock, paint: TextPaint) {
    drawWrapped(canvas, block.text, block.box, paint, block.firstLine)
}

data class GarmentShareItem(
    val name: String,
    val notes: String,
    val imageUri: Uri,
    val metadata: List<GarmentShareMetadata>,
    val colorSectionLabel: String,
    val primaryColor: GarmentShareColor,
    val secondaryColor: GarmentShareColor,
    val noColorLabel: String,
    /** Location values, shown only when the Location field is chosen (not part of [metadata]). */
    val location: List<String> = emptyList(),
)

/** One metadata row of the chosen fields; [chips] rows draw one chip per value on the card. */
private data class ShareRow(
    val field: ExportField,
    val label: String,
    val values: List<String>,
    val chips: Boolean,
)

data class GarmentShareMetadata(
    val label: String,
    val values: List<String>,
    val icon: GarmentShareMetadataIcon,
)

enum class GarmentShareMetadataIcon {
    Category,
    Season,
    Occasion,
    Fit,
}

data class GarmentShareColor(
    val role: String,
    val name: String,
    val hex: String?,
)
