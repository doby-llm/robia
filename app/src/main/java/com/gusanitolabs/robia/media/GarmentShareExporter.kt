package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlin.math.min

object GarmentShareExporter {
    private const val SHARE_DIRECTORY = "robia_shares"
    private const val ROBIA_LOGO_ASSET = "robia_logo.png"
    private const val PAGE_WIDTH = 720
    private const val MIN_PDF_PAGE_HEIGHT = 1280
    private const val PAGE_MARGIN = 44f
    private const val SECTION_GAP = 26f
    private const val FOOTER_HEIGHT = 136f

    fun createShareImage(
        context: Context,
        sourceUri: Uri,
        garmentName: String,
    ): Uri {
        val bitmap = context.contentResolver.openInputStream(sourceUri)?.use(BitmapFactory::decodeStream)
            ?: error("Unable to open garment image")
        return bitmap.use { source ->
            val file = shareFile(context, garmentName, "png")
            file.outputStream().use { output ->
                check(source.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode share image" }
            }
            contentUriFor(context, file)
        }
    }

    fun createSharePdf(
        context: Context,
        item: GarmentShareItem,
    ): Uri {
        val bitmap = context.contentResolver.openInputStream(item.imageUri)?.use(BitmapFactory::decodeStream)
            ?: error("Unable to open garment image")
        return bitmap.use { source ->
            val logo = context.assets.open(ROBIA_LOGO_ASSET).use(BitmapFactory::decodeStream)
            val document = PdfDocument()
            val pageHeight = calculatePdfPageHeight(source, item)
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, pageHeight, 1).create())
            drawGarmentCard(page.canvas, source, logo, item, pageHeight)
            document.finishPage(page)

            val file = shareFile(context, item.name, "pdf")
            file.outputStream().use { output -> document.writeTo(output) }
            document.close()
            logo.recycle()
            contentUriFor(context, file)
        }
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

    private fun drawGarmentCard(
        canvas: Canvas,
        image: Bitmap,
        logo: Bitmap,
        item: GarmentShareItem,
        pageHeight: Int,
    ) {
        canvas.drawColor(Color.rgb(250, 249, 247))

        val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(227, 226, 224)
            strokeWidth = 1.5f
        }
        val titlePaint = textPaint(size = 48f, color = Color.rgb(24, 25, 29), bold = true)
        val bodyPaint = textPaint(size = 20f, color = Color.rgb(70, 70, 75))
        val labelPaint = textPaint(size = 13f, color = Color.rgb(70, 70, 75), bold = true).apply { letterSpacing = 0.08f }
        val gridLabelPaint = textPaint(size = 15f, color = Color.rgb(70, 70, 75))
        val gridValuePaint = textPaint(size = 25f, color = Color.rgb(24, 25, 29))

        val contentRight = PAGE_WIDTH - PAGE_MARGIN
        val imageRect = RectF(PAGE_MARGIN, PAGE_MARGIN, contentRight, PAGE_MARGIN + imageFrameHeight(image))
        drawBitmapFitCover(canvas, image, imageRect, 0f)

        var y = imageRect.bottom + 36f
        if (item.name.isNotBlank()) {
            y = drawWrappedText(canvas, item.name, PAGE_MARGIN, y, contentRight, titlePaint, maxLines = 3, lineHeightMultiplier = 1.05f) + 16f
        }
        if (item.notes.isNotBlank()) {
            y = drawWrappedText(canvas, item.notes, PAGE_MARGIN, y, contentRight, bodyPaint, maxLines = 5, lineHeightMultiplier = 1.42f) + SECTION_GAP
        }

        canvas.drawLine(PAGE_MARGIN, y, contentRight, y, dividerPaint)
        y += 42f
        canvas.drawText(item.colorSectionLabel.uppercase(Locale.getDefault()), PAGE_MARGIN, y, labelPaint)
        y += 26f
        y = drawColorRow(canvas, item, PAGE_MARGIN, y, contentRight) + 48f

        val gridMetadata = item.metadata.filter { it.values.isNotEmpty() }.take(4)
        if (gridMetadata.isNotEmpty()) {
            y = drawMetadataGrid(canvas, gridMetadata, PAGE_MARGIN, y, contentRight, surfacePaint, gridLabelPaint, gridValuePaint) + 44f
        }

