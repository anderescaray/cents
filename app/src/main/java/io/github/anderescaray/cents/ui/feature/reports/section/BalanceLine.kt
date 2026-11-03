package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.anderescaray.cents.domain.model.BalancePoint
import io.github.anderescaray.cents.ui.components.rememberDrawOn
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.formatLocale
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The balance as one clean line, the way a broker's app draws a portfolio: no grid and no axes,
 * a faint wash under the line, a dotted line at where it started, and three dates under it.
 *
 * **A finger on it reads it.** Pressing and sliding along the line shows a guide and a dot at the
 * day under the finger and reports that day through [onScrub], so the card's headline can say what
 * there was then; letting go reports null. A vertical drag is left to the list, so the page still
 * scrolls when a thumb lands on the chart on the way down.
 *
 * Drawn here rather than through the chart library, because reading a line by touch, and the look
 * of it, are the whole point of this card, and the library's marker is built for a label in a box.
 *
 * [points] is the line; [scrubbed] is the index of the point being read, or null.
 */
@Composable
internal fun BalanceLine(
    points: List<BalancePoint>,
    /** The line where it is above where it started, and where it is below. */
    above: Color,
    below: Color,
    scrubbed: Int?,
    onScrub: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val values = remember(points) { points.map { it.balance.minorUnits } }
    val bounds = remember(values) { yBounds(values) }
    val drawn by rememberDrawOn(durationMillis = DRAW_ON_MS)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = CentsTheme.colors.tertiaryLabel, fontSize = DATE_LABEL_SIZE)
    val locale = formatLocale()
    val dates =
        remember(points, locale) {
            val formatter = DateTimeFormatter.ofPattern(datePattern(points.size), locale)
            listOf(points.first(), points[points.size / 2], points.last()).map { it.date.format(formatter) }
        }
    val currentOnScrub by rememberUpdatedState(onScrub)
    val look =
        LineLook(
            above = above,
            below = below,
            guide = CentsTheme.colors.tertiaryLabel,
            card = CentsTheme.colors.groupedCard,
        )

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(LINE_HEIGHT.dp)
                .semantics { contentDescription = description }
                .scrubbing(points.size) { currentOnScrub(it) },
    ) {
        val chartHeight = size.height - LABEL_BAND.dp.toPx()
        val inset = DOT_RADIUS.dp.toPx() * 2
        val geometry =
            LineGeometry(size.width, top = inset, bottom = chartHeight - inset, bounds = bounds, count = values.size)
        val shown = ((values.size - 1) * drawn).roundToInt().coerceAtLeast(0)
        drawBalance(values, geometry, shown, look)
        scrubbed?.takeIf { it in values.indices }?.let { i ->
            drawReading(i, values[i], geometry, chartHeight, values.first(), look)
        }
        drawDates(dates, labelStyle, measurer, chartHeight)
    }
}

/**
 * Reports the point under a finger while it is down and sliding sideways, then null when it
 * lifts. A drag that turns out to be vertical is left to the list, which scrolls instead.
 */
private fun Modifier.scrubbing(
    count: Int,
    onScrub: (Int?) -> Unit,
): Modifier =
    pointerInput(count) {
        val at = { x: Float -> indexAt(x, size.width.toFloat(), count) }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onScrub(at(down.position.x))
            val drag =
                awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                    onScrub(at(change.position.x))
                }
            if (drag != null) {
                horizontalDrag(drag.id) { change ->
                    change.consume()
                    onScrub(at(change.position.x))
                }
            }
            onScrub(null)
        }
    }

/** The colours the line is drawn in: one above where it started, one below. */
private class LineLook(
    val above: Color,
    val below: Color,
    val guide: Color,
    val card: Color,
)

/** Where a value and a day land on the canvas. */
private class LineGeometry(
    val width: Float,
    val top: Float,
    val bottom: Float,
    private val bounds: Pair<Long, Long>,
    private val count: Int,
) {
    private val range = (bounds.second - bounds.first).toFloat()

    fun x(index: Int): Float = if (count > 1) index * width / (count - 1) else width / 2

    fun y(value: Long): Float = bottom - ((value - bounds.first).toFloat() / range) * (bottom - top)
}

