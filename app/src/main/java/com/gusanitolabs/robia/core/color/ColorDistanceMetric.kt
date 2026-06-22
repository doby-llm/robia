package com.gusanitolabs.robia.core.color

import kotlin.math.pow
import kotlin.math.sqrt

interface ColorDistanceMetric {
    fun distance(first: RgbColor, second: RgbColor): Double
}

enum class ColorClassificationStrategy {
    RgbEuclidean,
    CieLabDeltaE76,
}

object RgbEuclideanDistanceMetric : ColorDistanceMetric {
    override fun distance(first: RgbColor, second: RgbColor): Double =
        (first.red - second.red).toDouble().pow(2) +
            (first.green - second.green).toDouble().pow(2) +
            (first.blue - second.blue).toDouble().pow(2)
}

object CieLabDeltaE76DistanceMetric : ColorDistanceMetric {
    override fun distance(first: RgbColor, second: RgbColor): Double =
        first.toCieLab().distanceTo(second.toCieLab())
}

data class CieLabColor(
    val lightness: Double,
    val a: Double,
    val b: Double,
) {
    fun distanceTo(other: CieLabColor): Double = sqrt(
        (lightness - other.lightness).pow(2) +
            (a - other.a).pow(2) +
            (b - other.b).pow(2),
    )
}

fun RgbColor.toCieLab(): CieLabColor {
    val linearRed = red.toLinearSrgbChannel()
    val linearGreen = green.toLinearSrgbChannel()
    val linearBlue = blue.toLinearSrgbChannel()

    val x = 0.4124564 * linearRed + 0.3575761 * linearGreen + 0.1804375 * linearBlue
    val y = 0.2126729 * linearRed + 0.7151522 * linearGreen + 0.0721750 * linearBlue
    val z = 0.0193339 * linearRed + 0.1191920 * linearGreen + 0.9503041 * linearBlue

    val fx = labPivot(x / D65_X)
    val fy = labPivot(y / D65_Y)
    val fz = labPivot(z / D65_Z)

    return CieLabColor(
        lightness = 116.0 * fy - 16.0,
        a = 500.0 * (fx - fy),
        b = 200.0 * (fy - fz),
    )
}

private fun Int.toLinearSrgbChannel(): Double {
    val normalized = this / 255.0
    return if (normalized <= SRGB_LINEAR_THRESHOLD) {
        normalized / 12.92
    } else {
        ((normalized + 0.055) / 1.055).pow(2.4)
    }
}

private fun labPivot(value: Double): Double =
    if (value > LAB_EPSILON) {
        value.pow(1.0 / 3.0)
    } else {
        LAB_KAPPA_OVER_116 * value + LAB_16_OVER_116
    }

private const val SRGB_LINEAR_THRESHOLD = 0.04045
private const val LAB_EPSILON = 216.0 / 24_389.0
private const val LAB_KAPPA_OVER_116 = 841.0 / 108.0
private const val LAB_16_OVER_116 = 4.0 / 29.0
private const val D65_X = 0.95047
private const val D65_Y = 1.0
private const val D65_Z = 1.08883
