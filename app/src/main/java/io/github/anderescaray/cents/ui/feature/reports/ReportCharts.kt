package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import io.github.anderescaray.cents.domain.model.MonthlyFlow
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Spending by category, as a ring.
 *
 * The slice colours are the owner's own — the colour they gave each category in Settings —
 * rather than a palette the chart library chose, so the donut and the list beneath it agree
 * about what "Groceries" looks like. Only the uncategorized slice has no colour of its own,
 * and it takes the tertiary label grey: it is money without a label, and it should read as
 * absence rather than as one more category.
 */
@Composable
fun SpendingDonut(
    breakdown: SpendingBreakdown,
    modifier: Modifier = Modifier,
) {
    val fallback = CentsTheme.colors.tertiaryLabel
    val producer = remember { PieChartModelProducer() }

    // Keyed on the breakdown, so the model is rebuilt when the totals change and not on
    // every recomposition. The adding up is already done; re-running the transaction while
    // the screen scrolls is how a chart starts costing frames.
    LaunchedEffect(breakdown) {
        if (breakdown.isEmpty) return@LaunchedEffect
        producer.runTransaction {
            pieSeries { series(breakdown.slices.map { it.total.minorUnits }) }
        }
    }

    if (breakdown.isEmpty) return

    val slices =
        remember(breakdown, fallback) {
            breakdown.slices.map { slice ->
                PieChart.Slice(fill = Fill(slice.colorArgb?.let { Color(it) } ?: fallback))
            }
        }

    PieChartHost(
        chart =
            rememberPieChart(
                sliceProvider = PieChart.SliceProvider.series(slices),
                spacing = SLICE_SPACING,
                // A ring rather than a filled circle: the hole is what stops five similar
                // colours reading as one blob.
                innerSize = PieSize.Inner.fixed(DONUT_HOLE),
            ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(DONUT_HEIGHT),
    )
}

/**
 * Income against expenses, one pair of columns per month.
 *
 * Green for money in and red for money out, from the same two tokens the transaction list
 * uses for amounts, so a colour means the same thing on both screens. Vico's own theme is
 * overridden rather than themed: its defaults come from Material, and a purple column would
 * be exactly the "accent that came from nowhere" ADR 0001 rules out.
 */
@Composable
fun MonthlyFlowChart(
    months: List<MonthlyFlow>,
    modifier: Modifier = Modifier,
) {
    val positive = CentsTheme.colors.positive
    val destructive = CentsTheme.colors.destructive
    val labelColor = CentsTheme.colors.secondaryLabel
    val producer = remember { CartesianChartModelProducer() }

    LaunchedEffect(months) {
        if (months.isEmpty()) return@LaunchedEffect
        producer.runTransaction {
            columnModel {
                series(months.map { it.summary.income.minorUnits })
                series(months.map { it.summary.expenses.minorUnits })
            }
        }
    }

    if (months.isEmpty()) return

    val labels =
        remember(months) {
            val formatter = DateTimeFormatter.ofPattern(MONTH_LABEL_PATTERN, Locale.getDefault())
            months.map { it.month.atDay(1).format(formatter) }
        }
    val labelStyle = TextStyle(color = labelColor, fontSize = AXIS_LABEL_SIZE)

    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider =
                        ColumnCartesianLayer.ColumnProvider.series(
                            rememberLineComponent(Fill(positive), COLUMN_WIDTH),
                            rememberLineComponent(Fill(destructive), COLUMN_WIDTH),
                        ),
                ),
                startAxis =
                    VerticalAxis.rememberStart(
                        label = rememberAxisLabelComponent(style = labelStyle),
                        // Minor units on an axis would read as a hundred times the money.
                        valueFormatter =
                            CartesianValueFormatter { _, value, _ ->
                                (value / MINOR_UNITS_PER_UNIT).toLong().toString()
                            },
                    ),
                bottomAxis =
                    HorizontalAxis.rememberBottom(
                        label = rememberAxisLabelComponent(style = labelStyle),
                        valueFormatter =
                            CartesianValueFormatter { _, value, _ ->
                                labels.getOrElse(value.toInt()) { "" }
                            },
                    ),
            ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(COLUMN_CHART_HEIGHT),
    )
}

/** What a chart says when the period it was given holds nothing. */
@Composable
fun EmptyChart(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(EMPTY_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = CentsTheme.typography.subhead,
            color = CentsTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = CentsDimens.spacingL),
        )
    }
}

private val DONUT_HEIGHT = 220.dp
private val DONUT_HOLE = 104.dp
private val SLICE_SPACING = 2.dp
private val COLUMN_CHART_HEIGHT = 180.dp
private val COLUMN_WIDTH = 10.dp
private val EMPTY_HEIGHT = 120.dp
private val AXIS_LABEL_SIZE = 11.sp

/** Three-letter month names: "Aug", "Ago" — whatever the device locale calls it. */
private const val MONTH_LABEL_PATTERN = "LLL"

/** Cents stores money in minor units; an axis is labelled in whole ones. */
private const val MINOR_UNITS_PER_UNIT = 100.0
