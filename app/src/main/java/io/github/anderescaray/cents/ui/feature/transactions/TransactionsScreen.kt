package io.github.anderescaray.cents.ui.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.LocalDate

@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        TransactionsList(state = state, onAddTransaction = onAddTransaction, onOpenTransaction = onOpenTransaction)

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
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.tab_transactions),
        bottomPadding = CentsDimens.sectionSpacing,
        trailing = {
            Text(
                text = stringResource(R.string.action_add),
                style = CentsTheme.typography.title2,
                color = CentsTheme.colors.accent,
                modifier = Modifier.clickable(onClick = onAddTransaction),
            )
        },
    ) {
        state.monthSummary?.let { summary -> monthSummary(summary) }
        if (state.isEmpty) {
            emptyState()
        }
        state.days.forEach { day -> day(day, state.today, onOpenTransaction) }
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
