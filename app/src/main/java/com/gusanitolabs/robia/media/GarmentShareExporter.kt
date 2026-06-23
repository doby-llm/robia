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
    private const val PAGE_HEIGHT = 960
    private const val PAGE_MARGIN = 48f

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
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            drawGarmentCard(page.canvas, source, item)
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

    private fun drawGarmentCard(canvas: Canvas, image: Bitmap, item: GarmentShareItem) {
        canvas.drawColor(Color.rgb(255, 252, 247))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 209, 185) }
        val titlePaint = textPaint(size = 32f, color = Color.rgb(35, 30, 25), bold = true)
        val bodyPaint = textPaint(size = 18f, color = Color.rgb(90, 80, 69))
        val labelPaint = textPaint(size = 15f, color = Color.rgb(110, 92, 70), bold = true)
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(246, 239, 229) }

        val imageRect = RectF(PAGE_MARGIN, 38f, PAGE_WIDTH - PAGE_MARGIN, 500f)
        drawRoundRect(canvas, imageRect, 30f, cardPaint)
        drawBitmapCropped(canvas, image, imageRect, 30f)

        var y = 548f
        y = drawWrappedText(canvas, item.name, PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, titlePaint, maxLines = 2) + 10f
        if (item.notes.isNotBlank()) {
            y = drawWrappedText(canvas, item.notes, PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, bodyPaint, maxLines = 3) + 18f
        }

        for (metadata in item.metadata.filter { it.values.isNotEmpty() }.take(5)) {
            if (y >= 740f) break
            canvas.drawText(metadata.label.uppercase(Locale.getDefault()), PAGE_MARGIN, y, labelPaint)
            y += 25f
            y = drawChips(canvas, metadata.values, PAGE_MARGIN, y, PAGE_WIDTH - PAGE_MARGIN, chipPaint, bodyPaint)
            y += 12f
        }

        y = min(y + 4f, 790f)
        canvas.drawText(item.colorSectionLabel.uppercase(Locale.getDefault()), PAGE_MARGIN, y, labelPaint)
        y += 22f
        drawColorSummary(canvas, item.primaryColor, PAGE_MARGIN, y, item.noColorLabel, bodyPaint, labelPaint)
        drawColorSummary(canvas, item.secondaryColor, 380f, y, item.noColorLabel, bodyPaint, labelPaint)

        val footerY = PAGE_HEIGHT - 54f
        canvas.drawRoundRect(RectF(252f, footerY - 28f, 468f, footerY + 12f), 20f, 20f, accentPaint)
        val footerPaint = textPaint(size = 17f, color = Color.rgb(70, 58, 45), bold = true).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("creado con robia", PAGE_WIDTH / 2f, footerY - 2f, footerPaint)
    }

    private fun drawBitmapCropped(canvas: Canvas, bitmap: Bitmap, target: RectF, radius: Float) {
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
    ) {
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
        canvas.drawText(color.name.ifBlank { noColorLabel }, left + 52f, top + 40f, bodyPaint)
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
