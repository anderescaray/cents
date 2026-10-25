package io.github.anderescaray.cents.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The tab a horizontal swipe leads to: the next one when the finger travelled right to left, the
 * previous one when it travelled left to right, and none past either end.
 */
internal fun swipeTarget(
    current: TopLevelTab,
    towardsNext: Boolean,
): TopLevelTab? = TopLevelTab.entries.getOrNull(current.ordinal + if (towardsNext) 1 else -1)

/**
 * The screens, with a sideways swipe moving to the tab beside [swipeFrom]; null, as on anything
 * pushed above a tab's own screen, turns the swipe off. [content] is told whether the tab change
 * under way came from a swipe, so it can slide rather than fade.
 *
 * Anything underneath that takes a sideways drag itself — a row that swipes to delete, a strip of
 * chips — gets it first, because a parent sees a gesture only after its children have passed on
 * it. A swipe that starts in the system's gesture area at either edge is Android's back gesture,
 * and is left to it.
 */
@Composable
internal fun SwipeableTabs(
    navController: NavHostController,
    swipeFrom: TopLevelTab?,
    modifier: Modifier = Modifier,
    content: @Composable (slideTabs: () -> Boolean) -> Unit,
) {
    var swiping by remember { mutableStateOf(false) }
    var swipes by remember { mutableIntStateOf(0) }
    // Cleared once the slide has had time to start and finish, not when the back stack changes:
    // the navigation host reads the transition a frame after the stack moves, and a flag cleared
    // on the change itself was already gone by then.
    LaunchedEffect(swipes) {
        delay(SWIPE_FLAG_MS)
        swiping = false
    }
    val density = LocalDensity.current
    val gestures = WindowInsets.systemGestures
    val bounds =
        SwipeBounds(
            leftEdge = gestures.getLeft(density, LocalLayoutDirection.current).toFloat(),
            rightEdge = gestures.getRight(density, LocalLayoutDirection.current).toFloat(),
            distance = with(density) { SWIPE_DISTANCE.dp.toPx() },
            flick = with(density) { FLICK_DISTANCE.dp.toPx() },
            flickVelocity = with(density) { FLICK_VELOCITY.dp.toPx() },
        )
    // Read through the latest values, so the gesture handler is not restarted mid-swipe by every
    // recomposition that hands it a new lambda.
    val from by rememberUpdatedState(swipeFrom)
    val onSwipe: (Boolean) -> Unit =
        remember(navController) {
            { towardsNext ->
                from?.let { current ->
                    swipeTarget(current, towardsNext)?.let { to ->
                        swiping = true
                        swipes++
                        navController.selectTab(to, current)
                    }
                }
            }
        }
    Box(modifier = modifier.swipeBetweenTabs(enabled = swipeFrom != null, bounds = bounds, onSwipe = onSwipe)) {
        content { swiping }
    }
}

/** Where a swipe may start and how far, or how fast, it has to go: in pixels, and per second. */
private data class SwipeBounds(
    val leftEdge: Float,
    val rightEdge: Float,
    val distance: Float,
    val flick: Float,
    val flickVelocity: Float,
)

/**
 * A sideways drag that travels [SwipeBounds.distance], or a shorter quick flick, calls [onSwipe]
 * with whether it went right to left.
 */
private fun Modifier.swipeBetweenTabs(
    enabled: Boolean,
    bounds: SwipeBounds,
    onSwipe: (towardsNext: Boolean) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(bounds, onSwipe) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val x = down.position.x
            if (x < bounds.leftEdge || x > size.width - bounds.rightEdge) return@awaitEachGesture
            val velocity = VelocityTracker()
            velocity.addPointerInputChange(down)
            var travelled = 0f
            val drag =
                awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                    travelled = overSlop
                    velocity.addPointerInputChange(change)
                    change.consume()
                } ?: return@awaitEachGesture
            val completed =
                horizontalDrag(drag.id) { change ->
                    travelled += change.positionChange().x
                    velocity.addPointerInputChange(change)
                    change.consume()
                }
            if (!completed) return@awaitEachGesture
            val fast = abs(velocity.calculateVelocity().x) > bounds.flickVelocity
            if (abs(travelled) > bounds.distance || (fast && abs(travelled) > bounds.flick)) onSwipe(travelled < 0)
        }
    }
}

/** How far a finger has to travel sideways for a swipe to change tab, rather than be a wobble. */
private const val SWIPE_DISTANCE = 72

/** A quick flick changes tab with less travel, the way a pager does. */
private const val FLICK_DISTANCE = 24
private const val FLICK_VELOCITY = 600

/** Long enough for the slide to start and run; a tap on the capsule after it fades again. */
private const val SWIPE_FLAG_MS = 500L
