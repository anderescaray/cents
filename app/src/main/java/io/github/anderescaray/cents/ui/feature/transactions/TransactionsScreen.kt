package io.github.anderescaray.cents.ui.feature.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
        state.days.forEach { day -> day(day) }
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

private fun LazyListScope.day(day: TransactionDay) {
    item(key = "day-${day.date}") {
        CentsListSection(header = day.date.headerLabel()) {
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
                )
            }
        }
    }
}

@Composable
private fun LocalDate.headerLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> stringResource(R.string.date_today)
        today.minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}
