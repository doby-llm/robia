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
    private const val PAGE_WIDTH = 720
    private const val MIN_PDF_PAGE_HEIGHT = 1440
    private const val PAGE_MARGIN = 48f
    private const val SECTION_GAP = 24f
    private const val FOOTER_HEIGHT = 64f

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
            val document = PdfDocument()
            val pageHeight = calculatePdfPageHeight(source, item)
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, pageHeight, 1).create())
            drawGarmentCard(page.canvas, source, item, pageHeight)
            document.finishPage(page)

            val file = shareFile(context, item.name, "pdf")
            file.outputStream().use { output -> document.writeTo(output) }
            document.close()
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

    private fun drawGarmentCard(canvas: Canvas, image: Bitmap, item: GarmentShareItem, pageHeight: Int) {
        canvas.drawColor(Color.rgb(255, 252, 247))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 209, 185) }
        val titlePaint = textPaint(size = 32f, color = Color.rgb(35, 30, 25), bold = true)
        val bodyPaint = textPaint(size = 18f, color = Color.rgb(90, 80, 69))
        val labelPaint = textPaint(size = 15f, color = Color.rgb(110, 92, 70), bold = true)
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(246, 239, 229) }

        val imageRect = RectF(PAGE_MARGIN, PAGE_MARGIN, PAGE_WIDTH - PAGE_MARGIN, PAGE_MARGIN + imageFrameHeight(image))
        drawRoundRect(canvas, imageRect, 30f, cardPaint)
        drawBitmapFitContain(canvas, image, imageRect, 30f)

        var y = imageRect.bottom + 48f
        y = drawWrappedText(canvas, item.name, PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, titlePaint, maxLines = 3) + 14f
        if (item.notes.isNotBlank()) {
            y = drawWrappedText(canvas, item.notes, PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, bodyPaint, maxLines = 5) + SECTION_GAP
        }

        for (metadata in item.metadata.filter { it.values.isNotEmpty() }) {
            y = drawMetadataSection(canvas, metadata, PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, chipPaint, bodyPaint, labelPaint)
            y += SECTION_GAP
        }

        canvas.drawText(item.colorSectionLabel.uppercase(Locale.getDefault()), PAGE_MARGIN, y, labelPaint)
        y += 28f
        y = drawColorSummary(canvas, item.primaryColor, PAGE_MARGIN, y, item.noColorLabel, bodyPaint, labelPaint)
        y = drawColorSummary(canvas, item.secondaryColor, PAGE_MARGIN, y + 12f, item.noColorLabel, bodyPaint, labelPaint)

        val footerY = maxOf(y + 54f, pageHeight - 54f)
        val footerPaint = textPaint(size = 17f, color = Color.rgb(70, 58, 45), bold = true).apply {
            textAlign = Paint.Align.CENTER
        }
        val footerText = "Created with Robia"
        val footerWidth = footerPaint.measureText(footerText) + 56f
        val footerRect = RectF(
            PAGE_WIDTH / 2f - footerWidth / 2f,
            footerY - 28f,
            PAGE_WIDTH / 2f + footerWidth / 2f,
            footerY + 12f,
        )
        canvas.drawRoundRect(footerRect, 20f, 20f, accentPaint)
        canvas.drawText(footerText, PAGE_WIDTH / 2f, footerY - 2f, footerPaint)
    }

    private fun drawBitmapFitContain(canvas: Canvas, bitmap: Bitmap, target: RectF, radius: Float) {
        val path = Path().apply { addRoundRect(target, radius, radius, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawColor(Color.WHITE)
        val scale = min(target.width() / bitmap.width, target.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = target.left + (target.width() - width) / 2f
        val top = target.top + (target.height() - height) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.restoreToCount(save)
    }

    private fun drawMetadataSection(
        canvas: Canvas,
        metadata: GarmentShareMetadata,
        left: Float,
        top: Float,
        right: Float,
        chipPaint: Paint,
        bodyPaint: Paint,
        labelPaint: Paint,
    ): Float {
        canvas.drawText(metadata.label.uppercase(Locale.getDefault()), left, top, labelPaint)
        return drawChips(canvas, metadata.values, left, top + 26f, right, chipPaint, bodyPaint)
    }

    private fun drawChips(
        canvas: Canvas,
        values: List<String>,
        left: Float,
        top: Float,
        right: Float,
        chipPaint: Paint,
        textPaint: Paint,
    ): Float {
        var x = left
        var y = top
        val chipHeight = 34f
        val visibleValues = if (values.size > 8) values.take(7) + "+${values.size - 7}" else values
        visibleValues.forEach { value ->
            val label = value.take(30)
            val width = min(textPaint.measureText(label) + 28f, right - left)
            if (x + width > right && x > left) {
                x = left
                y += chipHeight + 8f
            }
            canvas.drawRoundRect(RectF(x, y, x + width, y + chipHeight), 17f, 17f, chipPaint)
            canvas.drawText(label, x + 14f, y + 23f, textPaint)
            x += width + 8f
        }
        return y + chipHeight
    }

    private fun drawColorSummary(
        canvas: Canvas,
        color: GarmentShareColor,
        left: Float,
        top: Float,
        noColorLabel: String,
        bodyPaint: Paint,
        labelPaint: Paint,
    ): Float {
        val centerY = top + 24f
        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.hex?.toAndroidColorOrNull() ?: Color.TRANSPARENT }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.rgb(198, 187, 174)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(left + 22f, centerY, 18f, swatchPaint)
        canvas.drawCircle(left + 22f, centerY, 18f, outlinePaint)
        if (color.hex == null) {
            canvas.drawLine(left + 12f, centerY - 10f, left + 32f, centerY + 10f, outlinePaint)
            canvas.drawLine(left + 32f, centerY - 10f, left + 12f, centerY + 10f, outlinePaint)
        }
        canvas.drawText(color.role.uppercase(Locale.getDefault()), left + 52f, top + 16f, labelPaint)
        return drawWrappedText(canvas, color.name.ifBlank { noColorLabel }, left + 52f, top + 40f, PAGE_WIDTH - PAGE_MARGIN, bodyPaint, maxLines = 2)
    }

    private fun calculatePdfPageHeight(image: Bitmap, item: GarmentShareItem): Int {
        val titlePaint = textPaint(size = 32f, color = Color.rgb(35, 30, 25), bold = true)
        val bodyPaint = textPaint(size = 18f, color = Color.rgb(90, 80, 69))
        val contentWidth = PAGE_WIDTH - (PAGE_MARGIN * 2)

        var contentHeight = PAGE_MARGIN + imageFrameHeight(image) + 48f
        contentHeight += measureWrappedTextHeight(item.name, titlePaint, contentWidth, maxLines = 3) + 14f
        if (item.notes.isNotBlank()) {
            contentHeight += measureWrappedTextHeight(item.notes, bodyPaint, contentWidth, maxLines = 5) + SECTION_GAP
        }
        item.metadata.filter { it.values.isNotEmpty() }.forEach { metadata ->
            contentHeight += 26f + measureChipsHeight(metadata.values, contentWidth, bodyPaint) + SECTION_GAP
        }
        contentHeight += 28f + 56f + 12f + 56f + FOOTER_HEIGHT + PAGE_MARGIN

        return maxOf(MIN_PDF_PAGE_HEIGHT, contentHeight.toInt() + 1)
    }

    private fun imageFrameHeight(image: Bitmap): Float {
        val contentWidth = PAGE_WIDTH - (PAGE_MARGIN * 2)
        val aspectHeight = contentWidth * image.height / image.width.toFloat()
        return aspectHeight.coerceIn(520f, 780f)
    }

    private fun measureChipsHeight(values: List<String>, availableWidth: Float, textPaint: Paint): Float {
        var x = 0f
        var y = 0f
        val chipHeight = 34f
        val visibleValues = if (values.size > 8) values.take(7) + "+${values.size - 7}" else values
        visibleValues.forEach { value ->
            val label = value.take(30)
            val width = min(textPaint.measureText(label) + 28f, availableWidth)
            if (x + width > availableWidth && x > 0f) {
                x = 0f
                y += chipHeight + 8f
            }
            x += width + 8f
        }
        return y + chipHeight
    }

    private fun measureWrappedTextHeight(text: String, paint: Paint, availableWidth: Float, maxLines: Int): Float {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return 0f
        val lineHeight = paint.textSize * 1.25f
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
    ): Float {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return top
        val lineHeight = paint.textSize * 1.25f
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

    private fun drawRoundRect(canvas: Canvas, rect: RectF, radius: Float, paint: Paint) {
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    private fun String.toAndroidColorOrNull(): Int? = runCatching { Color.parseColor(this) }.getOrNull()

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
