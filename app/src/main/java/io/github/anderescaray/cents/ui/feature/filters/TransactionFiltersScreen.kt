package io.github.anderescaray.cents.ui.feature.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.components.weekdays
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import java.time.format.TextStyle
import java.util.Locale

/**
 * Everything that narrows the transaction list except the search field, on one screen.
 *
 * It is one screen on purpose. "What did I spend on restaurants in July" is a single
 * question with three parts, and answering it through three separate pickers would make the
 * owner hold the other two in their head while choosing the third.
 */
@Composable
fun TransactionFiltersScreen(
    onDone: () -> Unit,
    viewModel: TransactionFiltersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .imePadding(),
    ) {
        CentsModalBar(
            title = stringResource(R.string.filters_title),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.filters_apply),
            onConfirm = {
                viewModel.apply()
                onDone()
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = CentsDimens.spacingL,
                    // The last row here is an action. Without the navigation-bar inset it
                    // sits under the gesture bar, where it is hard to tap and easy to miss.
                    bottom =
                        CentsDimens.sectionSpacing +
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.sectionSpacing),
        ) {
            kindSection(state, viewModel)
            dateSection(state, viewModel)
            weekdaySection(state, viewModel)
            accountSection(state, viewModel)
            categorySection(state, viewModel)
            amountSection(state, viewModel)
            clearSection(state, viewModel)
        }
    }

    state.openPicker?.let { bound ->
        val selected =
            when (bound) {
                DateBound.FROM -> state.draft.from
                DateBound.TO -> state.draft.to
            } ?: state.today
        CentsDatePicker(
            selected = selected,
            today = state.today,
            onSelect = viewModel::setBound,
            onDismiss = viewModel::dismissPicker,
        )
    }
}

private fun LazyListScope.kindSection(
    state: TransactionFiltersUiState,
    viewModel: TransactionFiltersViewModel,
) {
    item(key = "kind") {
        val labels =
            mapOf(
                null to stringResource(R.string.filters_all),
                TransactionKind.INCOME to stringResource(R.string.kind_income),
                TransactionKind.EXPENSE to stringResource(R.string.kind_expense),
            )
        CentsSegmentedControl(
            options = listOf(null, TransactionKind.INCOME, TransactionKind.EXPENSE),
            selected = state.draft.kind,
            onSelect = viewModel::setKind,
            label = { labels.getValue(it) },
            modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
        )
    }
}

/** Presets first, because they are the answer nine times out of ten. */
private fun LazyListScope.dateSection(
    state: TransactionFiltersUiState,
    viewModel: TransactionFiltersViewModel,
) {
    item(key = "date") {
        val presets =
            listOf(
                DateRangePreset.ANY to stringResource(R.string.filters_date_any),
                DateRangePreset.THIS_MONTH to stringResource(R.string.filters_date_this_month),
                DateRangePreset.LAST_MONTH to stringResource(R.string.filters_date_last_month),
                DateRangePreset.THIS_YEAR to stringResource(R.string.filters_date_this_year),
                DateRangePreset.CUSTOM to stringResource(R.string.filters_date_custom),
            )
        CentsListSection(
            header = stringResource(R.string.filters_date_header),
            footer = stringResource(R.string.filters_date_inverted).takeIf { state.draft.dateRangeInverted },
        ) {
            presets.forEach { (preset, label) ->
                SelectableRow(
                    title = label,
                    selected = state.preset == preset,
                    onClick = { viewModel.setPreset(preset) },
                )
            }
            if (state.preset == DateRangePreset.CUSTOM) {
                BoundRow(R.string.filters_date_from, state.draft.from) { viewModel.openPicker(DateBound.FROM) }
                BoundRow(R.string.filters_date_to, state.draft.to, last = true) {
                    viewModel.openPicker(DateBound.TO)
                }
            }
        }
    }
}

/**
 * One day of the week, so a *spending by weekday* bar can be drilled into and the narrowing
 * it applies can be seen and cleared here like any other (D10). Days start on the locale's
 * first day, the same order the chart draws them in.
 */
