package io.github.anderescaray.cents.ui.feature.capture

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One chosen app, and the name of the account what it announces lands in.
 *
 * [accountName] is null when that account is gone or archived: capture then keeps nothing from
 * the app (`CaptureOutcome.IgnoreReason.NO_ACCOUNT`), and the screen has to say so rather than
 * show an app that looks set up and silently is not.
 */
@Immutable
data class CaptureSourceRow(
    val source: CaptureSource,
    val accountName: String?,
)

@Immutable
data class CaptureUiState(
    val enabled: Boolean = false,
    val sources: List<CaptureSourceRow> = emptyList(),
    /** The accounts an app can be tied to: the active ones. */
    val accounts: List<Account> = emptyList(),
    val expenseWordCount: Int = 0,
    val incomeWordCount: Int = 0,
    /** The app whose account is being changed, while that sheet is open. */
    val editing: CaptureSourceRow? = null,
    val isLoading: Boolean = true,
) {
    /** An app needs an account to write to, so with none there is nothing to choose one for. */
    val canAddApp: Boolean get() = accounts.isNotEmpty()
}

/**
 * Settings › Capture (PRD §4.6, `DECISIONS.md` D29): the switch, the apps it reads and where each
 * one's movements go, and how many words each list holds.
 *
 * Whether the system has granted *Notification access* is not here: it can change behind the
 * app's back in system settings, so the screen asks the phone each time it comes back into view.
 */
@HiltViewModel
class CaptureViewModel
    @Inject
    constructor(
        private val settings: CaptureSettingsRepository,
        accounts: AccountRepository,
    ) : ViewModel() {
        private val editingPackage = MutableStateFlow<String?>(null)

        val uiState: StateFlow<CaptureUiState> =
            combine(settings.observe(), accounts.observeAccounts(), editingPackage) { setup, active, editing ->
                val names = active.associate { it.id to it.name }
                val rows = setup.sources.map { CaptureSourceRow(it, names[it.accountId]) }
                CaptureUiState(
                    enabled = setup.enabled,
                    sources = rows,
                    accounts = active,
                    expenseWordCount = setup.expenseWords.size,
                    incomeWordCount = setup.incomeWords.size,
                    editing = rows.firstOrNull { it.source.packageName == editing },
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = CaptureUiState(),
            )

        fun setEnabled(enabled: Boolean) {
            viewModelScope.launch { settings.setEnabled(enabled) }
        }

        fun edit(packageName: String) {
            editingPackage.value = packageName
        }

        fun closeEditor() {
            editingPackage.value = null
        }

        /** Sends what the app being edited announces to [accountId] from now on. */
        fun moveTo(accountId: Long) {
            val source = uiState.value.editing?.source ?: return
            editingPackage.value = null
            viewModelScope.launch { settings.putSource(source.copy(accountId = accountId)) }
        }

        /** Stops reading the app being edited. What it already wrote stays in the list. */
        fun remove() {
            val source = uiState.value.editing?.source ?: return
            editingPackage.value = null
            viewModelScope.launch { settings.removeSource(source.packageName) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
