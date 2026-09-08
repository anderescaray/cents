package io.github.anderescaray.cents.ui.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.BubblePlacement
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.rememberReduceMotion
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The whole expansion, well inside the half second a launcher is allowed to take. */
internal const val EXPAND_DURATION_MS = 340

private const val DRIFT_PERIOD_MS = 5_200
private const val DRIFT_STAGGER_MS = 900
private const val TAU = (2 * Math.PI).toFloat()
private val DriftAmplitude = 5.dp
private val BubbleShadow = 10.dp

/**
 * The home canvas: one bubble per section, dragged and pinched wherever the owner wants them,
 * drifting gently, and expanding into the section when tapped.
 *
 * The canvas is a launcher, not a replacement for the tab bar — inside a section the bar is
 * still there. That is what makes this affordable: every screen already built keeps working,
 * and this is the way in rather than a new way to be in. See
 * `docs/decisions/0002-bubble-launcher.md`.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onMove: (TopLevelTab, BubblePlacement) -> Unit,
    onOpenSection: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    var opening by remember { mutableStateOf<Opening?>(null) }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                // The canvas is the safe area, not the window: the app draws edge to edge, and
                // nothing else stands between a bubble and the status bar clock. Placements are
                // fractions *of this*, so what fits here stays reachable — a bubble dragged to
                // the bottom edge of the window would hand its horizontal drag to the system
                // gesture bar.
                .windowInsetsPadding(WindowInsets.systemBars)
                .semantics { isTraversalGroup = true },
    ) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()
        val aspect = if (canvasHeight > 0f) canvasWidth / canvasHeight else 1f

        // Nothing until the store has answered: bubbles drawn at their defaults and corrected a
        // frame later would jump, and a launcher that twitches on every launch reads as broken.
        if (state.loaded) {
            state.bubbles.forEachIndexed { index, bubble ->
                key(bubble.tab) {
                    Bubble(
                        bubble = bubble,
                        index = index,
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight,
                        aspect = aspect,
                        drifting = !reduceMotion,
                        // A second tap during the expansion would move the shape to another
                        // bubble while still arriving at the first one.
                        enabled = opening == null,
                        onMove = onMove,
                        onOpen = { placement ->
                            if (reduceMotion) {
                                onOpenSection(bubble.tab)
                            } else {
                                opening = Opening(bubble.tab, placement)
                            }
                        },
                    )
                }
            }
        }

        val expanding = opening
        if (expanding != null) {
            ExpandingBubble(
                opening = expanding,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                onArrived = onOpenSection,
            )
        }
    }
}

/**
 * The bubble that was tapped and where it was at that moment — its *drawn* placement, drift
 * aside, so a bubble tapped straight after being dragged grows from where the finger left it
 * rather than from where the store has caught up to.
 */
private data class Opening(
    val tab: TopLevelTab,
    val from: BubblePlacement,
)

/**
 * One bubble. Drag moves it, a pinch resizes it, a tap opens it.
 *
 * The gesture writes to a draft rather than to the store, and only the *end* of the gesture is
 * saved: a drag emits every frame, and a store round-trip per frame would both hammer the disk
 * and hand the finger back a position from two frames ago. The draft outlives the write that
 * saves it — dropping it the moment the gesture ends would show the stored position for the
 * frames the write takes, which is a bubble springing back and then forward again.
 */
@Composable
private fun Bubble(
    bubble: HomeBubble,
    index: Int,
    canvasWidth: Float,
    canvasHeight: Float,
    aspect: Float,
    drifting: Boolean,
    enabled: Boolean,
    onMove: (TopLevelTab, BubblePlacement) -> Unit,
    onOpen: (BubblePlacement) -> Unit,
) {
    val density = LocalDensity.current
    var draft by remember { mutableStateOf<BubblePlacement?>(null) }
    val placement = (draft ?: bubble.placement).fittedTo(aspect)

    val transform =
        rememberTransformableState { zoomChange, panChange, _ ->
            val from = draft ?: bubble.placement
            draft =
                BubblePlacement(
                    centerX = from.centerX + panChange.x / canvasWidth,
                    centerY = from.centerY + panChange.y / canvasHeight,
                    diameter = from.diameter * zoomChange,
                ).fittedTo(aspect)
        }
    LaunchedEffect(transform.isTransformInProgress) {
        if (!transform.isTransformInProgress) {
            draft?.let { onMove(bubble.tab, it) }
        }
    }
    LaunchedEffect(bubble.placement) {
        // The stored layout changed for a reason other than the write just sent — Settings
        // resetting it, most likely — so the draft is stale and the canvas is right. A gesture
        // still under a finger is never overruled.
        if (!transform.isTransformInProgress && bubble.placement != draft) draft = null
    }

    val diameterPx = placement.diameter * minOf(canvasWidth, canvasHeight)
    val drift = driftOffset(index = index, enabled = drifting, amplitude = with(density) { DriftAmplitude.toPx() })
    val label = stringResource(bubble.tab.labelRes)

    Box(
        modifier =
            Modifier
                // Read inside the layout lambda, not in composition: the drift changes every
                // frame, and reading it up here would recompose the whole bubble — modifiers,
                // shadow, face and all — sixty times a second on the app's first screen.
                .offset {
                    IntOffset(
                        x = (placement.centerX * canvasWidth - diameterPx / 2f + drift.value.x).roundToInt(),
                        y = (placement.centerY * canvasHeight - diameterPx / 2f + drift.value.y).roundToInt(),
                    )
                }.size(with(density) { diameterPx.toDp() })
                .shadow(elevation = BubbleShadow, shape = CircleShape)
                .background(color = CentsTheme.colors.groupedCard, shape = CircleShape)
                .transformable(state = transform)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClickLabel = label,
                    role = Role.Button,
                    onClick = { onOpen(placement) },
                ).semantics { traversalIndex = index.toFloat() }
                .testTag(bubbleTag(bubble.tab)),
        contentAlignment = Alignment.Center,
    ) {
        BubbleFace(tab = bubble.tab, label = label)
    }
}

