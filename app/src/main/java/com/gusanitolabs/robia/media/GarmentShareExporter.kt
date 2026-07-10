package com.gusanitolabs.robia.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.graphics.RectF
import android.graphics.Shader
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
    const val ENABLE_PDF_IMAGE_GRADIENT_OVERLAY = true
    private const val PAGE_WIDTH = 720
    private const val MIN_PDF_PAGE_HEIGHT = 1280
    private const val PAGE_MARGIN = 44f
    private const val SECTION_GAP = 26f
    private const val FOOTER_HEIGHT = 136f
    private const val COLOR_CARD_HEIGHT = 104f

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
        drawBitmapFitContain(canvas, image, imageRect, 0f)
        if (ENABLE_PDF_IMAGE_GRADIENT_OVERLAY) {
            drawImageGradientOverlay(canvas, imageRect, item.primaryColor.hex?.toAndroidColorOrNull())
        }

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

    private fun drawBitmapFitContain(canvas: Canvas, bitmap: Bitmap, target: RectF, radius: Float) {
        val path = Path().apply { addRoundRect(target, radius, radius, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawColor(Color.rgb(250, 249, 247))
        val scale = min(target.width() / bitmap.width, target.height() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = target.left + (target.width() - width) / 2f
        val top = target.top + (target.height() - height) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.restoreToCount(save)
    }

    private fun drawImageGradientOverlay(canvas: Canvas, target: RectF, primaryColor: Int?) {
        val accent = primaryColor ?: Color.rgb(181, 162, 136)
        val path = Path().apply { addRect(target, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(path)

        val verticalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                target.left,
                target.top,
                target.left,
                target.bottom,
                intArrayOf(Color.TRANSPARENT, accent.withAlpha(46), Color.argb(92, 250, 249, 247)),
                floatArrayOf(0f, 0.64f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(target, verticalPaint)

        val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                target.left,
                target.centerY(),
                target.right,
                target.centerY(),
                intArrayOf(Color.argb(28, 250, 249, 247), Color.TRANSPARENT, accent.withAlpha(24)),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(target, sidePaint)
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
            val width = min(306f, right - x)
            val bottom = drawColorChip(canvas, color, x, top, width, item.noColorLabel)
            maxBottom = maxOf(maxBottom, bottom)
            x += width + 20f
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
        val chipRect = RectF(left, top, left + width, top + COLOR_CARD_HEIGHT)
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.rgb(227, 226, 224)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val labelPaint = textPaint(size = 24f, color = Color.rgb(24, 25, 29))
        val rolePaint = textPaint(size = 13f, color = Color.rgb(70, 70, 75), bold = true).apply { letterSpacing = 0.08f }
        canvas.drawRoundRect(chipRect, 14f, 14f, chipPaint)
        canvas.drawRoundRect(chipRect, 14f, 14f, outlinePaint)

        val swatchCenterY = top + COLOR_CARD_HEIGHT / 2f
        val swatchCenterX = left + 48f
        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.hex?.toAndroidColorOrNull() ?: Color.TRANSPARENT }
        canvas.drawCircle(swatchCenterX, swatchCenterY, 29f, swatchPaint)
        canvas.drawCircle(swatchCenterX, swatchCenterY, 29f, outlinePaint)
        if (color.hex == null) {
            canvas.drawLine(swatchCenterX - 17f, swatchCenterY - 17f, swatchCenterX + 17f, swatchCenterY + 17f, outlinePaint)
            canvas.drawLine(swatchCenterX + 17f, swatchCenterY - 17f, swatchCenterX - 17f, swatchCenterY + 17f, outlinePaint)
        }
        val textLeft = left + 94f
        canvas.drawText(color.role.uppercase(Locale.getDefault()), textLeft, top + 34f, rolePaint)
        canvas.drawText(color.name.ifBlank { noColorLabel }.fitSingleLine(labelPaint, width - 116f), textLeft, top + 72f, labelPaint)
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
        val rowHeights = metadata.chunked(2).map { rowItems ->
            rowItems.maxOf { metadataCellHeight(it, valuePaint, cellWidth) }
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(227, 226, 224)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        metadata.forEachIndexed { index, item ->
            val column = index % 2
            val row = index / 2
            val cellLeft = left + column * (cellWidth + gap)
            val cellTop = top + rowHeights.take(row).sum() + row * gap
            val rect = RectF(cellLeft, cellTop, cellLeft + cellWidth, cellTop + rowHeights[row])
            canvas.drawRoundRect(rect, 12f, 12f, surfacePaint)
            canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
            drawMetadataIcon(canvas, item.icon, cellLeft + 24f, cellTop + 24f)
            canvas.drawText(item.label, cellLeft + 70f, cellTop + 47f, labelPaint)
            drawWrappedText(
                canvas = canvas,
                text = item.values.joinToString(", "),
                left = cellLeft + 24f,
                top = cellTop + 92f,
                right = rect.right - 22f,
                paint = valuePaint,
                maxLines = Int.MAX_VALUE,
                lineHeightMultiplier = 1.16f,
            )
        }
        return top + rowHeights.sum() + (rowHeights.size - 1) * gap
    }

    private fun metadataCellHeight(item: GarmentShareMetadata, valuePaint: Paint, cellWidth: Float): Float {
        val valueTop = 92f
        val bottomPadding = 26f
        val valueWidth = cellWidth - 46f
        val valueHeight = measureWrappedTextHeight(
            text = item.values.joinToString(", "),
            paint = valuePaint,
            availableWidth = valueWidth,
            maxLines = Int.MAX_VALUE,
            lineHeightMultiplier = 1.16f,
        )
        return maxOf(154f, valueTop + valueHeight + bottomPadding)
    }

    private fun drawMetadataIcon(canvas: Canvas, icon: GarmentShareMetadataIcon, left: Float, top: Float) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(98, 94, 87)
            style = Paint.Style.STROKE
            strokeWidth = 3.4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(32, 181, 162, 136)
            style = Paint.Style.FILL
        }
        val frame = RectF(left, top, left + 34f, top + 34f)
        canvas.drawRoundRect(frame, 10f, 10f, fillPaint)
        when (icon) {
            GarmentShareMetadataIcon.Category -> drawCategoryIcon(canvas, frame, iconPaint)
            GarmentShareMetadataIcon.Season -> drawSeasonIcon(canvas, frame, iconPaint)
            GarmentShareMetadataIcon.Occasion -> drawOccasionIcon(canvas, frame, iconPaint)
            GarmentShareMetadataIcon.Fit -> drawFitIcon(canvas, frame, iconPaint)
        }
    }

    private fun drawCategoryIcon(canvas: Canvas, frame: RectF, paint: Paint) {
        val cx = frame.centerX()
        val top = frame.top + 9f
        val path = Path().apply {
            moveTo(cx, top)
            quadTo(cx + 7f, top + 1f, cx + 4f, top + 8f)
            moveTo(cx, top + 11f)
            lineTo(frame.left + 9f, frame.bottom - 9f)
            lineTo(frame.right - 9f, frame.bottom - 9f)
            lineTo(cx, top + 11f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawSeasonIcon(canvas: Canvas, frame: RectF, paint: Paint) {
        val cx = frame.centerX()
        val cy = frame.centerY()
        canvas.drawCircle(cx, cy, 5.5f, paint)
        repeat(8) { index ->
            val angle = Math.toRadians((index * 45).toDouble())
            val startX = cx + kotlin.math.cos(angle).toFloat() * 9f
            val startY = cy + kotlin.math.sin(angle).toFloat() * 9f
            val endX = cx + kotlin.math.cos(angle).toFloat() * 13f
            val endY = cy + kotlin.math.sin(angle).toFloat() * 13f
            canvas.drawLine(startX, startY, endX, endY, paint)
        }
    }

    private fun drawOccasionIcon(canvas: Canvas, frame: RectF, paint: Paint) {
        val cx = frame.centerX()
        val cy = frame.centerY()
        val path = Path().apply {
            moveTo(cx, frame.top + 8f)
            lineTo(cx + 3f, cy - 3f)
            lineTo(frame.right - 8f, cy)
            lineTo(cx + 3f, cy + 3f)
            lineTo(cx, frame.bottom - 8f)
            lineTo(cx - 3f, cy + 3f)
            lineTo(frame.left + 8f, cy)
            lineTo(cx - 3f, cy - 3f)
            close()
        }
        canvas.drawPath(path, paint)
        canvas.drawCircle(frame.right - 8f, frame.top + 9f, 1.6f, paint)
    }

    private fun drawFitIcon(canvas: Canvas, frame: RectF, paint: Paint) {
        val x = frame.left + 11f
        canvas.drawLine(x, frame.top + 8f, x, frame.bottom - 8f, paint)
        canvas.drawLine(x - 4f, frame.top + 8f, x + 4f, frame.top + 8f, paint)
        canvas.drawLine(x - 4f, frame.bottom - 8f, x + 4f, frame.bottom - 8f, paint)
        canvas.drawLine(frame.left + 17f, frame.bottom - 10f, frame.right - 8f, frame.top + 9f, paint)
        canvas.drawLine(frame.right - 8f, frame.top + 9f, frame.right - 8f, frame.top + 16f, paint)
        canvas.drawLine(frame.right - 8f, frame.top + 9f, frame.right - 15f, frame.top + 9f, paint)
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
        contentHeight += 1.5f + 42f + 26f + COLOR_CARD_HEIGHT + 48f
        val gridMetadata = item.metadata.filter { it.values.isNotEmpty() }.take(4)
        if (gridMetadata.isNotEmpty()) {
            val valuePaint = textPaint(size = 25f, color = Color.rgb(24, 25, 29))
            val gap = 10f
            val cellWidth = (contentWidth - gap) / 2f
            val rowHeights = gridMetadata.chunked(2).map { rowItems ->
                rowItems.maxOf { metadataCellHeight(it, valuePaint, cellWidth) }
            }
            contentHeight += rowHeights.sum() + (rowHeights.size - 1) * gap + 44f
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
    ): Float = paint.textSize * lineHeightMultiplier * wrappedLines(text, paint, availableWidth, maxLines).size

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
        val lineHeight = paint.textSize * lineHeightMultiplier
        var y = top
        wrappedLines(text, paint, right - left, maxLines).forEach { line ->
            canvas.drawText(line, left, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun wrappedLines(text: String, paint: Paint, availableWidth: Float, maxLines: Int): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(candidate) <= availableWidth || line.isBlank()) {
                line = candidate
            } else {
                lines += line
                if (lines.size == maxLines) return lines
                line = word
            }
        }
        if (line.isNotBlank() && lines.size < maxLines) lines += line
        return lines
    }


    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    private fun String.toAndroidColorOrNull(): Int? = runCatching { Color.parseColor(this) }.getOrNull()

    private fun Int.withAlpha(alpha: Int): Int = Color.argb(alpha, Color.red(this), Color.green(this), Color.blue(this))

    private fun String.fitSingleLine(paint: Paint, availableWidth: Float): String {
        if (paint.measureText(this) <= availableWidth) return this
        var end = length
        while (end > 1 && paint.measureText(substring(0, end)) > availableWidth) end--
        return substring(0, end).trimEnd()
    }

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
