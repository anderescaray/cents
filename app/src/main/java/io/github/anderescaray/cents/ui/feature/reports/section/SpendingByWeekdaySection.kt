package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.SpendingByWeekday
import io.github.anderescaray.cents.domain.model.WeekdaySpending
import io.github.anderescaray.cents.domain.repository.reports.SpendingByWeekdayRepository
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.weekdays
import io.github.anderescaray.cents.ui.feature.reports.AmountRow
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.formatLocale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import java.time.format.TextStyle as JavaTextStyle

/**
 * Do weekends wreck me: what was spent on each day of the week, as seven bars.
 *
 * The fourth PRD §4.3 view and the last one built. Like the ring it drills down — a day row
 * opens the movements recorded on that weekday inside the period — which is why
 * `TransactionFilter` gained a weekday and the Filters form a section for it (D10, answer B):
 * a bar that narrowed the list by something the owner could not see or clear would be worse
 * than one that did nothing.
 *
 * The bars show the shape; the footer says the answer. "Do weekends wreck me" is a question
 * about the *typical* day, so it is Saturday and Sunday's spending per day against the other
 * five's per day, not the raw two-against-five totals a weekend would always lose.
 */
data class SpendingByWeekdaySection(
    val spending: SpendingByWeekday,
) : ReportSectionState {
    override val key: String get() = KEY

    @Composable
    override fun Content(actions: ReportActions) {
        val locale = formatLocale()
        // Computed once per state rather than in the body: CLAUDE.md §5 asks for a derived
        // value to be precomputed rather than rebuilt on every recomposition, and this list
        // was already remembered inside WeekdayColumns — one source, passed to both places
        // that need the days in this order.
        val ordered = remember(spending, locale) { weekdays(locale).map(spending::on) }

        var daysOpen by rememberSaveable { mutableStateOf(false) }
        val costliest = remember(ordered) { ordered.maxByOrNull { it.total.minorUnits }?.takeIf { !it.total.isZero } }

        CentsListSection(
            header = stringResource(R.string.reports_weekday_header),
            footer = stringResource(spending.verdictRes()).takeIf { !spending.isEmpty },
        ) {
            if (spending.isEmpty) {
                EmptyChart(stringResource(R.string.reports_weekday_empty))
            } else {
                // The answer first: the costliest day, then the shape of the week.
                costliest?.let { day ->
                    Text(
                        text =
                            stringResource(
                                R.string.reports_weekday_most,
                                day.weekday.getDisplayName(JavaTextStyle.FULL, locale),
                            ),
                        style = CentsTheme.typography.headline,
                        color = CentsTheme.colors.label,
                        modifier =
                            Modifier.padding(
                                start = CentsDimens.rowHorizontalPadding,
                                end = CentsDimens.rowHorizontalPadding,
                                top = CentsDimens.spacingM,
                            ),
                    )
                }
                Box(Modifier.padding(vertical = CentsDimens.spacingM)) {
                    WeekdayColumns(ordered, locale, highlight = costliest?.weekday)
                }
                // The same label the ring uses for its total: this is the same number, laid
                // out by day instead of by category.
                AmountRow(
                    label = stringResource(R.string.reports_spending_total),
                    amount = spending.total,
                )
                // Seven rows folded into one until asked for: the chart already says which day
                // costs most, and the rows are the way into each day's movements.
                DaysToggle(open = daysOpen, onToggle = { daysOpen = !daysOpen })
                AnimatedVisibility(
                    visible = daysOpen,
                    enter = expandVertically() + fadeIn(),
                    exit =
                        shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        ordered.forEachIndexed { index, day ->
                            DayRow(
                                day = day,
                                locale = locale,
                                last = index == ordered.lastIndex,
                                onClick = { actions.onDrillDown(spending.filterFor(day.weekday)) },
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val KEY = "weekday"
    }
}

/**
 * Saturday and Sunday's spending per day against the weekday's per day.
 *
 * Cross-multiplied rather than divided, so a rounding step does not decide a close call:
 * `weekend / 2 > weekday / 5` is `weekend * 5 > weekday * 2`.
 */
private fun SpendingByWeekday.verdictRes(): Int {
    val weekendPerWeek = weekendTotal.minorUnits * WEEKDAY_COUNT
    val weekdayPerWeek = weekdayTotal.minorUnits * WEEKEND_COUNT
    return when {
        weekendPerWeek > weekdayPerWeek -> R.string.reports_weekday_verdict_weekend
        weekendPerWeek < weekdayPerWeek -> R.string.reports_weekday_verdict_weekday
        else -> R.string.reports_weekday_verdict_even
    }
}

@Singleton
class SpendingByWeekdaySectionSource
    @Inject
    constructor(
        private val repository: SpendingByWeekdayRepository,
    ) : ReportSectionSource {
        override fun observe(scope: ReportScope): Flow<ReportSectionState> =
            repository.observeSpendingByWeekday(scope).map(::SpendingByWeekdaySection)
    }

/** One weekday, its total, and the way into the movements behind it. */
@Composable
private fun DayRow(
    day: WeekdaySpending,
    locale: Locale,
    last: Boolean,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = day.weekday.getDisplayName(JavaTextStyle.FULL, locale),
        value = MoneyFormatter.format(day.total),
        showChevron = true,
        showSeparator = !last,
        onClick = onClick,
    )
}

/** *Day by day*, with a chevron that turns down when the days are open. */
@Composable
private fun DaysToggle(
    open: Boolean,
    onToggle: () -> Unit,
) {
    val turn by animateFloatAsState(if (open) OPEN_TURN else 0f, label = "daysChevron")
    CentsListRow(
        title = stringResource(R.string.reports_weekday_days),
        trailing = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = CentsTheme.colors.tertiaryLabel,
                modifier = Modifier.size(CHEVRON.dp).rotate(turn),
            )
        },
        showSeparator = open,
        onClick = onToggle,
    )
}

/**
 * Seven columns, one per day, in the order [ordered] already puts them in.
 *
 * One colour and one series: this is spending, so the columns take the same red the flow
 * chart's expenses do, and a colour means the same thing on both. The weekend against the
 * week is said in the footer rather than drawn, because a per-entry colour is more Vico than
 * the point is worth.
 */
@Composable
internal fun WeekdayColumns(
    ordered: List<WeekdaySpending>,
    locale: Locale,
    modifier: Modifier = Modifier,
    /** The day drawn in full; the others are faded, so the costliest stands out. */
    highlight: DayOfWeek? = null,
) {
    val destructive = CentsTheme.colors.destructive
    val labelColor = CentsTheme.colors.secondaryLabel
    val producer = remember { CartesianChartModelProducer() }

    LaunchedEffect(ordered) {
        producer.runTransaction {
            columnModel { series(ordered.map { it.total.minorUnits }) }
        }
    }

    val labels =
        remember(ordered, locale) {
            ordered.map { it.weekday.getDisplayName(JavaTextStyle.SHORT, locale) }
        }
    val labelStyle = TextStyle(color = labelColor, fontSize = AXIS_LABEL_SIZE)

    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider =
                        remember(highlight, ordered, destructive) {
                            HighlightedDay(ordered.indexOfFirst { it.weekday == highlight }, destructive)
                        },
                ),
                startAxis =
                    VerticalAxis.rememberStart(
                        label = rememberAxisLabelComponent(style = labelStyle),
                        valueFormatter = CartesianValueFormatter { _, value, _ -> axisAmount(value, locale) },
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
        scrollState = rememberFixedScroll(),
        zoomState = rememberFitZoom(),
        modifier = modifier.fillMaxWidth().height(COLUMN_CHART_HEIGHT),
    )
}

/** Every day in red, the [highlighted] one in full and the rest faded. */
private class HighlightedDay(
    private val highlighted: Int,
    color: Color,
) : ColumnCartesianLayer.ColumnProvider {
    private val full = LineComponent(Fill(color), COLUMN_WIDTH)
    private val faded = LineComponent(Fill(color.copy(alpha = FADED_ALPHA)), COLUMN_WIDTH)

    override fun getColumn(
        entry: ColumnCartesianLayerModel.Entry,
        extraStore: ExtraStore,
    ): LineComponent = if (highlighted < 0 || entry.x.roundToInt() == highlighted) full else faded

    override fun getWidestSeriesColumn(
        seriesKey: Any,
        seriesIndex: Int,
        extraStore: ExtraStore,
    ): LineComponent = full
}

private val COLUMN_CHART_HEIGHT = 180.dp
private val COLUMN_WIDTH = 12.dp
private val AXIS_LABEL_SIZE = 11.sp

private const val WEEKDAY_COUNT = 5L
private const val WEEKEND_COUNT = 2L
private const val FADED_ALPHA = 0.35f
private const val CHEVRON = 16
private const val OPEN_TURN = 90f
