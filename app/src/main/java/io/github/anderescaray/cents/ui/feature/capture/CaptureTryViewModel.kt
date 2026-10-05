package io.github.anderescaray.cents.ui.feature.capture

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.AccountRepository
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import io.github.anderescaray.cents.domain.usecase.CaptureParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@Immutable
data class CaptureTryUiState(
    val text: String = "",
    /** The apps capture reads; the text is read with the chosen one's own words. */
    val apps: List<CaptureSource> = emptyList(),
    /** The app whose words read the text, and null for the starting words when none is chosen. */
    val app: CaptureSource? = null,
    /** Its account's name and currency, which a real capture checks the amount against. */
    val accountName: String? = null,
    val accountCurrency: String? = null,
    /** Null while there is nothing to read. */
    val reading: CaptureParser.Reading? = null,
    val isLoading: Boolean = true,
) {
    /** An amount in another currency than the account's: capture would ignore it (D2). */
    val otherCurrency: Boolean
        get() {
            val movement = reading as? CaptureParser.Reading.Movement ?: return false
            return accountCurrency != null && movement.amount.currency != accountCurrency
        }
}

/**
 * *Try a notification*: a pasted text read exactly the way capture reads one, saying what would be
 * recorded or why nothing would. Nothing is saved, and no notification is involved.
 *
 * The reading is `CaptureParser.read`, which is what `RoomCaptureRecorder` calls, so the answer
 * here is the answer there.
 */
@HiltViewModel
class CaptureTryViewModel
    @Inject
    constructor(
        settings: CaptureSettingsRepository,
        accounts: AccountRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow(Form())

        val uiState: StateFlow<CaptureTryUiState> =
            combine(settings.observe(), accounts.observeAccounts(includeArchived = true), form) { setup, all, form ->
                val app = form.packageName?.let(setup::sourceFor) ?: setup.sources.firstOrNull()
                val account = app?.let { source -> all.firstOrNull { it.id == source.accountId } }
                CaptureTryUiState(
                    text = form.text,
                    apps = setup.sources,
                    app = app,
                    accountName = account?.name,
                    accountCurrency = account?.currency,
                    reading =
                        form.text.takeIf(String::isNotBlank)?.let { text ->
                            CaptureParser.read(
                                text = text,
                                expenseWords = setup.wordsFor(app?.packageName, TransactionKind.EXPENSE),
                                incomeWords = setup.wordsFor(app?.packageName, TransactionKind.INCOME),
                                ignoreWords = setup.ignoreWords,
                            )
                        },
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = CaptureTryUiState(),
            )

        fun setText(text: String) {
            form.update { it.copy(text = text) }
        }

        fun choose(packageName: String) {
            form.update { it.copy(packageName = packageName) }
        }

        private data class Form(
            val text: String = "",
            val packageName: String? = null,
        )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
