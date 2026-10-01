package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
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
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.FlowBucket
import io.github.anderescaray.cents.domain.model.FlowBucketSize
import io.github.anderescaray.cents.domain.model.FlowShape
import io.github.anderescaray.cents.domain.model.IncomeAndExpenses
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.repository.reports.MonthlyFlowRepository
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.feature.reports.AmountRow
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.LegendRow
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** What the columns show: income beside expenses, or what was left of one after the other. */
enum class FlowMode {
    IN_AND_OUT,
    SAVED,
}

/**
 * Income against expenses over time, a column per month, quarter or year.
 *
 * Three choices sit on the chart itself. **What** it shows: income beside expenses, or one
 * column of what was saved, green when something was left and red when more went out than came
 * in. **How long** a column is: a month, a quarter or a year. **How many** columns: 6, 12 or 24.
 *
 * **Tapping a column says what it holds** underneath: its income, its expenses and what was
 * saved or overspent, with the way into its movements. The latest column with something in
 * it is the one shown until another is tapped, and TalkBack moves between columns with two
 * custom actions, since it cannot reach the chart's taps. The figures are under the chart
 * rather than in a list of every month, which at 24 columns would be longer than the rest of
 * the tab.
 */
data class MonthlyFlowSection(
    val flow: IncomeAndExpenses,
    /**
     * Held on the state for the same reason the category trend's choice is: the size and count
     * change what is read from the database, so they live in the source, and this is a stable
     * instance from it so two states still compare equal.
     */
    val onReshape: (FlowShape) -> Unit,
) : ReportSectionState {
    override val key: String get() = KEY

    @Composable
    override fun Content(actions: ReportActions) {
        var mode by rememberSaveable { mutableStateOf(FlowMode.IN_AND_OUT) }
        val buckets = flow.buckets
        // Back to the latest column with something in it whenever the columns themselves
        // change: another size or count, another period, another account. December is the
        // last column of a year read in September, and a way into an empty list is no default.
        var selected by rememberSaveable(flow.scope, flow.shape) {
            mutableIntStateOf(buckets.indexOfLast { !it.isEmpty }.takeIf { it >= 0 } ?: buckets.lastIndex)
        }
        val bucket = buckets.getOrNull(selected) ?: buckets.lastOrNull()
        val select: (Int) -> Unit = { if (it in buckets.indices) selected = it }

        Column {
            CentsListSection(header = stringResource(R.string.reports_flow_header)) {
                // Inside the card they change, so the controls and the chart read as one thing.
                Column(Modifier.padding(vertical = CentsDimens.spacingS)) {
                    FlowControls(mode = mode, onMode = { mode = it }, shape = flow.shape, onReshape = onReshape)
                }
                if (flow.isEmpty || bucket == null) {
                    EmptyChart(stringResource(R.string.reports_flow_empty))
                } else {
                    val previous = stringResource(R.string.reports_flow_previous_column)
                    val next = stringResource(R.string.reports_flow_next_column)
                    Box(
                        Modifier
                            .padding(vertical = CentsDimens.spacingM)
                            // The columns are tapped through the chart library, which TalkBack
                            // cannot reach, so the same move is offered as two actions.
                            .semantics {
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction(previous) {
                                            select(selected - 1)
                                            true
                                        },
                                        CustomAccessibilityAction(next) {
                                            select(selected + 1)
                                            true
                                        },
                                    )
                            },
                    ) {
                        FlowChart(flow = flow, mode = mode, selected = selected, onSelect = select)
                    }
                    BucketDetail(
                        bucket = bucket,
                        size = flow.shape.size,
                        onOpen = { actions.onDrillDown(flow.filterFor(bucket)) },
                    )
                }
            }
        }
    }

    companion object {
        const val KEY = "flow"
        const val MODE_TAG = "flow-mode"
        const val SIZE_TAG = "flow-size"
        const val COUNT_TAG = "flow-count"
        const val OPEN_TAG = "flow-open"
    }
}

@Singleton
class MonthlyFlowSectionSource
    @Inject
    constructor(
        private val repository: MonthlyFlowRepository,
    ) : ReportSectionSource {
        /**
         * In memory, like the category the trend follows: a way of looking, reset by a restart,
         * and not worth a row in the database.
         */
        private val shape = MutableStateFlow(FlowShape())

        private val reshape: (FlowShape) -> Unit = { shape.value = it }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observe(scope: ReportScope): Flow<ReportSectionState> =
            shape
                .flatMapLatest { repository.observeFlow(scope, it) }
                .map { MonthlyFlowSection(it, reshape) }
    }

