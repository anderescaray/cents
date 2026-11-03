package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import io.github.anderescaray.cents.ui.components.rememberDrawOn
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.LegendRow
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.formatLocale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Am I doing better or worse than last time: this period's income and expenses beside the
 * comparison period's (previous period or same period last year), as opposing columns.
 *
 * The fourth view on the tab, and the first one built entirely on the seam #28 and #35 left
 * behind — one model, one repository interface, one Room implementation, this file, its own
 * strings, and single lines in `RepositoryModule`, `ReportSections` and the test factory.
 *
 * **The answer comes first.** One sentence says whether more or less was spent, and by how much;
 * then each measure — income, expenses, what was left — as its figure, its change and two bars on
 * one scale, then and now. A bar slightly shorter than its neighbour does not tell anyone whether
 * they saved forty euros or four hundred, so the figure and the change are always written.
 *
 * The tinting is not "up is green". Spending *more* is worse and earning more is better, so
 * each measure carries which direction is good — see [MeasureBars]. Getting that uniform would make
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
    SpendingVerdict(comparison = comparison, segment = segment)
    ChartLegend(
        listOf(
            CentsTheme.colors.tertiaryLabel to stringResource(segment.legendRes),
            CentsTheme.colors.accent to stringResource(R.string.reports_comparison_legend_current),
        ),
    )
    Spacer(Modifier.height(CentsDimens.spacingS))
    MeasureBars(
        label = stringResource(R.string.summary_income),
        before = comparison.before.income,
        now = comparison.current.income,
        change = comparison.incomeChange,
        moreIsBetter = true,
    )
    MeasureBars(
        label = stringResource(R.string.summary_expenses),
        before = comparison.before.expenses,
        now = comparison.current.expenses,
        change = comparison.expensesChange,
        moreIsBetter = false,
    )
    MeasureBars(
        label = stringResource(R.string.summary_net),
        before = comparison.before.net,
        now = comparison.current.net,
        change = comparison.netChange,
        moreIsBetter = true,
    )
    Spacer(Modifier.height(CentsDimens.spacingS))
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

/**
 * The answer in one sentence, before any bar: *Spent 120,00 € less than the previous period*, green
 * when less went out and red when more did. Spending is what this card is most often opened for.
 */
@Composable
private fun SpendingVerdict(
    comparison: PeriodComparison,
    segment: ComparisonSegment,
) {
    val change = comparison.expensesChange
    val amount = MoneyFormatter.format(if (change.isNegative) -change else change)
    val (text, tone) =
        when {
            change.isZero -> {
                stringResource(R.string.reports_comparison_spent_same) to CentsTheme.colors.label
            }

            change.isNegative -> {
                stringResource(R.string.reports_comparison_spent_less, amount) to
                    CentsTheme.colors.positiveText
            }

            else -> {
                stringResource(R.string.reports_comparison_spent_more, amount) to CentsTheme.colors.destructiveText
            }
        }
    Column(Modifier.padding(horizontal = CentsDimens.rowHorizontalPadding, vertical = CentsDimens.spacingM)) {
        Text(text = text, style = CentsTheme.typography.title3, color = tone)
        Text(
            text = stringResource(segment.thanRes),
            style = CentsTheme.typography.footnote,
            color = CentsTheme.colors.secondaryLabel,
        )
    }
}

/**
 * One measure, then and now: its name, what it is now and what it did, as a percentage where there
 * was something before to be a percentage of; and two bars on the same scale, the comparison
 * period in grey over this one in the accent.
 *
 * [moreIsBetter] is the whole point of the change's colour. Income going up and expenses going up
 * are both "a bigger number", and colouring them the same way would tell someone their worst month
 * was their best one.
 */
