package com.gusanitolabs.robia.media.cutout

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Result of [HangerRemoval.guardedBand]: the S4b selection, and whether the band guard kept the first selection in the band. */
internal class BandOutcome(val selection: FloatArray, val guardTripped: Boolean)

/**
 * Hanger rules of the precise pipeline (S3, S4 and S4b), pure so they run on the JVM. Ported from `pipeline_v11.py`
 * with the BAND=1 FILL=median variant that REQ-SG-05 specifies.
 */
internal object HangerRemoval {
    const val MAX_KILL_FRACTION = 0.15f
    private const val CONTAINED_FRACTION = 0.85f
    private const val BAND_KEEP_FRACTION = 0.85f
    private const val OPAQUE_LEVEL = 0.5f
    private const val BOX_MARGIN_PX = 4
    private const val KILL_DILATION = 9
    private const val RING_PX = 25
    private const val FILL_BLUR_RADIUS = 4
    private const val BAND_HEIGHT_FRACTION = 0.6
    private const val BAND_MIN_GARMENT_FRACTION = 0.08
    private const val BAND_BLEND_PX = 3
    private const val NEAR_KILL_PX = 2
    private const val FALLBACK_FILL = 235

    /** A hanger spans a good part of the garment width and sits at its top (the S3 plausibility rule). */
    fun isPlausible(box: MaskBox, garment: MaskBox): Boolean =
        (box.x1 - box.x0) >= 0.35f * garment.width &&
            box.y0 < garment.y0 + 0.25f * garment.height &&
            box.y1 < garment.y0 + 0.5f * garment.height

    /**
     * S3 with its two passes. The first pass ([detect] with a null region) sees the whole photo; when no detection there is
     * plausible, the second pass sees the top band of the garment ([hangerSearchRegion]) and its boxes are moved back to
     * photo coordinates. [threshold] is handed to both passes. Returns the first plausible hanger, clipped to the photo.
     */
    fun findHanger(
        garment: MaskBox,
        w: Int,
        h: Int,
        threshold: Float,
        detect: (region: IntArray?, threshold: Float) -> List<Pair<Float, MaskBox>>,
    ): MaskBox? {
        val first = detect(null, threshold).filter { isPlausible(it.second, garment) }
        val found = if (first.isNotEmpty()) {
            first
        } else {
            val region = hangerSearchRegion(garment, w, h)
            detect(region, threshold)
                .map { (score, box) ->
                    score to MaskBox(box.x0 + region[0], box.y0 + region[1], box.x1 + region[0], box.y1 + region[1])
                }
                .filter { isPlausible(it.second, garment) }
        }
        return found.firstOrNull()?.second?.let { clip(it, w, h) }
    }

    /**
     * Second S3 pass region (exclusive right and bottom, as a slice): the top band of the garment, widened by 10 % and
     * 15 % of its size. Returns `[x0, y0, x1, y1]`.
     */
    fun hangerSearchRegion(garment: MaskBox, w: Int, h: Int): IntArray {
        val y0 = max(garment.y0 - (0.15f * garment.height).toInt(), 0)
        val y1 = min(garment.y0 + (0.35f * garment.height).toInt(), h)
        val x0 = max(garment.x0 - (0.1f * garment.width).toInt(), 0)
        val x1 = min(garment.x1 + (0.1f * garment.width).toInt(), w)
        return intArrayOf(x0, y0, x1, y1)
    }

    /** The clipped box of a detection, as `hanger_mask` clips it before prompting SAM. */
    fun clip(box: MaskBox, w: Int, h: Int): MaskBox =
        MaskBox(max(box.x0, 0), max(box.y0, 0), min(box.x1, w - 1), min(box.y1, h - 1))

    /**
     * Pixels of the hanger to remove, from the SAM box-only mask [hangerProb] and the garment mask [garmentProb]
     * (both w×h probabilities). Null when the kill would exceed 15 % of the garment area, in which case nothing is removed.
     */
    fun killMask(
        hangerProb: FloatArray,
        garmentProb: FloatArray,
        box: MaskBox,
        w: Int,
        h: Int,
        garmentArea: Int,
    ): BooleanArray? {
        val inBox = BooleanArray(w * h)
        for (y in max(box.y0 - BOX_MARGIN_PX, 0) until min(box.y1 + BOX_MARGIN_PX, h)) {
            for (x in max(box.x0 - BOX_MARGIN_PX, 0) until min(box.x1 + BOX_MARGIN_PX, w)) {
                inBox[y * w + x] = true
            }
        }
        var hangerCount = 0
        var hangerInside = 0
        for (i in hangerProb.indices) {
            if (hangerProb[i] > 0.5f) {
                hangerCount++
                if (inBox[i]) hangerInside++
            }
        }
        val contained = hangerInside.toFloat() / max(hangerCount, 1) >= CONTAINED_FRACTION
        val kill = BooleanArray(w * h) { i ->
            inBox[i] && hangerProb[i] > 0.5f && (contained || garmentProb[i] < 0.5f)
        }
        val killCount = kill.count { it }
        return if (killCount.toFloat() / max(garmentArea, 1) > MAX_KILL_FRACTION) null else kill
    }

    /** Dilation applied to the kill before the hanger-first fill (km = kill dilated 9×9). */
    fun killSupport(kill: BooleanArray, w: Int, h: Int): BooleanArray = MaskOps.dilate(kill, w, h, KILL_DILATION)

