package io.github.anderescaray.cents.ui.feature.capture

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.takesIncomeAndExpenses
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import io.github.anderescaray.cents.domain.usecase.CaptureParser
import io.github.anderescaray.cents.ui.capture.InstalledApp
import io.github.anderescaray.cents.ui.capture.InstalledApps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@Immutable
data class CaptureAppsUiState(
    /** The apps not chosen yet, narrowed by [query]. */
    val apps: List<InstalledApp> = emptyList(),
    val query: String = "",
    val accounts: List<Account> = emptyList(),
    /** The app picked, while the sheet asks which account it writes to. */
    val choosing: InstalledApp? = null,
    val finished: Boolean = false,
    val isLoading: Boolean = true,
)

/**
 * Choosing an app for capture: the phone's apps, then the account that app's movements go to
 * (`DECISIONS.md` D29: one account per app).
 */
@HiltViewModel
class CaptureAppsViewModel
    @Inject
    constructor(
        private val settings: CaptureSettingsRepository,
        accounts: AccountRepository,
        installed: InstalledApps,
    ) : ViewModel() {
        private val form = MutableStateFlow(Form())

        /** Bumped each time the screen comes back, so the list is asked again. */
        private val asked = MutableStateFlow(0)

        /**
         * The phone's apps, each with its name folded once for searching. Asked of the package
         * manager when the screen opens and each time it comes back, not on every key: an app
         * notifying now is offered, so coming back while the bank's notification shows finds it.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val installedApps =
            asked
                .mapLatest { installed.choosable().map { Searchable(it, CaptureParser.fold(it.label)) } }
                .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

        val uiState: StateFlow<CaptureAppsUiState> =
            combine(
                installedApps,
                settings.observe(),
                accounts.observeAccounts(),
                form,
            ) { apps, setup, active, current ->
                val chosen = setup.sources.map { it.packageName }.toSet()
                val needle = CaptureParser.fold(current.query.trim())
                CaptureAppsUiState(
                    apps =
                        apps
                            .filter { it.app.packageName !in chosen && it.matches(needle) }
                            .map { it.app },
                    query = current.query,
                    // Not a broker (D32): capture writes ordinary incomes and expenses.
                    accounts = active.filter { it.takesIncomeAndExpenses },
                    choosing = current.choosing,
                    finished = current.finished,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = CaptureAppsUiState(),
            )

        /** The screen came back: the apps notifying now may have changed. */
        fun refresh() {
            asked.update { it + 1 }
        }

        fun setQuery(query: String) {
            form.update { it.copy(query = query) }
        }

        fun choose(app: InstalledApp) {
            form.update { it.copy(choosing = app) }
        }

        fun cancelChoosing() {
            form.update { it.copy(choosing = null) }
        }

        /** Ties the app being chosen to [accountId], and closes the picker once it is saved. */
        fun assign(accountId: Long) {
            val app = form.value.choosing ?: return
            form.update { it.copy(choosing = null) }
            viewModelScope.launch {
                settings.putSource(CaptureSource(app.packageName, app.label, accountId))
                form.update { it.copy(finished = true) }
            }
        }

        private data class Form(
            val query: String = "",
            val choosing: InstalledApp? = null,
            val finished: Boolean = false,
        )

        /** *Banco* finds *BANCO* and *Bancó*: an app's name is typed as it is remembered. */
        private class Searchable(
            val app: InstalledApp,
            val foldedLabel: String,
        ) {
            fun matches(needle: String): Boolean =
                needle.isEmpty() || needle in foldedLabel || needle in app.packageName.lowercase(Locale.ROOT)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
