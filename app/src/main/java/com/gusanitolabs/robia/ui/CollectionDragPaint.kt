package com.gusanitolabs.robia.ui

/**
 * One paint-drag on the members screen. The anchor's state before the drag decides what the whole range becomes:
 * if the anchor was not in the draft, the range is painted in; if it was, the range is painted out.
 * Items outside the range keep their [before] state, so shrinking the range restores them.
 */
class DragPaint(private val anchorId: String, private val before: Set<String>) {
    val targetIn: Boolean = anchorId !in before

    var currentId: String = anchorId
        private set

    fun moveTo(id: String) {
        currentId = id
    }

    /**
     * Draft after the paint so far. The range is the ids between anchor and current in [orderedIds], inclusive,
     * in either direction. If the anchor or the current id is not in [orderedIds], the draft is [before].
     */
    fun draft(orderedIds: List<String>): Set<String> {
        val anchor = orderedIds.indexOf(anchorId)
        val current = orderedIds.indexOf(currentId)
        if (anchor < 0 || current < 0) return before
        val range = orderedIds.subList(minOf(anchor, current), maxOf(anchor, current) + 1)
        return if (targetIn) before + range else before - range.toSet()
    }
}