        canvas.drawLine(PAGE_MARGIN, y, contentRight, y, dividerPaint)
        val footerTop = maxOf(y + 34f, pageHeight - PAGE_MARGIN - FOOTER_HEIGHT)
        drawFooter(canvas, logo, footerTop)
    }

    private fun drawBitmapFitCover(canvas: Canvas, bitmap: Bitmap, target: RectF, radius: Float) {
        val path = Path().apply { addRoundRect(target, radius, radius, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawColor(Color.WHITE)
        val scale = maxOf(target.width() / bitmap.width, target.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = target.left + (target.width() - width) / 2f
        val top = target.top + (target.height() - height) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.restoreToCount(save)
    }

    private fun drawColorRow(
        canvas: Canvas,
        item: GarmentShareItem,
        left: Float,
        top: Float,
        right: Float,
    ): Float {
        val colors = listOf(item.primaryColor) + listOf(item.secondaryColor).filter { it.isAvailable(item.noColorLabel) }
        var x = left
        var maxBottom = top
        colors.forEach { color ->
            val width = min(236f, right - x)
            val bottom = drawColorChip(canvas, color, x, top, width, item.noColorLabel)
            maxBottom = maxOf(maxBottom, bottom)
            x += width + 22f
        }
        return maxBottom
    }

    private fun drawColorChip(
        canvas: Canvas,
        color: GarmentShareColor,
        left: Float,
        top: Float,
        width: Float,
        noColorLabel: String,
    ): Float {
        val chipRect = RectF(left, top, left + width, top + 58f)
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.rgb(244, 243, 241) }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.rgb(227, 226, 224)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val labelPaint = textPaint(size = 15f, color = Color.rgb(24, 25, 29), bold = true)
        val rolePaint = textPaint(size = 11f, color = Color.rgb(70, 70, 75))
        canvas.drawRoundRect(chipRect, 29f, 29f, chipPaint)
        canvas.drawRoundRect(chipRect, 29f, 29f, outlinePaint)

        val swatchCenterY = top + 29f
        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.hex?.toAndroidColorOrNull() ?: Color.TRANSPARENT }
        canvas.drawCircle(left + 34f, swatchCenterY, 19f, swatchPaint)
        canvas.drawCircle(left + 34f, swatchCenterY, 19f, outlinePaint)
        if (color.hex == null) {
            canvas.drawLine(left + 22f, swatchCenterY - 12f, left + 46f, swatchCenterY + 12f, outlinePaint)
            canvas.drawLine(left + 46f, swatchCenterY - 12f, left + 22f, swatchCenterY + 12f, outlinePaint)
        }
        canvas.drawText(color.name.ifBlank { noColorLabel }.take(18), left + 70f, top + 24f, labelPaint)
        canvas.drawText(color.role, left + 70f, top + 42f, rolePaint)
        return chipRect.bottom
    }

    private fun drawMetadataGrid(
        canvas: Canvas,
        metadata: List<GarmentShareMetadata>,
        left: Float,
        top: Float,
        right: Float,
        surfacePaint: Paint,
        labelPaint: Paint,
        valuePaint: Paint,
    ): Float {
        val gap = 10f
        val cellWidth = (right - left - gap) / 2f
        val cellHeight = 154f
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(227, 226, 224)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        metadata.forEachIndexed { index, item ->
            val column = index % 2
            val row = index / 2
            val cellLeft = left + column * (cellWidth + gap)
            val cellTop = top + row * (cellHeight + gap)
            val rect = RectF(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight)
            canvas.drawRoundRect(rect, 12f, 12f, surfacePaint)
            canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
            canvas.drawText(item.label, cellLeft + 24f, cellTop + 74f, labelPaint)
            drawWrappedText(
                canvas = canvas,
                text = item.values.joinToString(", "),
                left = cellLeft + 24f,
                top = cellTop + 110f,
                right = rect.right - 22f,
                paint = valuePaint,
                maxLines = 2,
                lineHeightMultiplier = 1.16f,
            )
        }
        val rows = (metadata.size + 1) / 2
        return top + rows * cellHeight + (rows - 1) * gap
    }

    private fun drawFooter(canvas: Canvas, logo: Bitmap, top: Float) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 243, 241) }
        val footerRect = RectF(PAGE_MARGIN, top, PAGE_WIDTH - PAGE_MARGIN, top + FOOTER_HEIGHT)
        canvas.drawRoundRect(footerRect, 0f, 0f, footerPaint)

        val logoSize = 42f
        val logoLeft = PAGE_WIDTH / 2f - logoSize / 2f
        val logoTop = top + 30f
        canvas.drawBitmap(logo, null, RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize), Paint(Paint.ANTI_ALIAS_FLAG))

        val sparkPaint = textPaint(size = 20f, color = Color.rgb(98, 94, 87)).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("✦", PAGE_WIDTH / 2f - 38f, top + 56f, sparkPaint)
        canvas.drawText("✦", PAGE_WIDTH / 2f + 38f, top + 56f, sparkPaint)

        val textPaint = textPaint(size = 13f, color = Color.rgb(24, 25, 29), bold = true).apply {
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        canvas.drawText("Created with Robia", PAGE_WIDTH / 2f, top + 104f, textPaint)
    }

    private fun calculatePdfPageHeight(image: Bitmap, item: GarmentShareItem): Int {
        val titlePaint = textPaint(size = 48f, color = Color.rgb(24, 25, 29), bold = true)
        val bodyPaint = textPaint(size = 20f, color = Color.rgb(70, 70, 75))
        val contentWidth = PAGE_WIDTH - (PAGE_MARGIN * 2)

        var contentHeight = PAGE_MARGIN + imageFrameHeight(image) + 36f
        if (item.name.isNotBlank()) {
            contentHeight += measureWrappedTextHeight(item.name, titlePaint, contentWidth, maxLines = 3, lineHeightMultiplier = 1.05f) + 16f
        }
        if (item.notes.isNotBlank()) {
            contentHeight += measureWrappedTextHeight(item.notes, bodyPaint, contentWidth, maxLines = 5, lineHeightMultiplier = 1.42f) + SECTION_GAP
        }
        contentHeight += 1.5f + 42f + 26f + 58f + 48f
        val gridItems = item.metadata.count { it.values.isNotEmpty() }.coerceAtMost(4)
        if (gridItems > 0) {
            val gridRows = (gridItems + 1) / 2
            contentHeight += gridRows * 154f + (gridRows - 1) * 10f + 44f
        }
        contentHeight += 1.5f + 34f + FOOTER_HEIGHT + PAGE_MARGIN

        return maxOf(MIN_PDF_PAGE_HEIGHT, contentHeight.toInt() + 1)
    }

    private fun imageFrameHeight(image: Bitmap): Float {
        val contentWidth = PAGE_WIDTH - (PAGE_MARGIN * 2)
        val aspectHeight = contentWidth * image.height / image.width.toFloat()
        return aspectHeight.coerceIn(contentWidth * 1.04f, contentWidth * 1.24f)
    }


    private fun measureWrappedTextHeight(
        text: String,
        paint: Paint,
        availableWidth: Float,
        maxLines: Int,
        lineHeightMultiplier: Float = 1.25f,
    ): Float {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return 0f
        val lineHeight = paint.textSize * lineHeightMultiplier
        var line = ""
        var lines = 0
        for (word in words) {
            val candidate = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(candidate) <= availableWidth || line.isBlank()) {
                line = candidate
            } else {
                lines++
                line = word
                if (lines == maxLines - 1) break
            }
        }
        if (line.isNotBlank() && lines < maxLines) lines++
        return lineHeight * lines
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        right: Float,
        paint: Paint,
        maxLines: Int,
        lineHeightMultiplier: Float = 1.25f,
    ): Float {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return top
        val lineHeight = paint.textSize * lineHeightMultiplier
        var y = top
        var line = ""
        var lines = 0
        for (word in words) {
            val candidate = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(candidate) <= right - left || line.isBlank()) {
                line = candidate
            } else {
                canvas.drawText(line, left, y, paint)
                y += lineHeight
                lines++
                line = word
                if (lines == maxLines - 1) break
            }
        }
        if (line.isNotBlank() && lines < maxLines) {
            val suffix = if (words.joinToString(" ").length > line.length && lines == maxLines - 1) "…" else ""
            canvas.drawText(line.take(52) + suffix, left, y, paint)
        }
        return y + lineHeight
    }


    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    private fun String.toAndroidColorOrNull(): Int? = runCatching { Color.parseColor(this) }.getOrNull()

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

data class GarmentShareItem(
    val name: String,
    val notes: String,
    val imageUri: Uri,
    val metadata: List<GarmentShareMetadata>,
    val colorSectionLabel: String,
    val primaryColor: GarmentShareColor,
    val secondaryColor: GarmentShareColor,
    val noColorLabel: String,
)

data class GarmentShareMetadata(
    val label: String,
    val values: List<String>,
)

data class GarmentShareColor(
    val role: String,
    val name: String,
    val hex: String?,
)
