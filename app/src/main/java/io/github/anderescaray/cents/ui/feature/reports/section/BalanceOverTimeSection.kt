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
import androidx.compose.runtime.remember
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
import io.github.anderescaray.cents.domain.model.BalanceOverTime
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.repository.reports.BalanceOverTimeRepository
import io.github.anderescaray.cents.ui.components.AnimatedMoney
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.feature.reports.AmountRow
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.formatLocale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Am I trending up or down: the balance, day by day, over the period.
 *
 * The third view on the tab, and the first one built after the seam existed — so it is the
 * worked example of what a view is. Its model is `domain/model/BalanceOverTime.kt`, its query
 * is `TransactionDao.observeDailyNet` plus `observeBalanceBefore`, its repository is
 * `RoomBalanceOverTimeRepository`, and everything on screen is in this file.
 *
 * **The line starts from the money carried into the period, not from zero.** The whole
 * argument is on [BalanceOverTime]; the part that matters here is what it costs, which is
 * that the y-axis must not be dragged down to zero — see [BalanceRangeProvider].
 *
 * The three rows under the chart are not decoration. A line drawn against an axis that does
 * not start at zero cannot be read for its level, only for its shape, so the level is stated
 * in words: what there was, what there is, and the difference.
 */
data class BalanceOverTimeSection(
    val balance: BalanceOverTime,
) : ReportSectionState {
    override val key: String get() = KEY

    @Composable
    override fun Content(actions: ReportActions) {
        CentsListSection(
            header = stringResource(R.string.reports_balance_header),
            // Said, because over every account the brokers are left out, and a line that quietly
            // omits money is the kind of number that gets believed and should not be.
            footer = stringResource(R.string.reports_balance_scope).takeIf { !balance.oneAccount },
        ) {
            if (balance.isEmpty) {
                // A flat line at the opening balance would be true and would say nothing.
                EmptyChart(stringResource(R.string.reports_balance_empty))
            } else {
                // The answer first: how much the money moved over the period, in its colour.
                val tone =
                    when {
                        balance.change.isZero -> CentsTheme.colors.label
                        balance.change.isNegative -> CentsTheme.colors.destructiveText
                        else -> CentsTheme.colors.positiveText
                    }
                Column(
                    Modifier.padding(
                        horizontal = CentsDimens.rowHorizontalPadding,
                        vertical = CentsDimens.spacingM,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.reports_balance_change),
                        style = CentsTheme.typography.footnote,
                        color = CentsTheme.colors.secondaryLabel,
                    )
                    AnimatedMoney(
                        money = balance.change,
                        style = CentsTheme.typography.title1,
                        color = tone,
                        withSign = true,
                    )
                }
                BalanceLineChart(balance)
                Spacer(Modifier.height(CentsDimens.spacingM))
                // A line drawn against an axis that does not start at zero cannot be read for its
                // level, only for its shape, so the level is stated in words beside it.
                StatRow {
                    StatCell(label = stringResource(R.string.reports_balance_opening), amount = balance.opening)
                    StatCell(label = stringResource(R.string.reports_balance_closing), amount = balance.closing)
                }
                Spacer(Modifier.height(CentsDimens.spacingL))
            }
        }
    }

    companion object {
        const val KEY = "balance"
    }
}

@Singleton
class BalanceOverTimeSectionSource
    @Inject
    constructor(
        private val repository: BalanceOverTimeRepository,
    ) : ReportSectionSource {
        override fun observe(scope: ReportScope): Flow<ReportSectionState> =
            repository.observeBalanceOverTime(scope).map(::BalanceOverTimeSection)
    }

/**
 * The balance line.
 *
 * Green above the balance the period started from and red below it, split at exactly that
 * value — the same two tokens the transaction list uses for amounts and the columns above use
 * for income and expenses, so a colour means one thing across the whole app. Nothing here
 * comes from Vico's Material defaults (ADR 0001).
 */
