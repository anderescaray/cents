package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.MonthlyFlow
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.repository.reports.MonthlyFlowRepository
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.LegendRow
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.feature.reports.total
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Income against expenses, one pair of columns per month of the period.
 *
 * The second of the two views the tab started with, moved into a section unchanged.
 */
data class MonthlyFlowSection(
    val months: List<MonthlyFlow>,
) : ReportSectionState {
    override val key: String get() = KEY

    @Composable
    override fun Content(actions: ReportActions) {
        CentsListSection(header = stringResource(R.string.reports_flow_header)) {
            if (months.isEmpty() || months.all { it.isEmpty }) {
                EmptyChart(stringResource(R.string.reports_flow_empty))
            } else {
                Box(Modifier.padding(vertical = CentsDimens.spacingM)) { MonthlyFlowChart(months) }
                LegendRow(
                    color = CentsTheme.colors.positive,
                    label = stringResource(R.string.reports_flow_legend_income),
                    amount = months.map { it.summary.income }.total(),
                )
                LegendRow(
                    color = CentsTheme.colors.destructive,
                    label = stringResource(R.string.reports_flow_legend_expenses),
                    amount = months.map { it.summary.expenses }.total(),
                    last = true,
                )
            }
        }
    }

    companion object {
        const val KEY = "flow"
    }
}

@Singleton
class MonthlyFlowSectionSource
    @Inject
    constructor(
        private val repository: MonthlyFlowRepository,
    ) : ReportSectionSource {
        override fun observe(
            period: ReportPeriod,
            currency: String,
        ): Flow<ReportSectionState> = repository.observeMonthlyFlow(period, currency).map(::MonthlyFlowSection)
    }

/**
 * Green for money in and red for money out, from the same two tokens the transaction list
 * uses for amounts, so a colour means the same thing on both screens. Vico's own theme is
 * overridden rather than themed: its defaults come from Material, and a purple column would
 * be exactly the "accent that came from nowhere" ADR 0001 rules out.
 */
@Composable
internal fun MonthlyFlowChart(
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

private val COLUMN_CHART_HEIGHT = 180.dp
private val COLUMN_WIDTH = 10.dp
private val AXIS_LABEL_SIZE = 11.sp

/** Three-letter month names: "Aug", "Ago" — whatever the device locale calls it. */
private const val MONTH_LABEL_PATTERN = "LLL"

/** Cents stores money in minor units; an axis is labelled in whole ones. */
private const val MINOR_UNITS_PER_UNIT = 100.0
