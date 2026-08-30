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
 * A row that slides sideways to reveal a destructive action.
 *
 * **The row never stays open.** Carrying the drag past [FULL_SWIPE_FRACTION] of the row and
 * letting go fires; anything shorter springs back. There is no middle, which is a deliberate
 * departure from iOS, where a short drag parks the row with a tappable **Delete** showing —
 * see `docs/decisions/0001-ios-flavored-ui.md`. The owner found the parked state on the
 * phone and read it as the row getting stuck rather than as an offer.
 *
 * The cost of that is real and worth naming here: deleting by gesture now always takes a
 * confident swipe across most of the row, and the revealed strip is feedback during the
 * drag rather than a button. What it is *not* is a loss of the non-gesture path — that was
 * never the strip, it is the custom accessibility action below, which is unaffected.
 *
 * [onDelete] is expected to *ask*, not to delete: a swipe is a faster way to reach the
 * question the app already asks (rule 7, and `DECISIONS.md` D7), never a way around it. The
 * row closes itself as soon as it has asked, so the answer is given to a dialog rather than
 * to a row left open behind it.
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

    /** Ask, then tidy up after ourselves: the dialog owns the screen from here. */
    fun fire() {
        onDelete()
        scope.launch { offsetX.animateTo(0f) }
    }

    Box(modifier = modifier.onSizeChanged { rowWidth = it.width }) {
        RevealedDelete(label = deleteLabel)

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
                        onDragStopped = { settle(offsetX, rowWidth, ::fire) },
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
private fun BoxScope.RevealedDelete(label: String) {
    Row(
        modifier =
            Modifier
                .matchParentSize()
                .background(CentsTheme.colors.destructive),
        horizontalArrangement = Arrangement.End,
    ) {
        DeleteAction(label = label)
    }
}

/**
 * Where the row comes to rest, and whether letting go counted as deleting.
 *
 * Past [FULL_SWIPE_FRACTION] of the row the gesture is unambiguous — nobody drags a row two
 * thirds of the way across by accident — so it fires. Everything short of that springs back,
 * including the drag that used to park the row open.
 */
private suspend fun settle(
    offsetX: Animatable<Float, *>,
    rowWidth: Int,
    onFire: () -> Unit,
) {
    if (rowWidth > 0 && offsetX.value <= -rowWidth * FULL_SWIPE_FRACTION) {
        onFire()
    } else {
        offsetX.animateTo(0f)
    }
}

/**
 * What the drag uncovers. Now that the row never stays open this is feedback and nothing
 * else: it is never clickable and never in the semantics tree, in either state.
 *
 * The second half still matters, and for the reason it always did. Left reachable, every
 * closed row publishes a "Delete" node hidden only by the row sitting on top of it, so
 * TalkBack reads one between every pair of rows and D-pad focus lands on buttons nobody can
 * see — duplicating, for exactly those users, the custom action this component provides on
 * the row itself. That action is now the only way to delete without a gesture.
 */
@Composable
private fun DeleteAction(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                // First in the chain, because it clears what comes after it: the tag and the
                // label both have to go.
                .clearAndSetSemantics { }
                .testTag(DELETE_ACTION_TAG)
                .fillMaxHeight()
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
