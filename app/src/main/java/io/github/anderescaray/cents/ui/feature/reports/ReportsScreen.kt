package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import java.time.LocalDate

/** Which end of a custom range the picker is open on. */
private enum class RangeBound { FROM, TO }

/**
 * The Reports tab.
 *
 * One period governs the whole screen. Both charts answer the same question — "over this
 * stretch of time" — so a selector per chart would let them drift apart and quietly invite
 * a comparison of August's spending against the whole year's income.
 */
@Composable
fun ReportsScreen(
    onOpenMovements: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ReportsScreen(
        state = state,
        onSelectPreset = viewModel::selectPreset,
        onSetRange = viewModel::setCustomRange,
        onDrillDown = { slice ->
            // The filter is applied first and the tab switched second, so the list is
            // already narrowed by the time it slides in — no frame of every movement.
            viewModel.drillDown(slice)
            onOpenMovements()
        },
    )
}

@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onSelectPreset: (PeriodPreset) -> Unit,
    onSetRange: (LocalDate, LocalDate) -> Unit,
    onDrillDown: (CategorySpending) -> Unit,
) {
    var openPicker by rememberSaveable { mutableStateOf<RangeBound?>(null) }

    CentsLargeTitleScaffold(
        title = stringResource(R.string.tab_reports),
        bottomPadding = CentsDimens.tabBarHeight,
    ) {
        periodSection(state, onSelectPreset) { openPicker = it }

        if (state.hasNoAccounts) {
            item(key = "no-accounts") { NoAccountsSection() }
        } else {
            spendingSection(state.spending, onDrillDown)
            item(key = "gap") { Spacer(Modifier.height(CentsDimens.sectionSpacing)) }
            flowSection(state)
        }
    }

    openPicker?.let { bound ->
        RangePicker(
            bound = bound,
            state = state,
            onSetRange = onSetRange,
            onDismiss = { openPicker = null },
        )
    }
}

/**
 * The one control that governs the whole screen, and the two date rows Custom brings with
 * it. Both charts read the same period, so it is chosen once above them rather than twice
 * beside them.
 */
private fun LazyListScope.periodSection(
    state: ReportsUiState,
    onSelectPreset: (PeriodPreset) -> Unit,
    onOpenPicker: (RangeBound) -> Unit,
) {
    item(key = "period") {
        val presets = remember { PeriodPreset.entries.toList() }
        // The segmented control takes a plain label function, so the strings are resolved
        // here rather than inside it: `stringResource` is composable and that lambda is not.
        val presetLabels = presets.associateWith { stringResource(it.labelRes) }

        CentsSegmentedControl(
            options = presets,
            selected = state.preset,
            onSelect = onSelectPreset,
            label = { presetLabels.getValue(it) },
            modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
        )
        Spacer(Modifier.height(CentsDimens.sectionSpacing))
    }

    if (state.preset == PeriodPreset.CUSTOM) {
        item(key = "range") {
            CentsListSection {
                RangeRow(R.string.reports_range_from, state.customFrom) { onOpenPicker(RangeBound.FROM) }
                RangeRow(R.string.reports_range_to, state.customTo, last = true) { onOpenPicker(RangeBound.TO) }
            }
            Spacer(Modifier.height(CentsDimens.sectionSpacing))
        }
    }
}

@Composable
private fun NoAccountsSection() {
    CentsListSection(footer = stringResource(R.string.reports_no_accounts_footer)) {
        CentsListRow(
            title = stringResource(R.string.reports_no_accounts_title),
            showSeparator = false,
        )
    }
}

@Composable
private fun RangePicker(
    bound: RangeBound,
    state: ReportsUiState,
    onSetRange: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    CentsDatePicker(
        selected = if (bound == RangeBound.FROM) state.customFrom else state.customTo,
        today = LocalDate.now(),
        onSelect = { picked ->
            if (bound == RangeBound.FROM) {
                onSetRange(picked, state.customTo)
            } else {
                onSetRange(state.customFrom, picked)
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/**
 * The donut and the categories it is made of.
 *
 * The legend *is* the list. Each row already has to carry the name, the amount and the
 * share, so putting the swatch on that row means one place to read rather than a key beside
 * the chart to match colours up against.
 */
private fun LazyListScope.spendingSection(
    breakdown: SpendingBreakdown?,
    onDrillDown: (CategorySpending) -> Unit,
) {
    item(key = "spending") {
        CentsListSection(header = stringResource(R.string.reports_spending_header)) {
            if (breakdown == null || breakdown.isEmpty) {
                EmptyChart(stringResource(R.string.reports_spending_empty))
            } else {
                Box(Modifier.padding(vertical = CentsDimens.spacingM)) {
                    SpendingDonut(breakdown, onSliceClick = onDrillDown)
                }
                CentsListRow(
                    title = stringResource(R.string.reports_spending_total),
                    value = MoneyFormatter.format(breakdown.total),
                )
                breakdown.slices.forEachIndexed { index, slice ->
                    CategoryRow(
                        slice = slice,
                        last = index == breakdown.slices.lastIndex,
                        onClick = { onDrillDown(slice) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.flowSection(state: ReportsUiState) {
    item(key = "flow") {
        val months = state.monthlyFlow
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
}
