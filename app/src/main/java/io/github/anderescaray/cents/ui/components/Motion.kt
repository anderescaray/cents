package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.util.rememberReduceMotion

/** Things arriving: fast out, long settle. The film's one easing for anything appearing. */
val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Moving from one place to another: a push, a pop. Quick to leave, and soft as it lands. */
val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Things leaving: they gather speed and are gone, rather than drifting off slowly. */
val EaseInSharp = CubicBezierEasing(0.4f, 0f, 1f, 1f)

/** A value changing in place: a total counting, an arc re-balancing. */
val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** How long a card takes to rise into place, and how far apart two cards start. */
private const val ARRIVE_MS = 460
private const val ARRIVE_STAGGER_MS = 45
private const val ARRIVE_RISE_DP = 14f

/** How far a pressed thing gives under the thumb. */
private const val PRESSED_SCALE = 0.96f
private const val PRESS_DAMPING = 0.7f

/**
 * A card rising into place the first time a screen draws it, [index] places after the first.
 *
 * Once per screen, not once per composition: the flag is saved with the screen, so coming back
 * to it from a form — or scrolling a card back into view — does not replay the entrance. Motion
 * explains a change (ADR 0003, rule 4), and returning is not one. With animations off it is
 * simply there.
 *
 * The fade multiplies each draw by the alpha rather than drawing into an offscreen layer: a layer
 * is clipped to the card's bounds, and the card's soft shadow, which spills outside them, would
 * show hard straight edges for as long as the fade ran.
 */
@Composable
fun Modifier.arrive(index: Int = 0): Modifier {
    if (rememberReduceMotion()) return this
    var arrived by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (arrived) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (arrived) return@LaunchedEffect
        progress.animateTo(
            1f,
            tween(
                durationMillis = ARRIVE_MS,
                delayMillis = index.coerceAtMost(MAX_STAGGERED) * ARRIVE_STAGGER_MS,
                easing = EaseOutExpo,
            ),
        )
        arrived = true
    }
    return graphicsLayer {
        val value = progress.value
        alpha = value
        translationY = (1f - value) * ARRIVE_RISE_DP.dp.toPx()
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

private const val MAX_STAGGERED = 6

/** The scale of something held down: it gives a little, and springs back when let go. */
@Composable
fun rememberPressScale(pressed: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        // Firm rather than bouncy: it answers the thumb at once and settles without a wobble.
        animationSpec = spring(dampingRatio = PRESS_DAMPING, stiffness = Spring.StiffnessMediumLow * 2),
        label = "pressScale",
    )
    return scale
}

/**
 * A fraction from 0 to 1 that runs once when first drawn, for things that draw themselves on: the
 * ring, a budget bar. Already at 1 with animations off, and on every visit after the first.
 */
@Composable
fun rememberDrawOn(durationMillis: Int = 900): State<Float> {
    val reduce = rememberReduceMotion()
    // Saved with the screen, like [arrive]: coming back to it is not a reason to draw it again.
    var drawn by rememberSaveable { mutableStateOf(reduce) }
    val progress = remember { Animatable(if (drawn) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (drawn) return@LaunchedEffect
        progress.animateTo(1f, tween(durationMillis, easing = EaseOutExpo))
        drawn = true
    }
    return progress.asState()
}
