package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodComparison
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.repository.reports.PeriodComparisonRepository
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.LegendRow
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Am I doing better or worse than last time: this period's income and expenses beside the
 * previous period's, as opposing columns.
 *
 * The fourth view on the tab, and the first one built entirely on the seam #28 and #35 left
 * behind — one model, one repository interface, one Room implementation, this file, its own
 * strings, and single lines in `RepositoryModule`, `ReportSections` and the test factory.
 *
 * **The columns are the question and the rows are the answer.** Two pairs of bars show the
 * shape at a glance; the three rows underneath say the change in words, because a bar that is
 * slightly shorter than its neighbour does not tell anyone whether they saved forty euros or
 * four hundred.
 *
 * The tinting is not "up is green". Spending *more* is worse and earning more is better, so
 * each row carries which direction is good — see [ChangeRow]. Getting that uniform would make
 * a month of heavier spending glow green, which is the sort of chart that quietly teaches the
 * wrong thing.
 */
data class PeriodComparisonSection(
    val comparison: PeriodComparison,
) : ReportSectionState {
    override val key: String get() = KEY

    @Composable
    override fun Content(actions: ReportActions) {
        CentsListSection(
            header = stringResource(R.string.reports_comparison_header),
            // Said out loud, because the dates on the drill-down row will not be the dates in
            // the selector above and there is nothing else on screen to explain that.
            footer =
                stringResource(R.string.reports_comparison_partial)
                    .takeIf { comparison.ranges.partial && !comparison.isEmpty },
        ) {
            if (comparison.isEmpty) {
                // Two empty periods are not a chart of two zeroes: there is nothing to
                // compare, and saying so beats drawing a flat pair of bars.
                EmptyChart(stringResource(R.string.reports_comparison_empty))
            } else {
                Box(Modifier.padding(vertical = CentsDimens.spacingM)) {
                    ComparisonColumns(comparison)
                }
                LegendRow(
                    color = CentsTheme.colors.tertiaryLabel,
                    label = stringResource(R.string.reports_comparison_legend_before),
                    amount = comparison.before.net,
                )
                LegendRow(
                    color = CentsTheme.colors.accent,
                    label = stringResource(R.string.reports_comparison_legend_current),
                    amount = comparison.current.net,
                )
                ChangeRow(
                    label = stringResource(R.string.summary_income),
                    amount = comparison.current.income,
                    change = comparison.incomeChange,
                    moreIsBetter = true,
                )
                ChangeRow(
                    label = stringResource(R.string.summary_expenses),
                    amount = comparison.current.expenses,
                    change = comparison.expensesChange,
                    moreIsBetter = false,
                )
                ChangeRow(
                    label = stringResource(R.string.summary_net),
                    amount = comparison.current.net,
                    change = comparison.netChange,
                    moreIsBetter = true,
                )
                // The previous period is the only stretch on this tab that no other view can
                // reach: everything else reports on the period that is selected. So it gets
                // the drill-down, and this period keeps the one it already has everywhere.
                CentsListRow(
                    title = stringResource(R.string.reports_comparison_open_before),
                    subtitle = comparison.previous.label(),
                    showChevron = true,
                    showSeparator = false,
                    onClick = { actions.onDrillDown(comparison.filterForPrevious()) },
                )
            }
        }
    }

    companion object {
        const val KEY = "comparison"
    }
}

@Singleton
class PeriodComparisonSectionSource
    @Inject
    constructor(
        private val repository: PeriodComparisonRepository,
    ) : ReportSectionSource {
        override fun observe(scope: ReportScope): Flow<ReportSectionState> =
            repository.observePeriodComparison(scope).map(::PeriodComparisonSection)
    }

/**
 * One measure, its current value, and what it did since the previous period.
 *
 * [moreIsBetter] is the whole point of the row. Income going up and expenses going up are
 * both "a bigger number", and colouring them the same way would tell someone their worst
 * month was their best one.
 */
@Composable
private fun ChangeRow(
    label: String,
    amount: Money,
    change: Money,
    moreIsBetter: Boolean,
) {
    val tint =
        when {
            change.isZero -> CentsTheme.colors.secondaryLabel
            change.isNegative == moreIsBetter -> CentsTheme.colors.destructive
            else -> CentsTheme.colors.positive
        }

    CentsListRow(
        title = label,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MoneyFormatter.format(amount),
                    style = CentsTheme.typography.body,
                    color = CentsTheme.colors.label,
                )
                Text(
                    text =
                        if (change.isZero) {
                            stringResource(R.string.reports_comparison_unchanged)
                        } else {
                            MoneyFormatter.formatSigned(change)
                        },
                    style = CentsTheme.typography.footnote,
                    color = tint,
                )
            }
        },
    )
}

/**
 * Two pairs of columns: what came in, and what went out, each period beside the other.
 *
 * The previous period is grey and this one takes the accent, so the faded pair reads as the
 * past without needing the legend to say which is which. Both series are drawn even when one
 * period is empty — a missing column would read as missing data rather than as a period in
 * which nothing happened.
 */
@Composable
internal fun ComparisonColumns(
    comparison: PeriodComparison,
    modifier: Modifier = Modifier,
) {
    val past = CentsTheme.colors.tertiaryLabel
    val now = CentsTheme.colors.accent
    val labelColor = CentsTheme.colors.secondaryLabel
    val producer = remember { CartesianChartModelProducer() }

    LaunchedEffect(comparison) {
        producer.runTransaction {
            columnModel {
                series(comparison.before.income.minorUnits, comparison.before.expenses.minorUnits)
                series(comparison.current.income.minorUnits, comparison.current.expenses.minorUnits)
            }
        }
    }

    val axisLabels =
        listOf(
            stringResource(R.string.summary_income),
            stringResource(R.string.summary_expenses),
        )
    val labelStyle = TextStyle(color = labelColor, fontSize = AXIS_LABEL_SIZE)

    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider =
                        ColumnCartesianLayer.ColumnProvider.series(
                            rememberLineComponent(Fill(past), COLUMN_WIDTH),
                            rememberLineComponent(Fill(now), COLUMN_WIDTH),
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
                                axisLabels.getOrElse(value.toInt()) { "" }
                            },
                    ),
            ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(COLUMN_CHART_HEIGHT),
    )
}

/**
 * The previous period, written out.
 *
 * Both ends are shown rather than a name like "last month", because the period this compares
 * against depends on what is selected above, and "last month" beside a custom range would be
 * wrong in a way nothing on screen corrects.
 */
@Composable
private fun ReportPeriod.label(): String {
    val formatter = remember { DateTimeFormatter.ofPattern(RANGE_LABEL_PATTERN, Locale.getDefault()) }
    return "${from.format(formatter)} – ${to.format(formatter)}"
}

private val COLUMN_CHART_HEIGHT = 180.dp
private val COLUMN_WIDTH = 14.dp
private val AXIS_LABEL_SIZE = 11.sp

/** "4 Aug", in whatever the device locale calls August. */
private const val RANGE_LABEL_PATTERN = "d MMM"

/** Cents stores money in minor units; an axis is labelled in whole ones. */
private const val MINOR_UNITS_PER_UNIT = 100.0
