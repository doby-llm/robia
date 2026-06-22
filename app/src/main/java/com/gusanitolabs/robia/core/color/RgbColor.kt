package com.gusanitolabs.robia.core.color

/** sRGB color used by pure color-classification code. */
data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int = 255,
) {
    val value: Float
        get() = maxOf(red, green, blue) / MAX_CHANNEL_VALUE.toFloat()

    val saturation: Float
        get() {
            val maxChannel = maxOf(red, green, blue).toFloat()
            val minChannel = minOf(red, green, blue).toFloat()
            return if (maxChannel == 0f) 0f else (maxChannel - minChannel) / maxChannel
        }

    val isLowSaturation: Boolean
        get() = saturation <= LOW_SATURATION_THRESHOLD

    fun withoutAlpha(): RgbColor = copy(alpha = 255)

    companion object {
        fun fromHexOrNull(rawHex: String?): RgbColor? {
            val normalized = rawHex?.trim()?.removePrefix("#") ?: return null
            if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
                return null
            }
            return RgbColor(
                red = normalized.substring(0, 2).toInt(16),
                green = normalized.substring(2, 4).toInt(16),
                blue = normalized.substring(4, 6).toInt(16),
            )
        }
    }
}

private const val MAX_CHANNEL_VALUE = 255
private const val LOW_SATURATION_THRESHOLD = 0.12f