    /** The S4b fill colour: the per-channel median of the 25 px ring around [support], opaque (FALLBACK_FILL when the ring is empty). */
    fun ringColour(argb: IntArray, w: Int, h: Int, support: BooleanArray): Int {
        val ring = MaskOps.dilate(support, w, h, RING_PX).let { grown -> BooleanArray(w * h) { grown[it] && !support[it] } }
        val median = IntArray(3) { channelIndex ->
            val shift = SHIFTS[channelIndex]
            val values = (0 until w * h).filter { ring[it] }.map { (argb[it] shr shift) and 0xFF }.sorted()
            if (values.isEmpty()) {
                FALLBACK_FILL
            } else {
                val mid = values.size / 2
                if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2
            }
        }
        return pack(median[0], median[1], median[2])
    }

    /**
     * S4b fill: pixels under [support] take [colour] (the [ringColour] by default), then a 9×9 box blur is taken inside
     * [support] only. Returns a new ARGB array.
     */
    fun fill(argb: IntArray, w: Int, h: Int, support: BooleanArray, colour: Int = ringColour(argb, w, h, support)): IntArray {
        val filled = argb.copyOf()
        for (i in filled.indices) {
            if (support[i]) filled[i] = colour
        }
        val blurred = SHIFTS.map { shift -> MaskOps.boxMean(MaskOps.channel(filled, shift), w, h, FILL_BLUR_RADIUS) }
        for (i in filled.indices) {
            if (support[i]) {
                filled[i] = pack(
                    blurred[0][i].roundToInt().coerceIn(0, 255),
                    blurred[1][i].roundToInt().coerceIn(0, 255),
                    blurred[2][i].roundToInt().coerceIn(0, 255),
                )
            }
        }
        return filled
    }

    /** Rows above which the re-selection is used (`band_bottom`): hanger bottom + max(60 % of hanger height, 8 % of garment). */
    fun bandBottom(box: MaskBox, garmentHeight: Int, h: Int): Int =
        min((box.y1 + max(BAND_HEIGHT_FRACTION * (box.y1 - box.y0), BAND_MIN_GARMENT_FRACTION * garmentHeight)).toInt(), h)

    /**
     * The band rule: above [bandBottom] the re-selection is used, below it the previous selection, and [support] is
     * cleared in both (the hanger is always removed). Across the band bottom the two are blended linearly over
     * [BAND_BLEND_PX] rows (K03 step 2), so there is no 0/1 step: the re-selection's weight is [bandWeight].
     */
    fun applyBand(reselected: FloatArray, previous: FloatArray, support: BooleanArray, bandBottom: Int, w: Int): FloatArray =
        FloatArray(previous.size) { i ->
            val weight = bandWeight(i / w, bandBottom)
            when {
                support[i] -> 0f
                weight >= 1f -> reselected[i]
                weight <= 0f -> previous[i]
                else -> previous[i] + weight * (reselected[i] - previous[i]) // exact when the two are equal (guard tripped)
            }
        }

    /** Weight of the re-selection in [row]: 1 two rows above [bandBottom], falling by a third per row to 0 one row below it. */
    private fun bandWeight(row: Int, bandBottom: Int): Float =
        ((bandBottom - row + 1).toFloat() / BAND_BLEND_PX).coerceIn(0f, 1f)

    /**
     * The S5 guide (K03 step 1): luminance of the filled photo above [bandBottom] (the hanger is gone there), that of the
     * original photo below it.
     */
    fun bandGuide(originalLuminance: FloatArray, filledLuminance: FloatArray, bandBottom: Int, w: Int): FloatArray =
        FloatArray(originalLuminance.size) { i -> if (i / w < bandBottom) filledLuminance[i] else originalLuminance[i] }

    /**
     * Colour decontamination (K03 step 3): where [alpha] is above 0 within [NEAR_KILL_PX] of [support] (the dilated kill, so
     * including its own pixels), the colour becomes [colour], the fill colour; other pixels keep [argb]. The caller composites
     * the low 24 bits only.
     */
    fun decontaminate(argb: IntArray, alpha: ByteArray, support: BooleanArray, colour: Int, w: Int, h: Int): IntArray {
        val near = MaskOps.dilate(support, w, h, 2 * NEAR_KILL_PX + 1)
        return IntArray(argb.size) { i ->
            if (near[i] && (alpha[i].toInt() and 0xFF) > 0) colour else argb[i]
        }
    }

    /**
     * The band rule with its guard (K02 (a)). Inside the band (rows above [bandBottom], outside [support]) the re-selection
     * must keep at least 85 % of the opaque pixels the first selection had there. When it keeps less than 85 % of them, the
     * first selection is used inside the band as well ([BandOutcome.guardTripped], logged as `band_guard=1`).
     */
    fun guardedBand(reselected: FloatArray, previous: FloatArray, support: BooleanArray, bandBottom: Int, w: Int): BandOutcome {
        var previousOpaque = 0
        var kept = 0
        for (i in 0 until min(bandBottom * w, previous.size)) {
            if (support[i] || previous[i] <= OPAQUE_LEVEL) continue
            previousOpaque++
            if (reselected[i] > OPAQUE_LEVEL) kept++
        }
        val tripped = previousOpaque > 0 && kept < BAND_KEEP_FRACTION * previousOpaque
        val selection = if (tripped) {
            applyBand(previous, previous, support, bandBottom, w)
        } else {
            applyBand(reselected, previous, support, bandBottom, w)
        }
        return BandOutcome(selection, tripped)
    }

    private val SHIFTS = intArrayOf(16, 8, 0)

    private fun pack(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}