@Composable
private fun MeasureBars(
    label: String,
    before: Money,
    now: Money,
    change: Money,
    moreIsBetter: Boolean,
) {
    val tint =
        when {
            change.isZero -> CentsTheme.colors.secondaryLabel
            change.isNegative == moreIsBetter -> CentsTheme.colors.destructiveText
            else -> CentsTheme.colors.positiveText
        }
    val scale = maxOf(abs(before.minorUnits), abs(now.minorUnits)).coerceAtLeast(1L)
    Column(Modifier.padding(horizontal = CentsDimens.rowHorizontalPadding, vertical = CentsDimens.spacingS)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = CentsTheme.typography.subhead,
                color = CentsTheme.colors.secondaryLabel,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = changeText(change, before),
                style = CentsTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = tint,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(tint.copy(alpha = CHANGE_TINT))
                        .padding(horizontal = CentsDimens.spacingS, vertical = 2.dp),
            )
        }
        Text(
            text = MoneyFormatter.format(now),
            style = CentsTheme.typography.headline,
            color = if (now.isNegative) CentsTheme.colors.destructiveText else CentsTheme.colors.label,
        )
        Spacer(Modifier.height(CentsDimens.spacingXs + 2.dp))
        Bar(
            fraction = abs(before.minorUnits).toFloat() / scale,
            color = CentsTheme.colors.tertiaryLabel,
            amount = before,
        )
        Spacer(Modifier.height(BAR_GAP.dp))
        Bar(fraction = abs(now.minorUnits).toFloat() / scale, color = CentsTheme.colors.accent, amount = now)
    }
}

/** How a measure changed: "+12 %" where there was something before, the amount where there was not. */
@Composable
private fun changeText(
    change: Money,
    before: Money,
): String {
    val percent =
        if (before.isZero) null else (change.minorUnits.toDouble() / abs(before.minorUnits) * PERCENT).roundToInt()
    return when {
        change.isZero -> {
            stringResource(R.string.reports_comparison_unchanged)
        }

        percent == null -> {
            MoneyFormatter.formatSigned(change)
        }

        else -> {
            stringResource(
                R.string.reports_share_signed,
                if (percent >
                    0
                ) {
                    "+$percent"
                } else {
                    percent.toString().replace("-", "−")
                },
            )
        }
    }
}

/** One period's bar, with its amount at the end of it; it grows into place the first time it is seen. */
@Composable
private fun Bar(
    fraction: Float,
    color: Color,
    amount: Money,
) {
    val drawn by rememberDrawOn()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(BAR_HEIGHT.dp)
                    .clip(CircleShape)
                    .background(CentsTheme.colors.fill)
                    .drawBehind {
                        val width = size.width * fraction.coerceIn(0f, 1f) * drawn
                        if (width > 0f) {
                            drawRoundRect(
                                color = color,
                                size = size.copy(width = width),
                                cornerRadius =
                                    CornerRadius(size.height / 2),
                            )
                        }
                    },
        )
        Spacer(Modifier.width(CentsDimens.spacingS))
        Text(
            text = MoneyFormatter.format(amount),
            style = CentsTheme.typography.caption1,
            color = CentsTheme.colors.secondaryLabel,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.widthIn(min = BAR_AMOUNT_MIN.dp),
        )
    }
}

private const val CHANGE_TINT = 0.12f
private const val BAR_HEIGHT = 8
private const val BAR_GAP = 4
private const val BAR_AMOUNT_MIN = 72
private const val PERCENT = 100

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

/** What the verdict compares against: "than the previous period". */
private val ComparisonSegment.thanRes: Int
    get() =
        when (this) {
            ComparisonSegment.PREVIOUS_PERIOD -> R.string.reports_comparison_than_previous
            ComparisonSegment.SAME_PERIOD_LAST_YEAR -> R.string.reports_comparison_than_year_ago
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
 * The previous period, written out.
 *
 * Both ends are shown rather than a name like "last month", because the period this compares
 * against depends on what is selected above, and "last month" beside a custom range would be
 * wrong in a way nothing on screen corrects.
 */
@Composable
private fun ReportPeriod.label(): String {
    val formatter = remember { DateTimeFormatter.ofPattern(RANGE_LABEL_PATTERN, formatLocale()) }
    return "${from.format(formatter)} – ${to.format(formatter)}"
}

/** "4 Aug", in whatever the device locale calls August. */
private const val RANGE_LABEL_PATTERN = "d MMM"