@Composable
internal fun BalanceLineChart(
    balance: BalanceOverTime,
    modifier: Modifier = Modifier,
) {
    val positive = CentsTheme.colors.positive
    val destructive = CentsTheme.colors.destructive
    val labelColor = CentsTheme.colors.secondaryLabel
    val locale = formatLocale()
    val producer = remember { CartesianChartModelProducer() }

    // Keyed on the data, so the model is rebuilt when the balances change rather than on
    // every recomposition while the tab is scrolled.
    LaunchedEffect(balance) {
        if (balance.points.isEmpty()) return@LaunchedEffect
        producer.runTransaction {
            lineModel { series(balance.points.map { it.balance.minorUnits }) }
        }
    }

    if (balance.points.isEmpty()) return

    val labels =
        remember(balance) {
            val formatter = DateTimeFormatter.ofPattern(DAY_LABEL_PATTERN, formatLocale())
            balance.points.map { it.date.format(formatter) }
        }

    CartesianChartHost(
        chart = rememberBalanceChart(balance.opening.minorUnits, labels, positive, destructive, labelColor, locale),
        modelProducer = producer,
        // The whole period in the card's width: a line scrolled through a day at a time showed
        // a week, and the shape of the period is the whole point of it.
        scrollState = rememberFixedScroll(),
        zoomState = rememberFitZoom(),
        modifier = modifier.fillMaxWidth().height(LINE_CHART_HEIGHT),
    )
}

/**
 * The chart itself: one line, split in two colours at the balance the period started from,
 * over an axis that fits the balances rather than the whole range down to zero.
 *
 * Split out from [BalanceLineChart] because it is the half that is all Vico configuration,
 * and keeping the data handling above it readable is worth one indirection.
 */
@Composable
private fun rememberBalanceChart(
    openingMinor: Long,
    labels: List<String>,
    positive: Color,
    destructive: Color,
    labelColor: Color,
    locale: java.util.Locale,
) = rememberCartesianChart(
    rememberLineCartesianLayer(
        lineProvider =
            LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill =
                        LineCartesianLayer.LineFill.double(
                            Fill(positive),
                            Fill(destructive),
                            { openingMinor },
                        ),
                    areaFill =
                        LineCartesianLayer.AreaFill.double(
                            Fill(positive.copy(alpha = AREA_ALPHA)),
                            Fill(destructive.copy(alpha = AREA_ALPHA)),
                            { openingMinor },
                        ),
                ),
            ),
        rangeProvider = BalanceRangeProvider,
    ),
    startAxis =
        VerticalAxis.rememberStart(
            label = rememberAxisLabelComponent(style = TextStyle(color = labelColor, fontSize = AXIS_LABEL_SIZE)),
            valueFormatter = CartesianValueFormatter { _, value, _ -> axisAmount(value, locale) },
        ),
    bottomAxis =
        HorizontalAxis.rememberBottom(
            label = rememberAxisLabelComponent(style = TextStyle(color = labelColor, fontSize = AXIS_LABEL_SIZE)),
            valueFormatter =
                CartesianValueFormatter { _, value, _ ->
                    labels.getOrElse(value.toInt()) { "" }
                },
            // A year is 365 points and cannot carry 365 dates under it. Roughly five labels,
            // whatever the period is, and never a spacing of zero.
            itemPlacer = HorizontalAxis.ItemPlacer.aligned({ max(1, labels.size / TARGET_AXIS_LABELS) }),
        ),
)

/**
 * The y-axis fits the balances, instead of being dragged down to zero.
 *
 * Vico's own `auto()` returns zero as the minimum whenever every value is positive, which is
 * right for columns — a bar has to start somewhere — and wrong for a balance: 10.000 euros
 * moving by 200 over a month draws a dead flat line at the top of a chart that is mostly
 * empty space. This is the direct cost of starting the line from the money carried into the
 * period rather than from zero, and paying it is the point: the slope is the answer, the
 * level is context, and the rows under the chart state the level in words.
 *
 * The range is padded by a tenth of its own span so the line does not run along the frame,
 * and a period whose balance never moved is given a span to be flat inside rather than a
 * zero-height axis to divide by.
 */
private object BalanceRangeProvider : CartesianLayerRangeProvider {
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
        // A line that never moves still needs somewhere to be drawn. Ten percent of its own
        // value, or one whole unit when that value is zero too.
        return max(abs(maxY) * RANGE_PADDING, MINOR_UNITS_PER_UNIT)
    }
}

private val LINE_CHART_HEIGHT = 180.dp
private val AXIS_LABEL_SIZE = 11.sp

/** Day and short month: "3 Sep", "3 sept" — whatever the device locale calls it. */
private const val DAY_LABEL_PATTERN = "d MMM"

/** Cents stores money in minor units; an axis is labelled in whole ones. */
private const val MINOR_UNITS_PER_UNIT = 100.0

private const val TARGET_AXIS_LABELS = 5
private const val RANGE_PADDING = 0.1
private const val AREA_ALPHA = 0.16f
