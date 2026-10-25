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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import kotlin.math.abs

/**
 * The tab a horizontal swipe leads to: the next one when the finger travelled towards the start
 * (right to left), the previous one when it travelled towards the end, and none past either end.
 */
internal fun swipeTarget(
    current: TopLevelTab,
    towardsNext: Boolean,
): TopLevelTab? = TopLevelTab.entries.getOrNull(current.ordinal + if (towardsNext) 1 else -1)

/**
 * Whether the tab change under way came from a swipe, so it slides the way the finger went rather
 * than cross-fading as a tap on the capsule does. Read by the transitions when they start, and
 * cleared once the new tab is the current one.
 */
@Stable
internal class TabSwipe {
    var swiping by mutableStateOf(false)
}

@Composable
internal fun rememberTabSwipe(): TabSwipe = remember { TabSwipe() }

/**
 * Swiping sideways on a tab's own screen moves to the tab beside it.
 *
 * Anything underneath that takes a sideways drag itself — a row that swipes to delete, a strip of
 * chips — gets it first, because a parent sees a gesture only after its children have passed on
 * it. A swipe that starts in the system's gesture area at either edge is Android's back gesture,
 * and is left to it.
 */
@Composable
internal fun Modifier.swipeBetweenTabs(
    enabled: Boolean,
    onSwipe: (towardsNext: Boolean) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val gestures = WindowInsets.systemGestures
    val edgeStart = gestures.getLeft(density, direction).toFloat()
    val edgeEnd = gestures.getRight(density, direction).toFloat()
    val distance = with(density) { SWIPE_DISTANCE.dp.toPx() }
    val swipe by rememberUpdatedState(onSwipe)
    if (!enabled) return this
    return pointerInput(edgeStart, edgeEnd, distance) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val x = down.position.x
            if (x < edgeStart || x > size.width - edgeEnd) return@awaitEachGesture
            var travelled = 0f
            val drag =
                awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                    travelled = overSlop
                    change.consume()
                } ?: return@awaitEachGesture
            val completed =
                horizontalDrag(drag.id) { change ->
                    travelled += change.positionChange().x
                    change.consume()
                }
            if (completed && abs(travelled) > distance) swipe(travelled < 0)
        }
    }
}

/**
 * The screens, with a sideways swipe moving to the tab beside [swipeFrom]; null, as on anything
 * pushed above a tab's own screen, turns the swipe off. [content] is told whether the tab change
 * under way came from a swipe, so it can slide rather than fade.
 */
@Composable
internal fun SwipeableTabs(
    navController: NavHostController,
    swipeFrom: TopLevelTab?,
    modifier: Modifier = Modifier,
    content: @Composable (slideTabs: () -> Boolean) -> Unit,
) {
    val swipe = rememberTabSwipe()
    LaunchedEffect(swipeFrom) { swipe.swiping = false }
    Box(
        modifier =
            modifier.swipeBetweenTabs(enabled = swipeFrom != null) { towardsNext ->
                val from = swipeFrom ?: return@swipeBetweenTabs
                swipeTarget(from, towardsNext)?.let { to ->
                    swipe.swiping = true
                    navController.selectTab(to, from)
                }
            },
    ) {
        content { swipe.swiping }
    }
}

/** How far a finger has to travel sideways for a swipe to change tab, rather than be a wobble. */
private const val SWIPE_DISTANCE = 72
