package io.github.anderescaray.cents.ui.feature.groups

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.GroupSummary
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.GroupRepository
import io.github.anderescaray.cents.ui.util.deviceCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class GroupsUiState(
    val groups: List<GroupSummary> = emptyList(),
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}

/**
 * Every group and what it came to, most recent first (PRD §4.9). The sums are SQL's; this only
 * picks the currency to say them in, the same way the Budgets screen does.
 */
@HiltViewModel
class GroupsViewModel
    @Inject
    constructor(
        groups: GroupRepository,
        accounts: AccountRepository,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<GroupsUiState> =
            accounts
                .observeAccounts()
                .map { it.firstOrNull()?.currency ?: deviceCurrency() }
                .distinctUntilChanged()
                .flatMapLatest { currency -> groups.observeSummaries(currency) }
                .map { GroupsUiState(groups = it, isLoading = false) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = GroupsUiState(),
                )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
