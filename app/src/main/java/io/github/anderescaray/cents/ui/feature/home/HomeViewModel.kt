package io.github.anderescaray.cents.ui.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.MoneyFiguresRepository
import io.github.anderescaray.cents.domain.repository.PortfolioRepository
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.domain.repository.reports.SpendingByCategoryRepository
import io.github.anderescaray.cents.domain.usecase.netWorthOf
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import io.github.anderescaray.cents.ui.util.Today
import io.github.anderescaray.cents.ui.util.deviceCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    /**
     * Cash and bank, invested and their total, from the same formula as Wallet, and null when
     * they cannot honestly be added or none is chosen. Drawn at the top as [figures] says
     * (`DECISIONS.md` D34).
     */
    val netWorth: NetWorth? = null,
    val figures: MoneyFigures = MoneyFigures(),
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
        portfolio: PortfolioRepository,
        figures: MoneyFiguresRepository,
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

        /**
         * The figures at the top. The balances and the portfolio are only read while one is
         * chosen: Home is the screen the app opens on, and with none chosen that work is for
         * nothing.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val money =
            figures.observe().flatMapLatest { chosen ->
                if (!chosen.any) {
                    flowOf(HomeMoney(netWorth = null, figures = chosen))
                } else {
                    combine(
                        accounts.observeAccountsWithBalances(includeArchived = true),
                        portfolio.observePositions(),
                    ) { all, held -> HomeMoney(netWorth = netWorthOf(all, held), figures = chosen) }
                }
            }

        private val waiting =
            combine(
                rules.observePending().map { it.size },
                accounts.observeAccounts().map { it.isNotEmpty() },
            ) { pending, hasAccounts -> pending to hasAccounts }

        val uiState: StateFlow<HomeUiState> =
            combine(
                breakdown,
                summary,
                waiting,
                money,
                today.date,
            ) { spent, totals, (pending, hasAccounts), held, day ->
                HomeUiState(
                    today = day,
                    hasAccounts = hasAccounts,
                    spending = spent,
                    summary = totals,
                    pendingCount = pending,
                    netWorth = held.netWorth,
                    figures = held.figures,
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

        private data class HomeMoney(
            val netWorth: NetWorth?,
            val figures: MoneyFigures,
        )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
