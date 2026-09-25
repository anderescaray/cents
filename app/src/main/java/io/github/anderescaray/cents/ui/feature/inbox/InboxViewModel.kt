package io.github.anderescaray.cents.ui.feature.inbox

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.repository.CategoryRepository
import io.github.anderescaray.cents.domain.repository.TransactionRepository
import io.github.anderescaray.cents.ui.feature.transactionform.toCategoryKind
import io.github.anderescaray.cents.ui.util.Today
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One movement waiting for a category, and the categories it can be filed under. */
@Immutable
data class InboxItem(
    val details: TransactionDetails,
    /** Every live category of the movement's own kind: an expense is never filed as income. */
    val choices: List<Category>,
)

@Immutable
data class InboxUiState(
    val items: List<InboxItem> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** A movement that could not be filed: gone meanwhile, or no longer a movement of its own. */
    val writeFailed: Boolean = false,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

/**
 * The uncategorized inbox (PRD §4.6): every income or expense recorded without a category,
 * newest first, each with its categories one tap away.
 *
 * It is the list's own *uncategorized* filter underneath — the same SQL, excluding transfer legs
 * and a trade's cash, which carry no category because they are never classified — so what this
 * screen lists, the count on the movements tab and the Reports ring's *Uncategorized* slice all
 * agree. Filing one is a write, and the flow takes it off the screen; nothing is removed by hand.
 */
@HiltViewModel
class InboxViewModel
    @Inject
    constructor(
        private val transactions: TransactionRepository,
        categories: CategoryRepository,
        today: Today,
    ) : ViewModel() {
        private val failed = MutableStateFlow(false)

        val uiState: StateFlow<InboxUiState> =
            combine(
                transactions.observeTransactions(TransactionFilter(category = CategoryFilter.Uncategorized)),
                categories.observeAllCategories(),
                today.date,
                failed,
            ) { waiting, all, day, writeFailed ->
                val live = all.filterNot { it.archived }
                InboxUiState(
                    items =
                        waiting.map { details ->
                            val kind = details.transaction.kind.toCategoryKind()
                            InboxItem(details, live.filter { it.kind == kind })
                        },
                    today = day,
                    writeFailed = writeFailed,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = InboxUiState(),
            )

        fun categorize(
            transactionId: Long,
            categoryId: Long,
        ) {
            failed.update { false }
            viewModelScope.launch {
                val filed =
                    try {
                        transactions.categorize(transactionId, categoryId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                if (!filed) failed.update { true }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
