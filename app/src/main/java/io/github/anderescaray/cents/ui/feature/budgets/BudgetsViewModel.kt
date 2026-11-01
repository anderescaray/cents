package io.github.anderescaray.cents.ui.feature.budgets

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.BudgetRepository
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.domain.usecase.BudgetProgress
import io.github.anderescaray.cents.domain.usecase.budgetProgressOf
import io.github.anderescaray.cents.ui.util.Today
import io.github.anderescaray.cents.ui.util.deviceCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class BudgetsUiState(
    val rows: List<BudgetProgress> = emptyList(),
    /** Whether there is still something to give a limit to: the month as a whole, or a category. */
    val canAdd: Boolean = false,
    /** Whether there is any expense category at all, so the empty state can say what to do first. */
    val hasExpenseCategories: Boolean = false,
    val month: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * Every budget against the month it is in: what went out, what is still known to go out, and how
 * close that comes to the limit. Nothing here adds up movements: the SQL does, and the arithmetic
 * of a budget is [budgetProgressOf], which has no Android in it.
 */
@HiltViewModel
class BudgetsViewModel
    @Inject
    constructor(
        budgets: BudgetRepository,
        categories: CategoryRepository,
        rules: RecurringRuleRepository,
        accounts: AccountRepository,
        today: Today,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        private val spentThisMonth =
            combine(
                accounts
                    .observeAccounts()
                    .map { it.firstOrNull()?.currency ?: deviceCurrency() }
                    .distinctUntilChanged(),
                today.date.map { YearMonth.from(it) }.distinctUntilChanged(),
            ) { currency, month -> currency to month }
                .flatMapLatest { (currency, month) ->
                    val from = month.atDay(1)
                    val to = month.atEndOfMonth()
                    combine(
                        budgets.observeSpentByCategory(from, to, currency),
                        budgets.observeSpentInAll(from, to, currency),
                        ::Pair,
                    )
                }

        val uiState: StateFlow<BudgetsUiState> =
            combine(
                budgets.observeBudgets(),
                categories.observeCategories(CategoryKind.EXPENSE),
                spentThisMonth,
                // Two flows as one: combine takes five, and a charge's rule and the day it was moved
                // to are one question anyway.
                combine(rules.observeRules(), rules.observePostponements(), ::Pair),
                today.date,
            ) { limits, expenseCategories, (spent, spentInAll), (allRules, postponements), day: LocalDate ->
                val budgeted = limits.map { it.categoryId }.toSet()
                BudgetsUiState(
                    rows = budgetProgressOf(limits, expenseCategories, spent, allRules, day, postponements, spentInAll),
                    canAdd = null !in budgeted || expenseCategories.any { it.id !in budgeted },
                    hasExpenseCategories = expenseCategories.isNotEmpty(),
                    month = YearMonth.from(day),
                    isLoading = false,
                )
            }.flowOn(Dispatchers.Default).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = BudgetsUiState(),
            )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
