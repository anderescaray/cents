package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.feature.filters.SelectableRow
import io.github.anderescaray.cents.ui.feature.filters.displayName
import io.github.anderescaray.cents.ui.theme.CentsDimens
import java.time.LocalDate

/** Which end of a custom range the picker is open on. */
private enum class RangeBound { FROM, TO }

/**
 * The Reports tab.
 *
 * One period and one account govern the whole screen. Every chart answers the same question —
 * "over this stretch of time, for this money" — so a selector per chart would let them drift
 * apart and quietly invite a comparison of August's spending against the whole year's income,
 * or of one account's outgoings against every account's balance.
 *
 * Below the selector this screen composes a list of sections and knows nothing about any of
 * them: not what they draw, not what they are made of, not how many there are. Adding a view
 * does not change this file — see [ReportSectionState].
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
        onSelectAccount = viewModel::selectAccount,
        onSetRange = viewModel::setCustomRange,
        onDrillDown = { filter ->
            // The filter is applied first and the tab switched second, so the list is
            // already narrowed by the time it slides in — no frame of every movement.
            viewModel.drillDown(filter)
            onOpenMovements()
        },
    )
}

/**
 * The stateless overload, which is what every Compose test on this tab renders.
 *
 * It takes a [ReportsUiState] and three callbacks and nothing else — no view model, no Hilt,
 * no database. Sections draw themselves from their own state, which is the reason this
 * property survived the tab growing a third view.
 */
@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onSelectPreset: (PeriodPreset) -> Unit,
    onSelectAccount: (Long?) -> Unit,
    onSetRange: (LocalDate, LocalDate) -> Unit,
    onDrillDown: (TransactionFilter) -> Unit,
) {
    var openPicker by rememberSaveable { mutableStateOf<RangeBound?>(null) }
    val actions = remember(onDrillDown) { ReportActions(onDrillDown = onDrillDown) }

    CentsLargeTitleScaffold(
        title = stringResource(R.string.tab_reports),
        bottomPadding = CentsDimens.tabBarHeight,
    ) {
        periodSection(state, onSelectPreset) { openPicker = it }
        accountSection(state, onSelectAccount)

        if (state.hasNoAccounts) {
            item(key = "no-accounts") { NoAccountsSection() }
        } else {
            state.sections.forEachIndexed { index, section ->
                item(key = section.key) {
                    section.Content(actions)
                    if (index != state.sections.lastIndex) {
                        Spacer(Modifier.height(CentsDimens.sectionSpacing))
                    }
                }
            }
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
 * it. Every chart reads the same period, so it is chosen once above them rather than once
 * beside each.
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

/**
 * Which account the tab is about, or all of them.
 *
 * It is the filter form's list of rows rather than a sheet or a screen of its own: the
 * question is small, the answer is worth being able to read without tapping anything, and
 * `SelectableRow` already says "one of these is chosen" the way iOS does. Archived accounts
 * are offered, because the charts have always counted their movements — hiding one here would
 * make an archived account's history unreachable from the tab that exists to look at history.
 *
 * It does not appear at all until there are two accounts to choose between. A picker with one
 * answer is a row that says what the screen already said.
 */
private fun LazyListScope.accountSection(
    state: ReportsUiState,
    onSelectAccount: (Long?) -> Unit,
) {
    if (!state.canPickAccount) return

    item(key = "account") {
        CentsListSection(header = stringResource(R.string.reports_account_header)) {
            SelectableRow(
                title = stringResource(R.string.reports_all_accounts),
                selected = state.accountId == null,
                onClick = { onSelectAccount(null) },
            )
            state.accounts.forEachIndexed { index, account ->
                SelectableRow(
                    title = account.displayName(),
                    selected = state.accountId == account.id,
                    last = index == state.accounts.lastIndex,
                    onClick = { onSelectAccount(account.id) },
                )
            }
        }
        Spacer(Modifier.height(CentsDimens.sectionSpacing))
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
