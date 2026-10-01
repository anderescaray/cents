package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.ComparisonSegment
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodComparison
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.repository.reports.PeriodComparisonRepository
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.LegendRow
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Am I doing better or worse than last time: this period's income and expenses beside the
 * comparison period's (previous period or same period last year), as opposing columns.
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
 *
 * A segmented control allows toggling between the previous period and the same period last year,
 * on the shape the ring's Expense/Income toggle established. Both comparisons are read at once
 * rather than re-querying on flip, keeping transitions instantaneous and the two views in sync.
 */
data class PeriodComparisonSection(
    val previous: PeriodComparison,
    val samePeriodLastYear: PeriodComparison = previous,
) : ReportSectionState {
    override val key: String get() = KEY

    val comparison: PeriodComparison get() = previous

    fun comparisonFor(segment: ComparisonSegment): PeriodComparison =
        when (segment) {
            ComparisonSegment.PREVIOUS_PERIOD -> previous
            ComparisonSegment.SAME_PERIOD_LAST_YEAR -> samePeriodLastYear
        }

    @Composable
    override fun Content(actions: ReportActions) {
        var segment by rememberSaveable { mutableStateOf(ComparisonSegment.PREVIOUS_PERIOD) }
        val comparison = comparisonFor(segment)
        val labels = SEGMENTS.associateWith { stringResource(it.labelRes) }

        Column {
            CentsListSection(
                header = stringResource(segment.headerRes),
                // Said out loud, because the dates on the drill-down row will not be the dates in
                // the selector above and there is nothing else on screen to explain that.
                footer =
                    stringResource(segment.partialRes)
                        .takeIf { comparison.ranges.partial && !comparison.isEmpty },
            ) {
                CentsSegmentedControl(
                    options = SEGMENTS,
                    selected = segment,
                    onSelect = { segment = it },
                    label = { labels.getValue(it) },
                    modifier =
                        Modifier
                            .padding(horizontal = CentsDimens.rowHorizontalPadding, vertical = CentsDimens.spacingS)
                            .testTag(SEGMENT_CONTROL_TAG),
                )
                if (comparison.isEmpty) {
                    // Two empty periods are not a chart of two zeroes: there is nothing to
                    // compare, and saying so beats drawing a flat pair of bars.
                    EmptyChart(stringResource(R.string.reports_comparison_empty))
                } else {
                    ComparisonDetails(comparison, segment, actions.onDrillDown)
                }
            }
        }
    }

    companion object {
        const val KEY = "comparison"

        /** Previous period first: the original and default comparison. */
        private val SEGMENTS = listOf(ComparisonSegment.PREVIOUS_PERIOD, ComparisonSegment.SAME_PERIOD_LAST_YEAR)
    }
}

@Composable
private fun ComparisonDetails(
    comparison: PeriodComparison,
    segment: ComparisonSegment,
    onDrillDown: (TransactionFilter) -> Unit,
) {
    Box(Modifier.padding(vertical = CentsDimens.spacingM)) {
        key(segment) {
            ComparisonColumns(comparison)
        }
    }
    LegendRow(
        color = CentsTheme.colors.tertiaryLabel,
        label = stringResource(segment.legendRes),
        amount = comparison.before.net,
    )
    LegendRow(
        color = CentsTheme.colors.accentText,
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
    // The comparison period is the only stretch on this tab that no other view can
    // reach: everything else reports on the period that is selected. So it gets
    // the drill-down, and this period keeps the one it already has everywhere.
    CentsListRow(
        title = stringResource(segment.openRes),
        subtitle = comparison.previous.label(),
        showChevron = true,
        showSeparator = false,
        onClick = { onDrillDown(comparison.filterForPrevious()) },
    )
}

internal const val SEGMENT_CONTROL_TAG = "reportsComparisonSegment"

private val ComparisonSegment.labelRes: Int
    get() =
        when (this) {
            ComparisonSegment.PREVIOUS_PERIOD -> R.string.reports_comparison_segment_previous
            ComparisonSegment.SAME_PERIOD_LAST_YEAR -> R.string.reports_comparison_segment_year_ago
        }

private val ComparisonSegment.headerRes: Int
    get() =
        when (this) {
            ComparisonSegment.PREVIOUS_PERIOD -> R.string.reports_comparison_header
            ComparisonSegment.SAME_PERIOD_LAST_YEAR -> R.string.reports_comparison_header_year_ago
        }

private val ComparisonSegment.legendRes: Int
    get() =
        when (this) {
            ComparisonSegment.PREVIOUS_PERIOD -> R.string.reports_comparison_legend_before
            ComparisonSegment.SAME_PERIOD_LAST_YEAR -> R.string.reports_comparison_legend_year_ago
        }

private val ComparisonSegment.openRes: Int
    get() =
        when (this) {
            ComparisonSegment.PREVIOUS_PERIOD -> R.string.reports_comparison_open_before
            ComparisonSegment.SAME_PERIOD_LAST_YEAR -> R.string.reports_comparison_open_year_ago
        }

private val ComparisonSegment.partialRes: Int
    get() =
        when (this) {
            ComparisonSegment.PREVIOUS_PERIOD -> R.string.reports_comparison_partial
            ComparisonSegment.SAME_PERIOD_LAST_YEAR -> R.string.reports_comparison_partial_year_ago
        }

@Singleton
class PeriodComparisonSectionSource
    @Inject
    constructor(
        private val repository: PeriodComparisonRepository,
    ) : ReportSectionSource {
        override fun observe(scope: ReportScope): Flow<ReportSectionState> =
            combine(
                repository.observePeriodComparison(scope, ComparisonSegment.PREVIOUS_PERIOD),
                repository.observePeriodComparison(scope, ComparisonSegment.SAME_PERIOD_LAST_YEAR),
                ::PeriodComparisonSection,
            )
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
            change.isNegative == moreIsBetter -> CentsTheme.colors.destructiveText
            else -> CentsTheme.colors.positiveText
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
