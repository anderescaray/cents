package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.ChartPoint
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.formatLocale
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Currency
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * What an account, an asset or everything was worth through time, by week, month or year, with
 * the scale chosen above it (`DECISIONS.md` D40). The scale is remembered by whoever shows this,
 * so the chart reopens where it was left.
 *
 * One line in the accent colour: unlike the balance line in Reports, there is no level to be above
 * or below, only how the money moved.
 */
@Composable
fun InvestmentChart(
    points: List<ChartPoint>,
    scale: ChartScale,
    onScaleChange: (ChartScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    val week = stringResource(R.string.chart_scale_week)
    val month = stringResource(R.string.chart_scale_month)
    val year = stringResource(R.string.chart_scale_year)
    CentsListSection(header = stringResource(R.string.chart_header), modifier = modifier) {
        Column(Modifier.padding(CentsDimens.spacingM)) {
            CentsSegmentedControl(
                options = ChartScale.entries,
                selected = scale,
                onSelect = onScaleChange,
                label = { option ->
                    when (option) {
                        ChartScale.WEEK -> week
                        ChartScale.MONTH -> month
                        ChartScale.YEAR -> year
                    }
                },
                modifier = Modifier.testTag(CHART_SCALE_TAG),
            )
            if (points.size < 2) {
                // One point is not a line, and drawing it as a dot says less than this does.
                Text(
                    text = stringResource(R.string.chart_empty),
                    style = CentsTheme.typography.footnote,
                    color = CentsTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = CentsDimens.spacingM),
                )
            } else {
                ValueLine(points, scale, Modifier.padding(top = CentsDimens.spacingM))
            }
        }
    }
}

@Composable
private fun ValueLine(
    points: List<ChartPoint>,
    scale: ChartScale,
    modifier: Modifier = Modifier,
) {
    val accent = CentsTheme.colors.accent
    val labelColor = CentsTheme.colors.secondaryLabel
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        producer.runTransaction { lineModel { series(points.map { it.value.minorUnits }) } }
    }
    val labels =
        remember(points, scale) {
            val formatter = DateTimeFormatter.ofPattern(scale.labelPattern, formatLocale())
            points.map { it.start.format(formatter) }
        }
    // Whole units of the currency, grouped the way this phone writes numbers (rule 1).
    val axis =
        remember(points) {
            val code = points.first().value.currency
            val digits = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrNull() ?: 2
            AxisFormat(NumberFormat.getIntegerInstance(formatLocale()), 10.0.pow(digits.coerceAtLeast(0)))
        }
    val labelStyle = TextStyle(color = labelColor, fontSize = AXIS_LABEL_SIZE)
    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider =
                        LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(Fill(accent)),
                                areaFill = LineCartesianLayer.AreaFill.single(Fill(accent.copy(alpha = AREA_ALPHA))),
                            ),
                        ),
                    rangeProvider = FittedRange,
                ),
                startAxis =
                    VerticalAxis.rememberStart(
                        label = rememberAxisLabelComponent(style = labelStyle),
                        valueFormatter = CartesianValueFormatter { _, value, _ -> axis.format(value) },
                    ),
                bottomAxis =
                    HorizontalAxis.rememberBottom(
                        label = rememberAxisLabelComponent(style = labelStyle),
                        valueFormatter =
                            CartesianValueFormatter {
                                _,
                                value,
                                _,
                                ->
                                labels.getOrElse(value.toInt()) { "" }
                            },
                        itemPlacer =
                            HorizontalAxis.ItemPlacer.aligned(
                                { max(1, labels.size / TARGET_AXIS_LABELS) },
                            ),
                    ),
            ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT).testTag(CHART_TAG),
    )
}

/** Minor units on the axis as whole units of the currency. */
private class AxisFormat(
    private val numbers: NumberFormat,
    private val minorPerUnit: Double,
) {
    fun format(minor: Double): String = numbers.format((minor / minorPerUnit).roundToLong())
}

/** How a point's period is named under the chart. */
private val ChartScale.labelPattern: String
    get() =
        when (this) {
            ChartScale.WEEK -> "d MMM"
            ChartScale.MONTH -> "MMM yy"
            ChartScale.YEAR -> "yyyy"
        }

/**
 * The y-axis fits the values rather than starting at zero, the same reasoning as the balance line
 * in Reports: what moved is the answer, and the figures above the chart say the level.
 */
private object FittedRange : CartesianLayerRangeProvider {
    override fun getMinY(
        minY: Double,
        maxY: Double,
        extraStore: ExtraStore,
    ): Double = minY - padding(minY, maxY)

    override fun getMaxY(
        minY: Double,
        maxY: Double,
        extraStore: ExtraStore,
    ): Double = maxY + padding(minY, maxY)

    private fun padding(
        minY: Double,
        maxY: Double,
    ): Double {
        val span = maxY - minY
        if (span > 0) return span * RANGE_PADDING
        return max(abs(maxY) * RANGE_PADDING, MINOR_UNITS_PER_UNIT)
    }
}

internal const val CHART_TAG = "investmentChart"
internal const val CHART_SCALE_TAG = "investmentChartScale"

private val CHART_HEIGHT = 180.dp
private val AXIS_LABEL_SIZE = 11.sp
private const val MINOR_UNITS_PER_UNIT = 100.0
private const val TARGET_AXIS_LABELS = 5
private const val RANGE_PADDING = 0.1
private const val AREA_ALPHA = 0.16f
