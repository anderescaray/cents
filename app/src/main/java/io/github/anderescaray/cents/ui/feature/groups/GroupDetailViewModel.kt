package io.github.anderescaray.cents.ui.feature.groups

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.GroupSummary
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.GroupRepository
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.util.deviceCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class GroupDetailUiState(
    val summary: GroupSummary? = null,
    /** What went out, per category, largest first: the ring. */
    val spending: List<CategorySpending> = emptyList(),
    /** What came in, per category: a list under it, for a group that earned anything. */
    val income: List<CategorySpending> = emptyList(),
    val isLoading: Boolean = true,
) {
    /** Loaded, and no longer there: deleted from its own editor, which this screen sits under. */
    val isGone: Boolean get() = !isLoading && summary == null
}

/**
 * One group: what it came to, where the money went and where it came from, and the way into its
 * movements (PRD §4.9).
 *
 * Opening movements writes the list's filter and leaves the navigation to the screen, the way a
 * slice on the Reports tab does — so the list the owner lands on is the same list, narrowed, with
 * the filter visible and clearable there rather than a second list pretending to be the first.
 */
@HiltViewModel
class GroupDetailViewModel
    @Inject
    constructor(
        groups: GroupRepository,
        accounts: AccountRepository,
        private val filters: TransactionFilterStore,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val groupId: Long = savedStateHandle.toRoute<Destination.GroupDetail>().groupId

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<GroupDetailUiState> =
            accounts
                .observeAccounts()
                .map { it.firstOrNull()?.currency ?: deviceCurrency() }
                .distinctUntilChanged()
                .flatMapLatest { currency ->
                    combine(
                        groups.observeSummary(groupId, currency),
                        groups.observeByCategory(groupId, TransactionKind.EXPENSE, currency),
                        groups.observeByCategory(groupId, TransactionKind.INCOME, currency),
                    ) { summary, spending, income ->
                        GroupDetailUiState(summary = summary, spending = spending, income = income, isLoading = false)
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = GroupDetailUiState(),
                )

        /** Every movement in the group, in the list. */
        fun showMovements() = filters.apply(TransactionFilter(groupId = groupId), clearQuery = true)

        /**
         * The movements behind one slice or one row: this group, that category, that direction —
         * the kind too, or an income filed under Restaurants would sit in a list that claims to
         * add up to the Restaurants slice of what went out.
         */
        fun showMovements(
            slice: CategorySpending,
            kind: TransactionKind,
        ) = filters.apply(
            TransactionFilter(
                groupId = groupId,
                category = slice.categoryId?.let(CategoryFilter::One) ?: CategoryFilter.Uncategorized,
                kind = kind,
            ),
            clearQuery = true,
        )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
