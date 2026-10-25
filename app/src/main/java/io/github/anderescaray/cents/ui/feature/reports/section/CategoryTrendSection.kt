package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategoryTrend
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.TrendChoice
import io.github.anderescaray.cents.domain.repository.reports.CategoryTrendRepository
import io.github.anderescaray.cents.ui.components.CategoryBadge
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.feature.reports.AmountRow
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.formatLocale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Is one category creeping up: a line across the last twelve periods, for the category being
 * watched.
 *
 * The fifth PRD §4.3 view. It is also **the first one whose question needs more than the
 * selected period** — a trend cannot be read off one month — and how that is reconciled with
 * the tab's one-period rule is argued on [CategoryTrend]: the period picks the bucket, and the
 * line shows twelve of them.
 *
 * The last row is not decoration either. A line drawn over twelve buckets shows the shape but
 * not the size of the move, so the change against the average of the earlier buckets is said
 * in words — against the *average* rather than the previous bucket, because one quiet month
 * would otherwise make the next look like a spike.
 */
data class CategoryTrendSection(
    val trend: CategoryTrend,
    /**
     * Held on the state rather than reached through [ReportActions], because which category to
     * watch is this view's own business and nothing else on the tab has an opinion about it.
     *
     * It is a property of the singleton source rather than a lambda built per emission, so two
     * states carrying it still compare equal and Compose can skip what has not changed.
     */
    val onSelectCategory: (Long) -> Unit,
) : ReportSectionState {
    override val key: String get() = KEY

    @Composable
    override fun Content(actions: ReportActions) {
        var picking by rememberSaveable { mutableStateOf(false) }

        CentsListSection(
            header = stringResource(R.string.reports_trend_header),
            // Every bucket is cut to the elapsed days when the period is still running, so the
            // line is lower than those months really were. Said out loud for the same reason
            // the comparison section says it.
            footer = stringResource(R.string.reports_trend_partial).takeIf { trend.partial && !trend.isEmpty },
        ) {
            val selected = trend.selected
            if (selected == null) {
                EmptyChart(stringResource(R.string.reports_trend_empty))
            } else {
                Box(Modifier.padding(vertical = CentsDimens.spacingM)) {
                    TrendLine(trend, Color(selected.colorArgb))
                }

                // The picker is a row that opens the list rather than the list itself: there
                // can be as many categories as the owner has typed, and the other views on
                // this tab are what the screen is for.
                CentsListRow(
                    title = selected.name,
                    subtitle = stringResource(R.string.reports_trend_pick),
                    leading = { TrendBadge(selected) },
                    showChevron = true,
                    onClick = { picking = !picking },
                )

                if (picking) {
                    trend.choices.forEach { choice ->
                        ChoiceRow(
                            choice = choice,
                            selected = choice.id == selected.id,
                            onClick = {
                                onSelectCategory(choice.id)
                                picking = false
                            },
                        )
                    }
                }

                val change = trend.change
                trend.latest?.let { latest ->
                    AmountRow(
                        label = stringResource(R.string.reports_trend_latest),
                        amount = latest.total,
                        last = change == null,
                    )
                }
                // With one bucket there is no "usual" to compare against, so the row is not
                // drawn. It used to fall back to printing this period's total under a label
                // that says it is a change, which is a wrong number rather than a missing one.
                if (change != null) {
                    AmountRow(
                        label = stringResource(R.string.reports_trend_change),
                        amount = change,
                        // Spending more than usual is the thing this view exists to notice,
                        // so it is the one number here that is tinted — and up is bad.
                        valueColor =
                            when {
                                change.isZero -> null
                                change.isNegative -> CentsTheme.colors.positiveText
                                else -> CentsTheme.colors.destructiveText
                            },
                        last = true,
                    )
                }
            }
        }
    }

    companion object {
        const val KEY = "trend"
    }
}

