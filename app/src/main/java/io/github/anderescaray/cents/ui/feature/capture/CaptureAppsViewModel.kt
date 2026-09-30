package io.github.anderescaray.cents.ui.feature.capture

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import io.github.anderescaray.cents.ui.capture.InstalledApp
import io.github.anderescaray.cents.ui.capture.InstalledApps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
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

        val uiState: StateFlow<CaptureAppsUiState> =
            combine(
                flow { emit(installed.launchable()) },
                settings.observe(),
                accounts.observeAccounts(),
                form,
            ) { apps, setup, active, current ->
                val chosen = setup.sources.map { it.packageName }.toSet()
                val needle = fold(current.query.trim())
                CaptureAppsUiState(
                    apps =
                        apps.filter { app ->
                            app.packageName !in chosen &&
                                (needle.isEmpty() || needle in fold(app.label) || needle in app.packageName)
                        },
                    query = current.query,
                    accounts = active,
                    choosing = current.choosing,
                    finished = current.finished,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = CaptureAppsUiState(),
            )

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

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            private val MARKS = Regex("\\p{Mn}+")

            /** *Banco* finds *BANCO* and *Bancó*: an app's name is typed as it is remembered. */
            fun fold(text: String): String =
                Normalizer
                    .normalize(text, Normalizer.Form.NFD)
                    .replace(MARKS, "")
                    .lowercase(Locale.ROOT)
        }
    }
