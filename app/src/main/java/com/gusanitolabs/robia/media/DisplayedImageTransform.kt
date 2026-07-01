package com.gusanitolabs.robia.media

/**
 * FIT_CENTER mapping shared by Quick Edit rendering, hit testing, and overlay drawing.
 * Values are in displayed container pixels unless otherwise noted.
 */
data class DisplayedImageTransform(
    val imageWidth: Int,
    val imageHeight: Int,
    val containerWidth: Float,
    val containerHeight: Float,
) {
    val contentWidth: Float
    val contentHeight: Float
    val left: Float
    val top: Float
    val right: Float
    val bottom: Float

    init {
        val safeImageWidth = imageWidth.coerceAtLeast(1).toFloat()
        val safeImageHeight = imageHeight.coerceAtLeast(1).toFloat()
        val safeContainerWidth = containerWidth.coerceAtLeast(1f)
        val safeContainerHeight = containerHeight.coerceAtLeast(1f)
        val scale = minOf(safeContainerWidth / safeImageWidth, safeContainerHeight / safeImageHeight)
        contentWidth = safeImageWidth * scale
        contentHeight = safeImageHeight * scale
        left = (safeContainerWidth - contentWidth) / 2f
        top = (safeContainerHeight - contentHeight) / 2f
        right = left + contentWidth
        bottom = top + contentHeight
    }

    fun contains(containerX: Float, containerY: Float): Boolean =
        containerX >= left && containerX <= right && containerY >= top && containerY <= bottom

    fun toNormalizedPoint(containerX: Float, containerY: Float): NormalizedImagePoint? {
        if (!contains(containerX, containerY)) return null
        return NormalizedImagePoint(
            x = ((containerX - left) / contentWidth).coerceIn(0f, 1f),
            y = ((containerY - top) / contentHeight).coerceIn(0f, 1f),
        )
    }
}

data class ImageDimensions(
    val width: Int,
    val height: Int,
) {
    val isValid: Boolean = width > 0 && height > 0
}