/**
 * The source, and the one place on this tab that holds a choice of its own.
 *
 * `ReportSectionSource` says a source is stateless, and every other one is. This one keeps
 * which category is being watched, because the alternatives are worse: fetching every
 * category's twelve buckets on every emission is work proportional to categories times days,
 * and putting the choice in `ReportsViewModel` would make the tab know what a category detail
 * is, which is the coupling the seam exists to prevent.
 *
 * Being a `@Singleton`, the choice outlives leaving the tab and dies with the process. That is
 * the intent: "which category am I watching" is worth keeping while the app is open, and is
 * not worth a row in the database.
 */
@Singleton
class CategoryTrendSectionSource
    @Inject
    constructor(
        private val repository: CategoryTrendRepository,
    ) : ReportSectionSource {
        private val watching = MutableStateFlow<Long?>(null)

        /**
         * A stable instance rather than a method reference built at each emission, so the
         * states carrying it compare equal and Compose is not asked to redraw the section
         * every time any number on it changes.
         */
        private val select: (Long) -> Unit = { watching.value = it }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observe(scope: ReportScope): Flow<ReportSectionState> =
            watching.flatMapLatest { categoryId ->
                repository.observeCategoryTrend(scope, categoryId).map { trend ->
                    CategoryTrendSection(trend, select)
                }
            }
    }

@Composable
private fun ChoiceRow(
    choice: TrendChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = choice.name,
        selected = selected,
        leading = { TrendBadge(choice) },
        onClick = onClick,
    )
}

@Composable
private fun TrendBadge(choice: TrendChoice) {
    CategoryBadge(name = choice.name, colorArgb = choice.colorArgb, emoji = choice.emoji)
}

/**
 * Twelve buckets, in the category's own colour.
 *
 * The line is drawn in the colour the owner gave that category, so it agrees with the ring
 * further up the tab about what "Groceries" looks like. The axis is left to range over the
 * values it holds rather than being dragged to zero: what is being read here is the slope.
 */
@Composable
internal fun TrendLine(
    trend: CategoryTrend,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val labelColor = CentsTheme.colors.secondaryLabel
    val producer = remember { CartesianChartModelProducer() }

    LaunchedEffect(trend) {
        if (trend.buckets.isEmpty()) return@LaunchedEffect
        producer.runTransaction {
            lineModel { series(trend.buckets.map { it.total.minorUnits }) }
        }
    }

    if (trend.buckets.isEmpty()) return

    val labels =
        remember(trend) {
            val formatter =
                DateTimeFormatter.ofPattern(
                    trend.buckets
                        .first()
                        .whole
                        .labelPattern(),
                    formatLocale(),
                )
            trend.buckets.map { it.whole.from.format(formatter) }
        }
    val labelStyle = TextStyle(color = labelColor, fontSize = AXIS_LABEL_SIZE)

    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider =
                        LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(Fill(color))),
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
        modifier = modifier.fillMaxWidth().height(LINE_CHART_HEIGHT),
    )
}

/**
 * What to write under each point, which depends on what a bucket *is*.
 *
 * Twelve buckets of months are twelve month names; twelve buckets of years labelled that way
 * would all read "Jan", and twelve weeks would collapse to three repeated month names. The
 * period picks the bucket, so it has to pick the label too.
 *
 * Quarters and weeks carry the year or the day as well, because twelve of either spans more
 * than one year or more than one month and the names alone would repeat.
 */
private fun ReportPeriod.labelPattern(): String =
    when (this) {
        is ReportPeriod.Year -> "yyyy"
        is ReportPeriod.Month -> "LLL"
        is ReportPeriod.Quarter -> "LLL yy"
        is ReportPeriod.Week -> "d MMM"
        is ReportPeriod.Custom -> "d MMM"
    }

private val LINE_CHART_HEIGHT = 180.dp
private val AXIS_LABEL_SIZE = 11.sp

/** Cents stores money in minor units; an axis is labelled in whole ones. */
private const val MINOR_UNITS_PER_UNIT = 100.0
