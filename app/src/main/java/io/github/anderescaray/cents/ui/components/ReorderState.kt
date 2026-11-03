package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * A list reordered by dragging a row's handle: which row the finger is holding and how far it
 * has travelled. Shared by every list that can be put in order, so they all move the same way.
 */
class ReorderState {
    var draggedIndex by mutableStateOf<Int?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
    var rowHeight by mutableIntStateOf(0)

    /** The position the dragged row would take if the finger let go now. */
    fun targetIndex(count: Int): Int? {
        val from = draggedIndex ?: return null
        val movedRows = if (rowHeight == 0 || count == 0) 0 else (offset / rowHeight).roundToInt()
        return (from + movedRows).coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    fun start(index: Int) {
        draggedIndex = index
        offset = 0f
    }

    fun stop() {
        draggedIndex = null
        offset = 0f
    }
}

/**
 * A row of a reorderable list: it follows the finger while it is the one held, and otherwise
 * slides one row up or down to open the gap the held one is heading for.
 */
@Composable
fun Modifier.reorderableRow(
    state: ReorderState,
    index: Int,
    count: Int,
): Modifier {
    val isDragged = index == state.draggedIndex
    // Derived, not computed inline: a drag emits an offset per frame, but the displacement of the
    // rows around it only changes when the finger crosses into the next position.
    val shift by remember(index, count) {
        derivedStateOf {
            displacement(
                index = index,
                from = state.draggedIndex,
                to = state.targetIndex(count),
                rowHeight = state.rowHeight,
            )
        }
    }
    val animatedShift by animateFloatAsState(shift, label = "reorderShift")
    return zIndex(if (isDragged) 1f else 0f)
        .onSizeChanged { state.rowHeight = it.height }
        .graphicsLayer { translationY = if (isDragged) state.offset else animatedShift }
}

/** The handle a row is dragged by. Letting go reports where it moved from and to. */
fun Modifier.reorderHandle(
    state: ReorderState,
    index: Int,
    count: Int,
    onMove: (from: Int, to: Int) -> Unit,
): Modifier =
    pointerInput(index, count) {
        detectDragGestures(
            onDragStart = { state.start(index) },
            onDrag = { change, amount ->
                change.consume()
                state.offset += amount.y
            },
            onDragEnd = {
                val from = state.draggedIndex
                val to = state.targetIndex(count)
                if (from != null && to != null && from != to) onMove(from, to)
                state.stop()
            },
            onDragCancel = state::stop,
        )
    }

/**
 * How far a row that is not being dragged has to move so the gap follows the finger: everything
 * between the row's old and new position shifts by exactly one row.
 */
internal fun displacement(
    index: Int,
    from: Int?,
    to: Int?,
    rowHeight: Int,
): Float {
    if (from == null || to == null || from == to) return 0f
    return when {
        from < to && index in (from + 1)..to -> -rowHeight.toFloat()
        from > to && index in to..<from -> rowHeight.toFloat()
        else -> 0f
    }
}