/**
 * What a bubble says it is: the tab's own icon over its own label. The icon is not described to
 * a screen reader, because the bubble itself already carries the name — announcing both would
 * read the section twice.
 */
@Composable
private fun BubbleFace(
    tab: TopLevelTab,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(tab.iconRes),
            contentDescription = null,
            tint = CentsTheme.colors.accent,
            modifier = Modifier.size(30.dp),
        )
        Text(text = label, style = CentsTheme.typography.footnote, color = CentsTheme.colors.label)
    }
}

/**
 * The bubble becoming the section: one shape growing to fill the screen, its corners opening
 * out and its colour arriving on the section's own background, so nothing cross-fades.
 *
 * The section is navigated to when the shape lands, not when the tap happens, which is why the
 * duration is the whole budget rather than the start of one.
 */
@Composable
private fun ExpandingBubble(
    opening: Opening,
    canvasWidth: Float,
    canvasHeight: Float,
    onArrived: (TopLevelTab) -> Unit,
) {
    val placement = opening.from
    val progress = remember(opening) { Animatable(0f) }
    LaunchedEffect(opening) {
        progress.animateTo(1f, animationSpec = tween(EXPAND_DURATION_MS, easing = FastOutSlowInEasing))
        onArrived(opening.tab)
    }

    val from = CentsTheme.colors.groupedCard
    val to = CentsTheme.colors.groupedBackground
    Canvas(
        // Nothing behind this is tappable while it runs. The bubbles are already refusing
        // clicks; this stops a stray tap landing on whatever the shape has grown over.
        modifier =
            Modifier.fillMaxSize().pointerInput(
                Unit,
            ) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        val fraction = progress.value
        val diameter = placement.diameter * minOf(canvasWidth, canvasHeight)
        val width = lerpFloat(diameter, canvasWidth, fraction)
        val height = lerpFloat(diameter, canvasHeight, fraction)
        val centerX = lerpFloat(placement.centerX * canvasWidth, canvasWidth / 2f, fraction)
        val centerY = lerpFloat(placement.centerY * canvasHeight, canvasHeight / 2f, fraction)
        drawRoundRect(
            color = lerp(from, to, fraction),
            topLeft = Offset(centerX - width / 2f, centerY - height / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(lerpFloat(diameter / 2f, 0f, fraction)),
        )
    }
}

/**
 * The small continuous motion that makes the canvas feel alive, staggered per bubble so the four
 * do not breathe in unison. Off means *not started*, not an amplitude of zero: an animation
 * still running invisibly is one the device asked not to run.
 *
 * Handed back as a [State] rather than as a value, so the caller can read it where the offset is
 * used — in the layout phase — instead of in composition, which would recompose the bubble on
 * every frame of it forever.
 */
@Composable
private fun driftOffset(
    index: Int,
    enabled: Boolean,
    amplitude: Float,
): State<Offset> {
    if (!enabled) return remember { mutableStateOf(Offset.Zero) }
    val transition = rememberInfiniteTransition(label = "bubble-drift")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(DRIFT_PERIOD_MS + index * DRIFT_STAGGER_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "bubble-drift-phase",
    )
    return remember(amplitude, index) {
        derivedStateOf {
            val angle = phase * TAU + index
            Offset(x = cos(angle) * amplitude * HORIZONTAL_DRIFT, y = sin(angle) * amplitude)
        }
    }
}

private const val HORIZONTAL_DRIFT = 0.6f

private fun lerpFloat(
    start: Float,
    stop: Float,
    fraction: Float,
): Float = start + (stop - start) * fraction

internal fun bubbleTag(tab: TopLevelTab) = "bubble-${tab.name}"