/** The three choices: what the columns show, how long each is, and how many there are. */
@Composable
private fun FlowControls(
    mode: FlowMode,
    onMode: (FlowMode) -> Unit,
    shape: FlowShape,
    onReshape: (FlowShape) -> Unit,
) {
    val modeLabels =
        mapOf(
            FlowMode.IN_AND_OUT to stringResource(R.string.reports_flow_mode_in_and_out),
            FlowMode.SAVED to stringResource(R.string.reports_flow_mode_saved),
        )
    val sizeLabels =
        mapOf(
            FlowBucketSize.MONTH to stringResource(R.string.reports_flow_size_month),
            FlowBucketSize.QUARTER to stringResource(R.string.reports_flow_size_quarter),
            FlowBucketSize.YEAR to stringResource(R.string.reports_flow_size_year),
        )
    val margin = Modifier.padding(horizontal = CentsDimens.rowHorizontalPadding)

    CentsSegmentedControl(
        options = FlowMode.entries,
        selected = mode,
        onSelect = onMode,
        label = { modeLabels.getValue(it) },
        modifier = margin.testTag(MonthlyFlowSection.MODE_TAG),
    )
    Spacer(Modifier.height(CentsDimens.spacingS))
    Row(margin) {
        CentsSegmentedControl(
            options = FlowBucketSize.entries,
            selected = shape.size,
            onSelect = { onReshape(shape.copy(size = it)) },
            label = { sizeLabels.getValue(it) },
            modifier = Modifier.weight(SIZE_CONTROL_WEIGHT).testTag(MonthlyFlowSection.SIZE_TAG),
        )
        Spacer(Modifier.width(CentsDimens.spacingS))
        CentsSegmentedControl(
            options = FlowShape.COUNTS,
            selected = shape.count,
            onSelect = { onReshape(shape.copy(count = it)) },
            label = { it.toString() },
            modifier = Modifier.weight(1f).testTag(MonthlyFlowSection.COUNT_TAG),
        )
    }
}

/** The tapped column in figures, and the way into its movements. */
@Composable
private fun BucketDetail(
    bucket: FlowBucket,
    size: FlowBucketSize,
    onOpen: () -> Unit,
) {
    val formatter = remember(size) { DateTimeFormatter.ofPattern(size.titlePattern(), Locale.getDefault()) }
    val dayFormatter = remember { DateTimeFormatter.ofPattern(CUT_DAY_PATTERN, Locale.getDefault()) }
    val net = bucket.summary.net

    // A column cut where the period ends says so, or "2026" read in August looks like a
    // whole year beside 2025's.
    CentsListRow(
        title = bucket.whole.from.format(formatter),
        subtitle =
            stringResource(R.string.reports_flow_until, bucket.counted.to.format(dayFormatter))
                .takeIf { bucket.counted.to < bucket.whole.to },
    )
    LegendRow(
        color = CentsTheme.colors.positiveText,
        label = stringResource(R.string.reports_flow_legend_income),
        amount = bucket.summary.income,
    )
    LegendRow(
        color = CentsTheme.colors.destructiveText,
        label = stringResource(R.string.reports_flow_legend_expenses),
        amount = bucket.summary.expenses,
    )
    // Said in words, and as an amount without a sign: "Overspent 85,10 €" is the answer, where
    // "−85,10 €" is a number to work out.
    AmountRow(
        label = stringResource(if (net.isNegative) R.string.reports_flow_overspent else R.string.reports_flow_saved),
        amount = if (net.isNegative) -net else net,
        valueColor = if (net.isNegative) CentsTheme.colors.destructiveText else CentsTheme.colors.positiveText,
    )
    // A quiet column has nothing to open.
    if (!bucket.isEmpty) {
        CentsActionRow(
            label = stringResource(R.string.reports_flow_show_movements),
            onClick = onOpen,
            modifier = Modifier.testTag(MonthlyFlowSection.OPEN_TAG),
        )
    }
}

/**
 * The columns, coloured from the same two tokens the transaction list uses for amounts, so a
 * colour means the same thing on both screens. Vico's own theme is overridden rather than
 * themed: its defaults come from Material, and a purple column would be exactly the "accent
 * that came from nowhere" ADR 0001 rules out.
 *
 * The tapped column is drawn in full and the others faded, which is how the chart says which
 * one the figures underneath are about.
 */
@Composable
internal fun FlowChart(
    flow: IncomeAndExpenses,
    mode: FlowMode,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positive = CentsTheme.colors.positive
    val destructive = CentsTheme.colors.destructive
    val labelColor = CentsTheme.colors.secondaryLabel
    val buckets = flow.buckets
    val producer = rememberFlowModel(buckets, mode)

    if (buckets.isEmpty()) return

    val size = flow.shape.size
    val labels =
        remember(buckets, size) {
            val formatter = DateTimeFormatter.ofPattern(size.axisPattern(), Locale.getDefault())
            buckets.map { it.whole.from.format(formatter) }
        }
    val labelStyle = TextStyle(color = labelColor, fontSize = AXIS_LABEL_SIZE)
    val width = if (buckets.size > FlowShape.DEFAULT_COUNT) NARROW_COLUMN_WIDTH else COLUMN_WIDTH
    val columns =
        remember(selected, positive, destructive, width) { FlowColumns(selected, positive, destructive, width) }
    // Every other label once there are more columns than fit a name each.
    val labelSpacing = if (buckets.size > FlowShape.DEFAULT_COUNT) 2 else 1
    val itemPlacer = remember(labelSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }) }
    // Vico hands taps to a marker controller, and only when the chart has a marker, so the
    // marker is one that draws nothing and the controller reports which column was tapped.
    val marker = remember { object : CartesianMarker {} }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val controller = remember { SelectOnTap { currentOnSelect(it) } }

    CartesianChartHost(
        chart =
            rememberCartesianChart(
                rememberColumnCartesianLayer(columnProvider = columns),
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
                        itemPlacer = itemPlacer,
                    ),
                marker = marker,
                markerController = controller,
            ),
        modelProducer = producer,
        modifier = modifier.fillMaxWidth().height(COLUMN_CHART_HEIGHT),
    )
}

