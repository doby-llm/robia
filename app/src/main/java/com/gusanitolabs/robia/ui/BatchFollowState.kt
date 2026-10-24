package com.gusanitolabs.robia.ui

/**
 * Whether the batch list follows the garment being processed (REQ-FB-07). Each event returns the next state, so the
 * rules stay pure: no Compose or Android types.
 */
internal data class BatchFollowState(val following: Boolean = true) {
    /** A manual drag of the list stops following. */
    fun onUserScroll(): BatchFollowState = copy(following = false)

    /** The Follow affordance resumes following. */
    fun onFollowTapped(): BatchFollowState = copy(following = true)

    /** Reaching the bottom of the list resumes following. */
    fun onAtBottom(): BatchFollowState = copy(following = true)

    /** Index of the garment being processed (the first Processing row), or null when none is. */
    fun targetIndex(rows: List<BatchDraftItem>): Int? =
        rows.indexOfFirst { it.status == BatchDraftStatus.Processing }.takeIf { it >= 0 }
}
