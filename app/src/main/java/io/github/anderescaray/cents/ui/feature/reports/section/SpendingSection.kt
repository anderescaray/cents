package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
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
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.reports.SpendingByCategoryRepository
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.feature.reports.Swatch
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Where the money went — and, on the other segment, where it came from: one ring per
 * direction, with the categories it is made of listed under it.
 *
 * One of the two views the tab started with, moved into a section and otherwise unchanged.
 * Everything it needs is in this file: the state, the source that fills it, the chart and the
 * row it draws. That is the shape a new view copies.
 *
 * **Both directions are carried at once rather than one at a time.** The ring was only ever
 * specified for spending (D4), and the owner asked for income as well; the cheap way would
 * have been a kind held somewhere and one query re-run on every flip. Two aggregations over
 * the same period cost two `GROUP BY`s bounded by the number of categories, and buy a toggle
 * that answers instantly instead of emptying the ring while SQLite is asked again. It also
 * keeps this section stateless in the way the seam asks for: the source still takes a period
 * and nothing else, so the choice lives in the composable that shows it and nowhere else.
 */
data class SpendingSection(
    val expense: SpendingBreakdown,
    val income: SpendingBreakdown,
) : ReportSectionState {
    override val key: String get() = KEY

    /** The ring for one direction, so a test can ask for the side it is about. */
    fun breakdownFor(kind: TransactionKind): SpendingBreakdown =
        when (kind) {
            TransactionKind.EXPENSE -> expense
            TransactionKind.INCOME -> income
        }

    @Composable
    override fun Content(actions: ReportActions) {
        // Saved rather than remembered: the section is one item of a `LazyColumn`, so
        // scrolling it off screen disposes it, and a toggle that quietly went back to
        // spending on the way past would be a bug nobody could describe.
        var kind by rememberSaveable { mutableStateOf(TransactionKind.EXPENSE) }
        val breakdown = breakdownFor(kind)
        val labels = KINDS.associateWith { stringResource(it.labelRes) }

        Column {
            // Above the card rather than inside it, the way the period control sits above
            // the whole tab: a segmented control is a question about what follows, and a row
            // in a grouped list reads as one more piece of the answer.
            CentsSegmentedControl(
                options = KINDS,
                selected = kind,
                onSelect = { kind = it },
                label = { labels.getValue(it) },
                // Tagged because "Income" is also the word the flow chart's legend uses one
                // section down, and a test asking for it by text would be asking which of
                // the two the tree happened to compose first.
                modifier = Modifier.padding(horizontal = CentsDimens.screenMargin).testTag(KIND_CONTROL_TAG),
            )
            Spacer(Modifier.height(CentsDimens.spacingM))

            CentsListSection(header = stringResource(kind.headerRes)) {
                if (breakdown.isEmpty) {
                    EmptyChart(stringResource(kind.emptyRes))
                } else {
                    Box(Modifier.padding(vertical = CentsDimens.spacingM)) {
                        // Keyed on the direction so each ring gets its own model producer.
                        // Shared, Vico animates from the old geometry to the new one while
                        // the colours have already changed — five expense arcs painted in
                        // the one income category's colour for the length of the
                        // transition. The two are different answers, not one changing.
                        key(kind) {
                            SpendingDonut(breakdown) { actions.onDrillDown(breakdown.filterFor(it)) }
                        }
                    }
                    CentsListRow(
                        title = stringResource(kind.totalRes),
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
    }

    companion object {
        const val KEY = "spending"

        /** Expenses first: it is the question the tab was built to answer (D4). */
        private val KINDS = listOf(TransactionKind.EXPENSE, TransactionKind.INCOME)
    }
}

/**
 * The words a direction is described in.
 *
 * Four strings rather than one flipped noun, because the ring is not "spending, negated":
 * "where the money went" and "where it came from" are two questions, and a total labelled
 * *Total spent* over income would be wrong rather than merely terse.
 */
private val TransactionKind.labelRes: Int
    get() = if (this == TransactionKind.INCOME) R.string.kind_income else R.string.kind_expense

private val TransactionKind.headerRes: Int
    get() = if (this == TransactionKind.INCOME) R.string.reports_income_header else R.string.reports_spending_header

private val TransactionKind.totalRes: Int
    get() = if (this == TransactionKind.INCOME) R.string.reports_income_total else R.string.reports_spending_total

private val TransactionKind.emptyRes: Int
    get() = if (this == TransactionKind.INCOME) R.string.reports_income_empty else R.string.reports_spending_empty

@Singleton
class SpendingSectionSource
    @Inject
    constructor(
        private val repository: SpendingByCategoryRepository,
    ) : ReportSectionSource {
        override fun observe(scope: ReportScope): Flow<ReportSectionState> =
            combine(
                repository.observeSpendingByCategory(scope, TransactionKind.EXPENSE),
                repository.observeSpendingByCategory(scope, TransactionKind.INCOME),
                ::SpendingSection,
            )
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
) = SpendingDonut(slices = breakdown.slices, modifier = modifier, onSliceClick = onSliceClick)

/**
 * The same ring from its slices alone, for a screen whose slices are not a report period's — a
 * group's spending (PRD §4.9) is a span of its own rather than a month.
 */
@Composable
internal fun SpendingDonut(
    slices: List<CategorySpending>,
    modifier: Modifier = Modifier,
    onSliceClick: (CategorySpending) -> Unit = {},
) {
    val fallback = CentsTheme.colors.tertiaryLabel
    val producer = remember { PieChartModelProducer() }

    // Keyed on the slices, so the model is rebuilt when the totals change and not on every
    // recomposition. The adding up is already done; re-running the transaction while the
    // screen scrolls is how a chart starts costing frames.
    LaunchedEffect(slices) {
        if (slices.isEmpty()) return@LaunchedEffect
        producer.runTransaction {
            pieSeries { series(slices.map { it.total.minorUnits }) }
        }
    }

    if (slices.isEmpty()) return

    val fills =
        remember(slices, fallback) {
            slices.map { slice ->
                PieChart.Slice(fill = Fill(slice.colorArgb?.let { Color(it) } ?: fallback))
            }
        }

    PieChartHost(
        chart =
            rememberPieChart(
                sliceProvider = PieChart.SliceProvider.series(fills),
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
                .pointerInput(slices) {
                    // The hole is an absolute size to Vico, so it is measured here in the
                    // same pixels as the tap rather than assumed to be a fixed fraction of
                    // a ring whose diameter depends on how wide the screen is.
                    val holeDiameter = DONUT_HOLE.toPx()
                    detectTapGestures { tap ->
                        slices
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
): CategorySpending? = slices.sliceAt(tap, width, height, holeDiameter)

/** [SpendingBreakdown.sliceAt] for a ring drawn from its slices alone. */
internal fun List<CategorySpending>.sliceAt(
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
    val edges = runningFold(0f) { swept, slice -> swept + slice.share * FULL_TURN }.drop(1)
    val index = edges.indexOfFirst { clockwiseFromTop <= it }

    // Rounding in the shares can leave the last hair of the circle unclaimed, and it belongs
    // to the slice that was drawn there.
    return getOrNull(if (index >= 0) index else lastIndex)
}

internal const val KIND_CONTROL_TAG = "reportsSpendingKind"

private val DONUT_HEIGHT = 220.dp
private val DONUT_HOLE = 104.dp
private val SLICE_SPACING = 2.dp

private const val QUARTER_TURN = 90f
private const val FULL_TURN = 360f
private const val PERCENT = 100