private fun LazyListScope.weekdaySection(
    state: TransactionFiltersUiState,
    viewModel: TransactionFiltersViewModel,
) {
    item(key = "weekday") {
        val locale = Locale.getDefault()
        CentsListSection(header = stringResource(R.string.filters_weekday_header)) {
            SelectableRow(
                title = stringResource(R.string.filters_weekday_any),
                selected = state.draft.weekday == null,
                onClick = { viewModel.setWeekday(null) },
            )
            val days = weekdays(locale)
            days.forEachIndexed { index, day ->
                SelectableRow(
                    title = day.getDisplayName(TextStyle.FULL, locale),
                    selected = state.draft.weekday == day,
                    last = index == days.lastIndex,
                    onClick = { viewModel.setWeekday(day) },
                )
            }
        }
    }
}

private fun LazyListScope.accountSection(
    state: TransactionFiltersUiState,
    viewModel: TransactionFiltersViewModel,
) {
    item(key = "account") {
        CentsListSection(header = stringResource(R.string.filters_account_header)) {
            SelectableRow(
                title = stringResource(R.string.filters_all_accounts),
                selected = state.draft.accountId == null,
                onClick = { viewModel.setAccount(null) },
            )
            state.accounts.forEachIndexed { index, account ->
                SelectableRow(
                    title = account.displayName(),
                    selected = state.draft.accountId == account.id,
                    last = index == state.accounts.lastIndex,
                    onClick = { viewModel.setAccount(account.id) },
                )
            }
        }
    }
}

private fun LazyListScope.categorySection(
    state: TransactionFiltersUiState,
    viewModel: TransactionFiltersViewModel,
) {
    item(key = "category") {
        CentsListSection(header = stringResource(R.string.filters_category_header)) {
            SelectableRow(
                title = stringResource(R.string.filters_all_categories),
                selected = state.draft.category == CategoryFilter.All,
                onClick = { viewModel.setCategory(CategoryFilter.All) },
            )
            SelectableRow(
                title = stringResource(R.string.transactions_uncategorized),
                selected = state.draft.category == CategoryFilter.Uncategorized,
                last = state.categories.isEmpty(),
                onClick = { viewModel.setCategory(CategoryFilter.Uncategorized) },
            )
            state.categories.forEachIndexed { index, category ->
                SelectableRow(
                    title = category.displayName(),
                    selected = state.draft.category == CategoryFilter.One(category.id),
                    last = index == state.categories.lastIndex,
                    onClick = { viewModel.setCategory(CategoryFilter.One(category.id)) },
                )
            }
        }
    }
}

/**
 * Both bounds are typed in euros with a comma, the way the movement keypad works, and the
 * footer says the range back as money so a half-typed bound is still readable.
 */
private fun LazyListScope.amountSection(
    state: TransactionFiltersUiState,
    viewModel: TransactionFiltersViewModel,
) {
    item(key = "amount") {
        CentsListSection(
            header = stringResource(R.string.filters_amount_header),
            footer =
                if (state.draft.amountRangeInverted) {
                    stringResource(R.string.filters_amount_inverted)
                } else {
                    state.draft.amountRangeLabel(state.currency)
                },
        ) {
            AmountField(
                input = state.minAmountInput,
                placeholder = stringResource(R.string.filters_amount_min),
                onChange = viewModel::setMinAmount,
            )
            AmountField(
                input = state.maxAmountInput,
                placeholder = stringResource(R.string.filters_amount_max),
                onChange = viewModel::setMaxAmount,
                last = true,
            )
        }
    }
}

private fun LazyListScope.clearSection(
    state: TransactionFiltersUiState,
    viewModel: TransactionFiltersViewModel,
) {
    item(key = "clear") {
        CentsListSection(footer = stringResource(R.string.filters_clear_footer)) {
            CentsActionRow(
                label = stringResource(R.string.filters_clear),
                onClick = viewModel::clear,
                destructive = !state.isPristine,
            )
        }
    }
}
