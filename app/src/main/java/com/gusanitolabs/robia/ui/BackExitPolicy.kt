package com.gusanitolabs.robia.ui

/** What one Back press on the root Browse or Manage screen can see. Pure: no Android types. */
data class BackState(
    val onManage: Boolean,
    val selectionCount: Int,
    val hasQuery: Boolean,
    val hasSimilarTo: Boolean,
    val hasFilters: Boolean,
    /** `SystemClock.elapsedRealtime()` of the last exit hint, or null when none is pending. */
    val lastExitHintMs: Long?,
)

sealed interface BackAction {
    data object ClearSelection : BackAction
    data object ClearSearchAndFilters : BackAction
    data object GoToBrowse : BackAction
    data object ShowExitHint : BackAction
    data object Exit : BackAction
}

/** A second Back press inside this window (measured from the hint) exits the app. */
const val EXIT_WINDOW_MILLIS = 2000L

/**
 * One press peels one layer: selection, then search / "similar to" / filters together,
 * then Manage goes to Browse, and plain Browse asks twice before exiting.
 */
fun decideBack(state: BackState, nowMs: Long): BackAction {
    val hintMs = state.lastExitHintMs
    return when {
        state.selectionCount > 0 -> BackAction.ClearSelection
        state.hasQuery || state.hasSimilarTo || state.hasFilters -> BackAction.ClearSearchAndFilters
        state.onManage -> BackAction.GoToBrowse
        hintMs != null && nowMs >= hintMs && nowMs - hintMs <= EXIT_WINDOW_MILLIS -> BackAction.Exit
        else -> BackAction.ShowExitHint
    }
}
