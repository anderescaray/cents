package io.github.anderescaray.cents.ui.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSearchField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.LocalDate

@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenFilters: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        TransactionsList(
            state = state,
            onAddTransaction = onAddTransaction,
            onOpenTransaction = onOpenTransaction,
            onOpenFilters = onOpenFilters,
            onQueryChange = viewModel::setQuery,
            onClearFilter = viewModel::clearFilter,
        )

        if (state.undoableDeletion != null) {
            UndoBanner(
                onUndo = viewModel::undoDeletion,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(CentsDimens.screenMargin),
            )
        }
    }
}

@Composable
private fun TransactionsList(
    state: TransactionsUiState,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenFilters: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearFilter: () -> Unit,
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.tab_transactions),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { FiltersButton(count = state.filter.activeCount, onClick = onOpenFilters) },
        trailing = {
            Text(
                text = stringResource(R.string.action_add),
                style = CentsTheme.typography.title2,
                color = CentsTheme.colors.accent,
                modifier = Modifier.clickable(onClick = onAddTransaction),
            )
        },
    ) {
        searchField(query = state.query, onQueryChange = onQueryChange)

        // Two different headline numbers, because there are two different questions. With
        // nothing asked, the month is what the list is about; once something is asked, the
        // answer is what the filter matched, and the month is beside the point.
        if (state.isNarrowed) {
            matchSummary(state)
        } else {
            state.monthSummary?.let { summary -> monthSummary(summary) }
        }

        if (state.isEmpty) {
            if (state.isNarrowed) noMatchesState(onClearFilter) else emptyState()
        }
        state.days.forEach { day -> day(day, state.today, onOpenTransaction) }
    }
}

/**
 * The way into the filter form, saying how many things are currently narrowing the list.
 *
 * The count is the point: a filtered list that looks like an unfiltered one is how someone
 * concludes their movements have disappeared.
 */
@Composable
private fun RowScope.FiltersButton(
    count: Int,
    onClick: () -> Unit,
) {
    Text(
        text =
            if (count == 0) {
                stringResource(R.string.filters_open)
            } else {
                stringResource(R.string.filters_open_active, count)
            },
        style = CentsTheme.typography.body,
        color = CentsTheme.colors.accent,
        // A bare Text is a few points tall. The tap target is the row height, whatever the
        // label happens to measure.
        modifier =
            Modifier
                .testTag(FILTERS_BUTTON_TAG)
                .clip(RoundedCornerShape(CentsDimens.spacingS))
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = CentsDimens.minRowHeight)
                .wrapContentHeight(Alignment.CenterVertically),
    )
}

private fun LazyListScope.searchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    item(key = "search") {
        CentsSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.search_placeholder),
            modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
        )
    }
}

/** What the filter matched: how many, and what they come to. */
private fun LazyListScope.matchSummary(state: TransactionsUiState) {
    item(key = "matches") {
        CentsListSection(header = stringResource(R.string.filters_results_header)) {
            val summary = state.matchSummary
            CentsListRow(
                title =
                    pluralStringResource(
                        R.plurals.filters_match_count,
                        state.matchCount,
                        state.matchCount,
                    ),
                // Mixed currencies have no total, the same refusal the accounts list makes.
                value = summary?.let { MoneyFormatter.formatSigned(it.net) },
                valueColor =
                    if (summary != null && summary.net.isNegative) {
                        CentsTheme.colors.destructive
                    } else {
                        CentsTheme.colors.label
                    },
                showSeparator = summary != null,
            )
            if (summary != null) {
                CentsListRow(
                    title = stringResource(R.string.summary_income),
                    value = MoneyFormatter.format(summary.income),
                    valueColor = CentsTheme.colors.positive,
                )
                CentsListRow(
                    title = stringResource(R.string.summary_expenses),
                    value = MoneyFormatter.format(summary.expenses),
                    valueColor = CentsTheme.colors.destructive,
                    showSeparator = false,
                )
            }
        }
    }
}

/**
 * Nothing matched — which is not the same as having no movements, and must not look like
 * it. The way out is on the screen rather than back through the filter form.
 */
private fun LazyListScope.noMatchesState(onClearFilter: () -> Unit) {
    item(key = "no-matches") {
        CentsListSection(footer = stringResource(R.string.filters_no_matches_footer)) {
            CentsListRow(title = stringResource(R.string.filters_no_matches_title))
            CentsActionRow(
                label = stringResource(R.string.filters_show_everything),
                onClick = onClearFilter,
                modifier = Modifier.testTag(SHOW_EVERYTHING_TAG),
            )
        }
    }
}

/** The one place a deletion can be taken back, for as long as it is on screen. */
@Composable
private fun UndoBanner(
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(BANNER_RADIUS.dp))
                .background(CentsTheme.colors.barBackground)
                .padding(horizontal = CentsDimens.spacingL, vertical = CentsDimens.spacingM),
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.transaction_deleted),
            style = CentsTheme.typography.subhead,
            color = CentsTheme.colors.label,
        )
        Text(
            text = stringResource(R.string.action_undo),
            style = CentsTheme.typography.headline,
            color = CentsTheme.colors.accent,
            modifier = Modifier.clickable(onClick = onUndo),
        )
    }
}

private fun LazyListScope.monthSummary(summary: PeriodSummary) {
    item(key = "summary") {
        CentsListSection(header = stringResource(R.string.transactions_this_month)) {
            CentsListRow(
                title = stringResource(R.string.summary_income),
                value = MoneyFormatter.format(summary.income),
                valueColor = CentsTheme.colors.positive,
            )
            CentsListRow(
                title = stringResource(R.string.summary_expenses),
                value = MoneyFormatter.format(summary.expenses),
                valueColor = CentsTheme.colors.destructive,
            )
            CentsListRow(
                title = stringResource(R.string.summary_net),
                value = MoneyFormatter.formatSigned(summary.net),
                valueColor =
                    if (summary.net.isNegative) {
                        CentsTheme.colors.destructive
                    } else {
                        CentsTheme.colors.label
                    },
                showSeparator = false,
            )
        }
    }
}

private fun LazyListScope.emptyState() {
    item(key = "empty") {
        CentsListSection(footer = stringResource(R.string.transactions_empty_footer)) {
            CentsListRow(
                title = stringResource(R.string.transactions_empty_title),
                showSeparator = false,
            )
        }
    }
}

private fun LazyListScope.day(
    day: TransactionDay,
    today: LocalDate,
    onOpenTransaction: (Long) -> Unit,
) {
    item(key = "day-${day.date}") {
        CentsListSection(header = day.date.relativeLabel(today)) {
            day.items.forEachIndexed { index, details ->
                val transaction = details.transaction
                CentsListRow(
                    title =
                        details.categoryName
                            ?: stringResource(R.string.transactions_uncategorized),
                    subtitle = transaction.note?.takeIf { it.isNotBlank() } ?: details.accountName,
                    value = MoneyFormatter.formatSigned(transaction.signedAmount),
                    valueColor =
                        if (transaction.kind == TransactionKind.INCOME) {
                            CentsTheme.colors.positive
                        } else {
                            CentsTheme.colors.label
                        },
                    showSeparator = index != day.items.lastIndex,
                    onClick = { onOpenTransaction(transaction.id) },
                )
            }
        }
    }
}

private const val BANNER_RADIUS = 14

internal const val FILTERS_BUTTON_TAG = "openFilters"
internal const val SHOW_EVERYTHING_TAG = "showEverything"
