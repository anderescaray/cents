package io.github.anderescaray.cents.ui.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.domain.repository.reports.SpendingByCategoryRepository
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import io.github.anderescaray.cents.ui.util.Today
import io.github.anderescaray.cents.ui.util.deviceCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * The month at a glance, under the two things Home is for — recording a movement and filing the
 * ones that arrived on their own, which the inbox's own view model answers.
 */
@Immutable
data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val hasAccounts: Boolean = true,
    /** Where this month's money went, largest first: the same SQL as the Reports ring. */
    val spending: SpendingBreakdown? = null,
    val summary: PeriodSummary? = null,
    /** Bills and salaries waiting for the figure only the owner knows. */
    val pendingCount: Int = 0,
    val isLoading: Boolean = true,
) {
    val month: YearMonth get() = YearMonth.from(today)

    /** What was left this month: income less expenses. Null until both are known. */
    val left: Money? get() = summary?.let { it.income - it.expenses }
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        accounts: AccountRepository,
        transactions: TransactionRepository,
        spending: SpendingByCategoryRepository,
        rules: RecurringRuleRepository,
        private val filters: TransactionFilterStore,
        today: Today,
    ) : ViewModel() {
        private val currency =
            accounts
                .observeAccounts()
                .map { it.firstOrNull()?.currency ?: deviceCurrency() }
                .distinctUntilChanged()

        private val scope =
            combine(currency, today.date.map { YearMonth.from(it) }.distinctUntilChanged()) { code, month ->
                ReportScope(period = ReportPeriod.Month(month), currency = code)
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val breakdown = scope.flatMapLatest { spending.observeSpendingByCategory(it) }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val summary =
            scope.flatMapLatest { transactions.observeSummary(it.period.from, it.period.to, it.currency) }

        val uiState: StateFlow<HomeUiState> =
            combine(
                breakdown,
                summary,
                rules.observePending().map { it.size },
                accounts.observeAccounts().map { it.isNotEmpty() },
                today.date,
            ) { spent, totals, pending, hasAccounts, day ->
                HomeUiState(
                    today = day,
                    hasAccounts = hasAccounts,
                    spending = spent,
                    summary = totals,
                    pendingCount = pending,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = HomeUiState(),
            )

        /** Narrows the movement list to one category's spending this month, for the row tapped. */
        fun openCategory(slice: CategorySpending) {
            val breakdown = uiState.value.spending ?: return
            filters.apply(breakdown.filterFor(slice), clearQuery = true)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
