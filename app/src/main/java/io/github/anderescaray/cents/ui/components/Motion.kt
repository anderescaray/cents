package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.util.rememberReduceMotion

/** Things arriving: fast out, long settle. The film's one easing for anything appearing. */
val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** A value changing in place: a total counting, an arc re-balancing. */
val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** How long a card takes to rise into place, and how far apart two cards start. */
private const val ARRIVE_MS = 600
private const val ARRIVE_STAGGER_MS = 70
private const val ARRIVE_RISE_DP = 22f

/** How far a pressed thing gives under the thumb. */
private const val PRESSED_SCALE = 0.97f

/**
 * A card rising into place the first time a screen draws it, [index] places after the first.
 *
 * Only the first time: `remember` holds the animation, so scrolling a card back into view or a
 * recomposition does not replay it — motion explains a change (ADR 0003, rule 4), and coming
 * back to a card is not one. With animations off it is simply there.
 */
@Composable
fun Modifier.arrive(index: Int = 0): Modifier {
    if (rememberReduceMotion()) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            tween(
                durationMillis = ARRIVE_MS,
                delayMillis = index.coerceAtMost(MAX_STAGGERED) * ARRIVE_STAGGER_MS,
                easing = EaseOutExpo,
            ),
        )
    }
    val value = progress.value
    return this
        .offset(y = ((1f - value) * ARRIVE_RISE_DP).dp)
        .alpha(value)
}

private const val MAX_STAGGERED = 6

/** The scale of something held down: it gives a little, and springs back when let go. */
@Composable
fun rememberPressScale(pressed: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return scale
}

/**
 * A fraction from 0 to 1 that runs once when first drawn, for things that draw themselves on: the
 * ring, a budget bar. Already at 1 with animations off.
 */
@Composable
fun rememberDrawOn(durationMillis: Int = 900): State<Float> {
    val reduce = rememberReduceMotion()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) progress.animateTo(1f, tween(durationMillis, easing = EaseOutExpo))
    }
    return progress.asState()
}