/** The dotted start, the wash under the line, and the line itself up to [shown]. */
private fun DrawScope.drawBalance(
    values: List<Long>,
    geometry: LineGeometry,
    shown: Int,
    look: LineLook,
) {
    val line = Path()
    values.forEachIndexed { i, v ->
        if (i > shown) return@forEachIndexed
        if (i == 0) line.moveTo(0f, geometry.y(v)) else line.lineTo(geometry.x(i), geometry.y(v))
    }
    if (values.size == 1) line.lineTo(size.width, geometry.y(values[0]))
    val end = if (values.size == 1) size.width else geometry.x(shown)

    // Where it started, dotted: above it the money grew, below it it shrank.
    val start = geometry.y(values.first())
    drawLine(
        color = look.guide.copy(alpha = START_ALPHA),
        start = Offset(0f, start),
        end = Offset(size.width, start),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.dp.toPx(), DASH.dp.toPx())),
    )
    val area =
        Path().apply {
            addPath(line)
            lineTo(end, geometry.bottom)
            lineTo(0f, geometry.bottom)
            close()
        }
    // Drawn twice, each half clipped at the start: green where there is more than there was, red
    // where there is less, changing colour exactly where the line crosses the dotted one.
    val stroke = Stroke(width = LINE_WIDTH.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    listOf(look.above to (0f to start), look.below to (start to size.height)).forEach { (color, band) ->
        clipRect(top = band.first, bottom = band.second) {
            val wash =
                Brush.verticalGradient(
                    listOf(color.copy(alpha = AREA_TOP), Color.Transparent),
                    startY = geometry.top,
                    endY = geometry.bottom,
                )
            drawPath(area, wash)
            drawPath(line, color = color, style = stroke)
        }
    }
}

/** The guide and the dot at the day a finger is on. */
private fun DrawScope.drawReading(
    index: Int,
    value: Long,
    geometry: LineGeometry,
    chartHeight: Float,
    start: Long,
    look: LineLook,
) {
    val x = geometry.x(index)
    val y = geometry.y(value)
    val color = if (value >= start) look.above else look.below
    drawLine(look.guide.copy(alpha = GUIDE_ALPHA), Offset(x, 0f), Offset(x, chartHeight), strokeWidth = 1.dp.toPx())
    drawCircle(look.card, radius = DOT_RADIUS.dp.toPx() + 2.dp.toPx(), center = Offset(x, y))
    drawCircle(color, radius = DOT_RADIUS.dp.toPx(), center = Offset(x, y))
}

/** The first, middle and last dates, along the bottom edge. */
private fun DrawScope.drawDates(
    dates: List<String>,
    style: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
    chartHeight: Float,
) {
    val y = chartHeight + LABEL_GAP.dp.toPx()
    dates.forEachIndexed { index, text ->
        val layout = measurer.measure(text, style)
        val x =
            when (index) {
                0 -> 0f
                dates.lastIndex -> size.width - layout.size.width
                else -> (size.width - layout.size.width) / 2
            }
        drawText(layout, topLeft = Offset(x, y))
    }
}

/** The point under a finger [x] pixels across a chart [width] wide. */
internal fun indexAt(
    x: Float,
    width: Float,
    count: Int,
): Int {
    if (count <= 1 || width <= 0f) return 0
    return ((x / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/**
 * The lowest and highest the axis reaches: the values with a little room above and below, and,
 * for a line that never moved, a band around it so it sits in the middle instead of on an edge.
 */
internal fun yBounds(values: List<Long>): Pair<Long, Long> {
    val min = values.min()
    val max = values.max()
    val span = max - min
    val pad = if (span > 0) (span * Y_PADDING).toLong().coerceAtLeast(1) else maxOf(abs(max) / FLAT_SHARE, MINOR_UNIT)
    return (min - pad) to (max + pad)
}

/** A day for a month, a month for a year, and the year as well once the line is longer. */
private fun datePattern(days: Int): String =
    when {
        days > LONG_SPAN_DAYS -> "MMM yy"
        days > SHORT_SPAN_DAYS -> "MMM"
        else -> "d MMM"
    }

private const val LINE_HEIGHT = 196
private const val LABEL_BAND = 22
private const val LABEL_GAP = 6
private const val LINE_WIDTH = 2.5f
private const val DOT_RADIUS = 5
private const val DASH = 3
private const val AREA_TOP = 0.10f
private const val START_ALPHA = 0.5f
private const val GUIDE_ALPHA = 0.6f
private const val DRAW_ON_MS = 700
private const val Y_PADDING = 0.15
private const val FLAT_SHARE = 20L
private const val MINOR_UNIT = 100L
private const val SHORT_SPAN_DAYS = 62
private const val LONG_SPAN_DAYS = 400
private val DATE_LABEL_SIZE = 11.sp
