package io.github.anderescaray.cents.ui.feature.budgets

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Budget
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.BudgetRepository
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.util.AmountEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class BudgetEditUiState(
    val isNew: Boolean = true,
    /** Expense categories that can take this limit: the ones without one, and its own. */
    val choices: List<Category> = emptyList(),
    val categoryId: Long? = null,
    val amountInput: String = AmountEntry.EMPTY,
    val currency: String = "EUR",
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val writeFailed: Boolean = false,
    val confirmingDelete: Boolean = false,
    val finished: Boolean = false,
) {
    val limit: Money get() = Money(AmountEntry.toMinorUnits(amountInput), currency)

    val category: Category? get() = choices.firstOrNull { it.id == categoryId }

    /** A limit of nothing is not a budget: it is a category you mean never to spend in. */
    val canSave: Boolean get() = loaded && !saving && category != null && limit.minorUnits > 0
}

/**
 * Setting one limit: which expense category, and how much a month.
 *
 * The category is fixed once the budget exists. Moving a limit to another category is deleting
 * one and setting another, and saying so is clearer than a picker that quietly does both.
 */
@HiltViewModel
class BudgetEditViewModel
    @Inject
    constructor(
        private val budgets: BudgetRepository,
        private val categories: CategoryRepository,
        private val accounts: AccountRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val budgetId: Long? =
            savedStateHandle
                .toRoute<Destination.BudgetEdit>()
                .budgetId
                .takeUnless { it == Destination.BudgetEdit.NEW }

        private val form = MutableStateFlow(BudgetEditUiState(isNew = budgetId == null))
        val uiState: StateFlow<BudgetEditUiState> = form.asStateFlow()

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val existing = budgetId?.let { budgets.findById(it) }
            val taken =
                budgets
                    .observeBudgets()
                    .first()
                    .map { it.categoryId }
                    .toSet() - setOfNotNull(existing?.categoryId)
            val expense = categories.observeCategories(CategoryKind.EXPENSE).first()
            val currency =
                existing?.limit?.currency
                    ?: accounts
                        .observeAccounts()
                        .first()
                        .firstOrNull()
                        ?.currency
                    ?: FALLBACK_CURRENCY
            val choices = expense.filter { it.id !in taken }
            form.update { state ->
                state.copy(
                    choices = choices,
                    categoryId = existing?.categoryId ?: choices.singleOrNull()?.id,
                    amountInput =
                        existing?.let {
                            AmountEntry.fromMinorUnits(
                                it.limit.minorUnits,
                            )
                        } ?: state.amountInput,
                    currency = currency,
                    loaded = true,
                )
            }
        }

        fun chooseCategory(id: Long) {
            if (!form.value.isNew) return
            form.update { it.copy(categoryId = id) }
        }

        fun appendDigit(digit: Char) {
            form.update { it.copy(amountInput = AmountEntry.append(it.amountInput, digit)) }
        }

        fun appendDecimalSeparator() {
            form.update { it.copy(amountInput = AmountEntry.append(it.amountInput, AmountEntry.SEPARATOR)) }
        }

        fun deleteDigit() {
            form.update { it.copy(amountInput = AmountEntry.delete(it.amountInput)) }
        }

        fun save() {
            val state = form.value
            val categoryId = state.categoryId
            if (!state.canSave || categoryId == null) return
            form.update { it.copy(saving = true, writeFailed = false) }
            viewModelScope.launch {
                val written =
                    write {
                        budgets.saveBudget(
                            Budget(id = budgetId ?: Budget.NEW, categoryId = categoryId, limit = state.limit),
                        )
                    }
                form.update { it.copy(saving = false, writeFailed = !written, finished = written) }
            }
        }

        /** Rule 7: deleting asks first, even a limit, because it cannot be put back. */
        fun requestDelete() {
            if (budgetId == null) return
            form.update { it.copy(confirmingDelete = true) }
        }

        fun cancelDelete() {
            form.update { it.copy(confirmingDelete = false) }
        }

        fun confirmDelete() {
            val id = budgetId ?: return
            form.update { it.copy(confirmingDelete = false, saving = true) }
            viewModelScope.launch {
                val deleted = write { budgets.deleteBudget(id) }
                form.update { it.copy(saving = false, writeFailed = !deleted, finished = deleted) }
            }
        }

        /**
         * A write that can be refused — the category deleted from another screen while this one was
         * open — becomes a footer rather than an exception escaping the view model's scope.
         */
        private suspend fun write(action: suspend () -> Unit): Boolean =
            try {
                action()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }

        private companion object {
            const val FALLBACK_CURRENCY = "EUR"
        }
    }
