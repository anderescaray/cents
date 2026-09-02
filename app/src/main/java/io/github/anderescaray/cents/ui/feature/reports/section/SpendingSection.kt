package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.domain.repository.reports.SpendingByCategoryRepository
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.feature.reports.Swatch
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Where the money went, as a ring with the categories it is made of listed under it.
 *
 * One of the two views the tab started with, moved into a section and otherwise unchanged.
 * Everything it needs is in this file: the state, the source that fills it, the chart and the
 * row it draws. That is the shape a new view copies.
 */
data class SpendingSection(
    val breakdown: SpendingBreakdown,
) : ReportSectionState {
    override val key: String get() = KEY

    @Composable
    override fun Content(actions: ReportActions) {
        CentsListSection(header = stringResource(R.string.reports_spending_header)) {
            if (breakdown.isEmpty) {
                EmptyChart(stringResource(R.string.reports_spending_empty))
            } else {
                Box(Modifier.padding(vertical = CentsDimens.spacingM)) {
                    SpendingDonut(breakdown) { actions.onDrillDown(breakdown.filterFor(it)) }
                }
                CentsListRow(
                    title = stringResource(R.string.reports_spending_total),
                    value = MoneyFormatter.format(breakdown.total),
                )
                breakdown.slices.forEachIndexed { index, slice ->
                    CategoryRow(
                        slice = slice,
                        last = index == breakdown.slices.lastIndex,
                        onClick = { actions.onDrillDown(breakdown.filterFor(slice)) },
                    )
                }
            }
        }
    }

    companion object {
        const val KEY = "spending"
    }
}

@Singleton
class SpendingSectionSource
    @Inject
    constructor(
        private val repository: SpendingByCategoryRepository,
    ) : ReportSectionSource {
        override fun observe(
            period: ReportPeriod,
            currency: String,
        ): Flow<ReportSectionState> = repository.observeSpendingByCategory(period, currency).map(::SpendingSection)
    }

/**
 * The legend *is* the list. Each row already has to carry the name, the amount and the
 * share, so putting the swatch on that row means one place to read rather than a key beside
 * the chart to match colours up against.
 */
@Composable
private fun CategoryRow(
    slice: CategorySpending,
    last: Boolean,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = slice.name ?: stringResource(R.string.reports_uncategorized),
        subtitle = stringResource(R.string.reports_share, (slice.share * PERCENT).toInt()),
        value = MoneyFormatter.format(slice.total),
        leading = { Swatch(slice.colorArgb?.let { Color(it) } ?: CentsTheme.colors.tertiaryLabel) },
        showChevron = true,
        showSeparator = !last,
        onClick = onClick,
    )
}

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
internal fun SpendingDonut(
    breakdown: SpendingBreakdown,
    modifier: Modifier = Modifier,
    onSliceClick: (CategorySpending) -> Unit = {},
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
        modifier =
            modifier
                .fillMaxWidth()
                .height(DONUT_HEIGHT)
                .pointerInput(breakdown) {
                    // The hole is an absolute size to Vico, so it is measured here in the
                    // same pixels as the tap rather than assumed to be a fixed fraction of
                    // a ring whose diameter depends on how wide the screen is.
                    val holeDiameter = DONUT_HOLE.toPx()
                    detectTapGestures { tap ->
                        breakdown
                            .sliceAt(tap, size.width.toFloat(), size.height.toFloat(), holeDiameter)
                            ?.let(onSliceClick)
                    }
                },
    )
}

/**
 * Which slice a tap landed on, or null for a tap that missed the ring.
 *
 * Vico draws the pie but hands back no hit testing, so this reconstructs the geometry it
 * used: [PieSize.Outer.Fill] means a circle centred in the box with the smaller side as its
 * diameter, and slices are laid out clockwise from twelve o'clock — the minus-ninety-degree
 * start angle. Nothing shrinks that circle here, because these slices carry no labels and no
 * offset from the centre, which are the two things Vico insets the radius for.
 *
 * [holeDiameter] is passed in pixels rather than assumed, because Vico takes the hole as an
 * absolute size: on a screen narrower than the ring is tall, the ring shrinks and the hole
 * does not, so a fixed fraction would slowly become wrong.
 *
 * A tap in the hole or outside the ring returns null **on purpose**. Guessing at a nearby
 * slice would open a list of the wrong category's movements, and a mistap that does nothing
 * is far better than one that quietly answers a different question. The rows beneath the
 * ring reach the same place and are the reliable way in.
 */
internal fun SpendingBreakdown.sliceAt(
    tap: Offset,
    width: Float,
    height: Float,
    holeDiameter: Float,
): CategorySpending? {
    val radius = minOf(width, height) / 2f
    val fromCentre = Offset(tap.x - width / 2f, tap.y - height / 2f)
    val distance = hypot(fromCentre.x, fromCentre.y)
    if (distance < holeDiameter / 2f || distance > radius) return null

    // atan2 measures from three o'clock counter-clockwise; the chart starts at twelve and
    // runs clockwise, so the angle is rotated a quarter turn and wrapped into 0..360.
    val degrees = Math.toDegrees(atan2(fromCentre.y.toDouble(), fromCentre.x.toDouble())).toFloat()
    val clockwiseFromTop = (degrees + QUARTER_TURN + FULL_TURN) % FULL_TURN

    // Where each slice ends, in degrees clockwise from twelve.
    val edges = slices.runningFold(0f) { swept, slice -> swept + slice.share * FULL_TURN }.drop(1)
    val index = edges.indexOfFirst { clockwiseFromTop <= it }

    // Rounding in the shares can leave the last hair of the circle unclaimed, and it belongs
    // to the slice that was drawn there.
    return slices.getOrNull(if (index >= 0) index else slices.lastIndex)
}

private val DONUT_HEIGHT = 220.dp
private val DONUT_HOLE = 104.dp
private val SLICE_SPACING = 2.dp

private const val QUARTER_TURN = 90f
private const val FULL_TURN = 360f
private const val PERCENT = 100
