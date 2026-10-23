package com.gusanitolabs.robia.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DRAG_EDGE_ZONE = 64.dp
private val DRAG_MAX_STEP = 24.dp
private const val DRAG_FRAME_MS = 16L

/** The card under a long-press that is still held: the drag anchor, and the selection before the press. */
internal class DragSelectArm {
    var anchorId: String? = null
    var baseline: Set<String> = emptySet()

    fun arm(itemId: String, selectedBefore: Set<String>) {
        anchorId = itemId
        baseline = selectedBefore
    }

    fun disarm() {
        anchorId = null
        baseline = emptySet()
    }
}

/** One drag: the selection is [baseline] plus every item from [anchorId] to [currentId] in grid order. */
internal class DragSelect(
    private val anchorId: String,
    private val baseline: Set<String>,
) {
    var currentId: String = anchorId
        private set

    fun moveTo(itemId: String) {
        currentId = itemId
    }

    fun selectedIds(orderedIds: List<String>): Set<String> {
        val anchor = orderedIds.indexOf(anchorId)
        val current = orderedIds.indexOf(currentId)
        if (anchor < 0 || current < 0) return baseline
        return baseline + orderedIds.subList(minOf(anchor, current), maxOf(anchor, current) + 1)
    }
}

/**
 * Pixels to scroll this frame while a drag is held at [pointerY]: zero outside the edge zones, linear up to
 * [maxStepPx] at the very edge. Negative scrolls up, positive scrolls down.
 */
internal fun dragAutoscrollStep(
    pointerY: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edgePx: Float,
    maxStepPx: Float,
): Float {
    val intoTop = viewportStart + edgePx - pointerY
    if (intoTop > 0f) return -maxStepPx * minOf(intoTop / edgePx, 1f)
    val intoBottom = pointerY - (viewportEnd - edgePx)
    if (intoBottom > 0f) return maxStepPx * minOf(intoBottom / edgePx, 1f)
    return 0f
}

/**
 * Grid-level drag for range selection. It only acts while [arm] holds a card's long-press: then it consumes the
 * moves, so the grid does not scroll, and autoscrolls in the edge zones. Two or more fingers end the drag, so
 * pinch keeps working. Without an armed long-press it consumes nothing.
 */
@Composable
internal fun Modifier.dragSelect(
    gridState: LazyGridState,
    arm: DragSelectArm,
    items: List<UiWardrobeItem>,
    onSelectionChange: (Set<String>) -> Unit,
): Modifier {
    val currentItems by rememberUpdatedState(items)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val scope = rememberCoroutineScope()
    return pointerInput(gridState, arm) {
        val edgePx = DRAG_EDGE_ZONE.toPx()
        val maxStepPx = DRAG_MAX_STEP.toPx()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var drag: DragSelect? = null
            var pinched = false
            var pointer = Offset.Zero
            var autoscroll: Job? = null
            fun track(active: DragSelect) {
                val ids = currentItems.map(UiWardrobeItem::id)
                gridState.itemIdAt(pointer, ids.toHashSet())?.let(active::moveTo)
                currentOnSelectionChange(active.selectedIds(ids))
            }
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        // Pinch takes over; the selection made so far stays.
                        pinched = true
                        drag = null
                        autoscroll?.cancel()
                    } else if (!pinched) {
                        val change = event.changes.firstOrNull { it.pressed }
                        val anchorId = arm.anchorId
                        if (change != null && drag == null && anchorId != null) {
                            val started = DragSelect(anchorId, arm.baseline)
                            drag = started
                            autoscroll = scope.launch {
                                while (true) {
                                    delay(DRAG_FRAME_MS)
                                    val layout = gridState.layoutInfo
                                    val step = dragAutoscrollStep(
                                        pointerY = pointer.y,
                                        viewportStart = layout.viewportStartOffset.toFloat(),
                                        viewportEnd = layout.viewportEndOffset.toFloat(),
                                        edgePx = edgePx,
                                        maxStepPx = maxStepPx,
                                    )
                                    if (step != 0f) {
                                        gridState.scrollBy(step)
                                        track(started)
                                    }
                                }
                            }
                        }
                        val active = drag
                        if (change != null && active != null) {
                            pointer = change.position
                            change.consume()
                            track(active)
                        }
                    }
                } while (event.changes.any { it.pressed })
            } finally {
                autoscroll?.cancel()
                arm.disarm()
            }
        }
    }
}

/** The visible card under [pointer] whose id is in [ids]; gaps and banners hit nothing. */
private fun LazyGridState.itemIdAt(pointer: Offset, ids: Set<String>): String? {
    for (info in layoutInfo.visibleItemsInfo) {
        val id = info.key as? String ?: continue
        if (id !in ids) continue
        val left = info.offset.x.toFloat()
        val top = info.offset.y.toFloat()
        if (pointer.x in left..left + info.size.width && pointer.y in top..top + info.size.height) return id
    }
    return null
}