/** The chart's numbers: two series side by side, or one of what was left. */
@Composable
private fun rememberFlowModel(
    buckets: List<FlowBucket>,
    mode: FlowMode,
): CartesianChartModelProducer {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(buckets, mode) {
        if (buckets.isEmpty()) return@LaunchedEffect
        producer.runTransaction {
            columnModel {
                when (mode) {
                    FlowMode.IN_AND_OUT -> {
                        series(buckets.map { it.summary.income.minorUnits })
                        series(buckets.map { it.summary.expenses.minorUnits })
                    }

                    FlowMode.SAVED -> {
                        series(buckets.map { it.summary.net.minorUnits })
                    }
                }
            }
            // Carried with the numbers rather than beside them, so the colours can never be
            // one mode's while the columns are still the other's.
            extras { it[FLOW_MODE_KEY] = mode }
        }
    }
    return producer
}

/**
 * A colour per column: green and red for income and expenses, or for what was saved and what
 * was overspent, faded on every column but the [selected] one.
 */
private class FlowColumns(
    private val selected: Int,
    positive: Color,
    destructive: Color,
    width: Dp,
) : ColumnCartesianLayer.ColumnProvider {
    private val green = LineComponent(Fill(positive), width)
    private val red = LineComponent(Fill(destructive), width)
    private val fadedGreen = LineComponent(Fill(positive.copy(alpha = FADED_ALPHA)), width)
    private val fadedRed = LineComponent(Fill(destructive.copy(alpha = FADED_ALPHA)), width)

    override fun getColumn(
        entry: ColumnCartesianLayerModel.Entry,
        extraStore: ExtraStore,
    ): LineComponent {
        val isGreen =
            when (extraStore.getOrNull(FLOW_MODE_KEY) ?: FlowMode.IN_AND_OUT) {
                FlowMode.IN_AND_OUT -> entry.seriesIndex == 0
                FlowMode.SAVED -> entry.y >= 0
            }
        val isSelected = entry.x.roundToInt() == selected
        return when {
            isGreen && isSelected -> green
            isGreen -> fadedGreen
            isSelected -> red
            else -> fadedRed
        }
    }

    override fun getWidestSeriesColumn(
        seriesKey: Any,
        seriesIndex: Int,
        extraStore: ExtraStore,
    ): LineComponent = green
}

/** Reports the column a tap landed on, and never shows a marker of its own. */
private class SelectOnTap(
    private val onSelect: (Int) -> Unit,
) : CartesianMarkerController {
    override val acceptsLongPress: Boolean get() = false

    override fun shouldAcceptInteraction(
        interaction: Interaction,
        targets: List<CartesianMarker.Target>,
    ): Boolean = interaction is Interaction.Tap

    override fun shouldShowMarker(
        interaction: Interaction,
        targets: List<CartesianMarker.Target>,
    ): Boolean {
        targets.firstOrNull()?.let { onSelect(it.x.roundToInt()) }
        return false
    }
}

/** A column's name on the axis: "Aug", "Q3 26", "2026". */
private fun FlowBucketSize.axisPattern(): String =
    when (this) {
        FlowBucketSize.MONTH -> "LLL"
        FlowBucketSize.QUARTER -> "QQQ yy"
        FlowBucketSize.YEAR -> "yyyy"
    }

/** A column's name over its figures: "August 2026", "3rd quarter 2026", "2026". */
private fun FlowBucketSize.titlePattern(): String =
    when (this) {
        FlowBucketSize.MONTH -> "LLLL yyyy"
        FlowBucketSize.QUARTER -> "QQQQ yyyy"
        FlowBucketSize.YEAR -> "yyyy"
    }

private val COLUMN_CHART_HEIGHT = 180.dp
private val COLUMN_WIDTH = 10.dp
private val NARROW_COLUMN_WIDTH = 6.dp
private val AXIS_LABEL_SIZE = 11.sp
private const val FADED_ALPHA = 0.35f

/** "31 Aug": where a cut column stops. */
private const val CUT_DAY_PATTERN = "d MMM"

/** Which mode the numbers in a chart model were drawn for. */
private val FLOW_MODE_KEY = ExtraStore.Key<FlowMode>()
private const val SIZE_CONTROL_WEIGHT = 1.6f

/** Cents stores money in minor units; an axis is labelled in whole ones. */
private const val MINOR_UNITS_PER_UNIT = 100.0
