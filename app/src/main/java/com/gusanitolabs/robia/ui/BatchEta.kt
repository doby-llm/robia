package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.GarmentTag
import java.text.Collator
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val BATCH_ETA_GAP_WINDOW = 4
private const val BATCH_ETA_MAX_RISE = 1.2
private const val BATCH_ETA_ALMOST_DONE_MS = 10_000.0
private const val BATCH_ETA_SECONDS_LIMIT_MS = 90_000.0

internal sealed interface BatchEta {
    data class Seconds(val s: Int) : BatchEta
    data class Minutes(val m: Int) : BatchEta
    data object AlmostDone : BatchEta
}

/**
 * Approximate time left for a batch. The raw estimate is the mean of the last gaps between completed garments
 * times the number of garments left. The shown value may drop freely but rises at most 20 % per update, so the
 * countdown never jumps up.
 */
internal class BatchEtaEstimator(private val clock: () -> Long) {
    private val completionTimes = mutableListOf<Long>()
    private var shownMs: Double? = null

    fun onTerminal() {
        completionTimes += clock()
    }

    fun estimate(remaining: Int): BatchEta? {
        if (completionTimes.size < 2) return null
        val gapsMs = completionTimes.zipWithNext { earlier, later -> (later - earlier).toDouble() }
            .takeLast(BATCH_ETA_GAP_WINDOW)
        val rawMs = gapsMs.average() * remaining
        val previousMs = shownMs
        val smoothedMs = if (previousMs == null || previousMs <= 0.0) {
            rawMs
        } else {
            minOf(rawMs, previousMs * BATCH_ETA_MAX_RISE)
        }
        shownMs = smoothedMs
        return batchEtaFor(smoothedMs)
    }
}

internal fun batchEtaFor(ms: Double): BatchEta = when {
    ms < BATCH_ETA_ALMOST_DONE_MS -> BatchEta.AlmostDone
    ms < BATCH_ETA_SECONDS_LIMIT_MS -> BatchEta.Seconds((ceil(ms / 1_000.0 / 10.0) * 10).toInt())
    else -> BatchEta.Minutes((ms / 60_000.0).roundToInt())
}

/** Plural resource and count for each non-zero attention item, in the order review, failed, interrupted. */
internal fun batchAttentionLine(needsReview: Int, failed: Int, interrupted: Int): List<Pair<Int, Int>> = listOf(
    R.plurals.batch_attention_review to needsReview,
    R.plurals.batch_attention_failed to failed,
    R.plurals.batch_attention_interrupted to interrupted,
).filter { (_, count) -> count > 0 }

/**
 * Second line of a batch row: the localized category and occasions, occasions sorted by [collator].
 * Failed items and items needing review show their error message instead while one is set.
 */
internal fun batchRowSummary(
    draft: BatchDraftItem,
    availableTags: List<GarmentTag>,
    localize: (GarmentTag) -> String,
    collator: Collator,
): String {
    if (draft.isProcessingActive()) return ""
    val needsAttention = draft.status == BatchDraftStatus.Failed ||
        (draft.status == BatchDraftStatus.NeedsReview && !draft.explicitlyAccepted)
    if (needsAttention) draft.errorMessage?.let { return it }
    val tagsById = availableTags.associateBy(GarmentTag::id)
    val selected = draft.selectedTagIds.mapNotNull(tagsById::get)
    val category = selected.firstOrNull { tag -> tag.categoryId == "category" }?.let(localize)
    val occasions = selected
        .filter { tag -> tag.categoryId == "occasion" }
        .map(localize)
        .sortedWith(collator)
        .joinToString(", ")
    return listOfNotNull(category, occasions.takeIf { it.isNotEmpty() }).joinToString(" · ")
}
