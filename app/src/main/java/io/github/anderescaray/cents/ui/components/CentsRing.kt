package io.github.anderescaray.cents.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.PI
import kotlin.math.min

/** The stroke as a share of the diameter: the film's "about a tenth". */
private const val STROKE_FRACTION = 0.1f

/** The gap between two arcs, in degrees. */
private const val GAP_DEGREES = 7f
private const val FULL_CIRCLE = 360f
private const val TOP = -90f

/**
 * The ring of category colours that says where the money went (ADR 0003): one rounded arc per
 * slice, clockwise from twelve, largest first, with a gap between neighbours. An arc too short
 * to hold its own round caps is drawn as a dot rather than a smear.
 *
 * It draws itself on the first time it appears. It is decorative to TalkBack: the list beside it
 * says the same in words, and a ring has no way to be read aloud that a list does not do better.
 * [content] sits in the middle — the total, usually.
 */
@Composable
fun CentsRing(
    slices: List<RingSlice>,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val drawn by rememberDrawOn(durationMillis = 1100)
    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val stroke = size.minDimension * STROKE_FRACTION
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val radius = diameter / 2f
            // What the round caps add at each end, in degrees: they eat into the gap otherwise.
            val capDegrees = (stroke / 2f / radius) * (180f / PI.toFloat())

            if (trackColor != Color.Transparent || slices.isEmpty()) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = FULL_CIRCLE,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
            }

            val visible = slices.filter { it.share > 0f }
            val gap = if (visible.size > 1) GAP_DEGREES else 0f
            var start = TOP
            visible.forEach { slice ->
                val whole = slice.share * FULL_CIRCLE
                val sweep = (whole - gap - capDegrees * 2f) * drawn
                if (visible.size == 1) {
                    drawArc(slice.color, TOP, FULL_CIRCLE * drawn, false, topLeft, arcSize, style = Stroke(stroke))
                } else if (sweep <= 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = start + whole / 2f,
                        sweepAngle = 0.01f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        alpha = drawn,
                    )
                } else {
                    drawArc(
                        color = slice.color,
                        startAngle = start + gap / 2f + capDegrees,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                start += whole
            }
        }
        content()
    }
}

/**
 * A budget's track: blue while there is room, amber from 80 %, red past the limit, with a hairline
 * at 80 % so the threshold is visible before it is crossed. It fills from empty when first drawn.
 */
@Composable
fun BudgetBar(
    fraction: Float,
    color: Color,
    trackColor: Color,
    markColor: Color,
    modifier: Modifier = Modifier,
    markAt: Float = 0.8f,
) {
    val drawn by rememberDrawOn(durationMillis = 1200)
    val target by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600, easing = EaseInOut), label = "budgetFill")
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val height = size.height
        val radius = height / 2f
        drawRoundRect(
            color = trackColor,
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(radius),
        )
        val width = size.width * target * drawn
        if (width > 0f) {
            drawRoundRect(
                color = color,
                size = Size(maxOf(width, min(height, size.width)), height),
                cornerRadius = CornerRadius(radius),
            )
        }
        val markX = size.width * markAt
        drawLine(
            color = markColor,
            start = Offset(markX, -height * 0.4f),
            end = Offset(markX, height * 1.4f),
            strokeWidth = 1.5f,
        )
    }
}
