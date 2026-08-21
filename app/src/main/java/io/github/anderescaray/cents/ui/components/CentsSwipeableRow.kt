package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A row that slides sideways to reveal a destructive action, the way iOS deletes things.
 *
 * Two gestures reach the same place. A short drag parks the row open with **Delete**
 * showing, to be tapped or dismissed with a tap anywhere on the row; carrying the drag most
 * of the way across and letting go fires it directly.
 *
 * Either way [onDelete] is what happens next — and [onDelete] is expected to *ask*, not to
 * delete: a swipe is a faster way to reach the question the app already asks (rule 7, and
 * `DECISIONS.md` D7), never a way around it. The row closes itself as soon as it has
 * asked, so the answer is given to a dialog rather than to a row left open behind it.
 *
 * The gesture is horizontal only, so it does not fight the vertical drag that reorders a
 * list, nor the scroll of the list it sits in.
 *
 * [content] is handed a modifier it must apply to the row it builds. That is how the same
 * action reaches TalkBack, which has no swipe to make: the modifier carries a custom
 * accessibility action, and it has to sit on the row's own semantics node — the one screen
 * readers actually focus — rather than on a wrapper around it, where it would be announced
 * to nobody.
 */
@Composable
fun CentsSwipeableRow(
    onDelete: () -> Unit,
    deleteLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var rowWidth by remember { mutableIntStateOf(0) }
    var actionWidth by remember { mutableIntStateOf(0) }

    /** Derived so the flag flips once, not on every frame the drag produces. */
    val open by remember { derivedStateOf { offsetX.value < 0f } }

    /** Ask, then tidy up after ourselves: the dialog owns the screen from here. */
    fun fire() {
        onDelete()
        scope.launch { offsetX.animateTo(0f) }
    }

    Box(modifier = modifier.onSizeChanged { rowWidth = it.width }) {
        RevealedDelete(
            label = deleteLabel,
            onClick = ::fire,
            reachable = open,
            onWidth = { actionWidth = it },
        )

        Box(
            modifier =
                Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .draggable(
                        state =
                            rememberDraggableState { delta ->
                                scope.launch {
                                    // Left only, and never further than the row is wide.
                                    offsetX.snapTo((offsetX.value + delta).coerceIn(-rowWidth.toFloat(), 0f))
                                }
                            },
                        orientation = Orientation.Horizontal,
                        onDragStopped = { settle(offsetX, rowWidth, actionWidth, ::fire) },
                    ),
        ) {
            content(
                Modifier.semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(deleteLabel) {
                                fire()
                                true
                            },
                        )
                },
            )

            // An open row answers a tap by closing, the way iOS does — otherwise changing
            // your mind about a swipe opens the editor you were trying not to reach. It
            // does not swallow the drag: a press that turns into one still slides the row.
            if (open) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { scope.launch { offsetX.animateTo(0f) } },
                )
            }
        }
    }
}

/**
 * What the swipe uncovers.
 *
 * `matchParentSize` keeps it out of the measurement: the row decides how tall the pair is,
 * and this fills whatever that turns out to be. The colour is on the strip rather than on
 * the button, because a full swipe carries the row much further than the button is wide and
 * would otherwise open a card-coloured gap behind it.
 */
@Composable
private fun BoxScope.RevealedDelete(
    label: String,
    onClick: () -> Unit,
    reachable: Boolean,
    onWidth: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .matchParentSize()
                .background(CentsTheme.colors.destructive),
        horizontalArrangement = Arrangement.End,
    ) {
        DeleteAction(
            label = label,
            onClick = onClick,
            reachable = reachable,
            modifier = Modifier.onSizeChanged { onWidth(it.width) },
        )
    }
}

/**
 * Where the row comes to rest, and whether letting go counted as deleting.
 *
 * Past [FULL_SWIPE_FRACTION] of the row the gesture is unambiguous — nobody drags a row
 * two thirds of the way across by accident — so it fires. Short of that it either parks
 * open, once the finger has passed half the button, or springs back.
 */
private suspend fun settle(
    offsetX: Animatable<Float, *>,
    rowWidth: Int,
    actionWidth: Int,
    onFire: () -> Unit,
) {
    val open = -actionWidth.toFloat()
    when {
        rowWidth > 0 && offsetX.value <= -rowWidth * FULL_SWIPE_FRACTION -> onFire()
        offsetX.value <= open / 2 -> offsetX.animateTo(open)
        else -> offsetX.animateTo(0f)
    }
}

/**
 * The revealed button. It is always composed — its width is what the drag settles against —
 * but while the row is closed it is not [reachable]: no click, and no semantics.
 *
 * That second half matters more than it looks. Left alone, every closed row publishes a
 * clickable "Delete" node hidden only by the row sitting on top of it, so TalkBack reads one
 * between every pair of rows and D-pad focus lands on buttons nobody can see — duplicating,
 * for exactly those users, the custom action this component went out of its way to provide.
 */
@Composable
private fun DeleteAction(
    label: String,
    onClick: () -> Unit,
    reachable: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                // First in the chain, because it clears what comes after it: the tag, the
                // click and the label all have to go while the row is closed.
                .then(if (reachable) Modifier else Modifier.clearAndSetSemantics { })
                .testTag(DELETE_ACTION_TAG)
                .fillMaxHeight()
                .clickable(enabled = reachable, onClick = onClick)
                .defaultMinSize(minWidth = ACTION_MIN_WIDTH.dp)
                .padding(horizontal = CentsDimens.spacingL),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CentsTheme.typography.body,
            color = CentsTheme.colors.onAccent,
        )
    }
}

/** iOS sizes this button to its label, with a floor so it stays a comfortable target. */
private const val ACTION_MIN_WIDTH = 88

/** How far across the row a drag has to go before letting go means "do it". */
private const val FULL_SWIPE_FRACTION = 0.6f

internal const val DELETE_ACTION_TAG = "swipeDelete"
