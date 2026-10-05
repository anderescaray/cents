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
import io.github.anderescaray.cents.ui.capture.CaptureText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** What the owner has typed and chosen: kept apart so the fields never wait on the reading. */
@Immutable
data class CaptureTryInput(
    val title: String = "",
    val text: String = "",
    val packageName: String? = null,
)

/** Why capture would write nothing, whatever the text says: what the recorder checks first. */
enum class CaptureTryBlocker {
    /** Capture is switched off. */
    OFF,

    /** No app is chosen, so no notification is read at all. */
    NO_APP,

    /** The chosen app's account is archived or gone. */
    NO_ACCOUNT,
}

@Immutable
data class CaptureTryUiState(
    /** The apps capture reads; the text is read with the chosen one's own words. */
    val apps: List<CaptureSource> = emptyList(),
    /** The app whose words read the text, and null for the starting words when none is chosen. */
    val app: CaptureSource? = null,
    /** Its account's name and currency, which a real capture checks the amount against. */
    val accountName: String? = null,
    val accountCurrency: String? = null,
    /** What would stop capture before it read anything; null when nothing would. */
    val blocker: CaptureTryBlocker? = null,
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

    /** What capture would actually do: write a movement, and nothing stands in its way. */
    val records: Boolean
        get() = reading is CaptureParser.Reading.Movement && !otherCurrency && blocker == null
}

/**
 * *Try a notification*: a pasted title and text read exactly the way capture reads a notification,
 * saying what would be recorded or why nothing would. Nothing is saved.
 *
 * The two are joined by `CaptureText.of` and read by `CaptureParser.read`, which is what the
 * listener and `RoomCaptureRecorder` do, and the checks that come before any reading (the switch,
 * an app, its account) are the recorder's too, so the answer here is the answer there.
 */
@HiltViewModel
class CaptureTryViewModel
    @Inject
    constructor(
        settings: CaptureSettingsRepository,
        accounts: AccountRepository,
    ) : ViewModel() {
        private val form = MutableStateFlow(CaptureTryInput())

        /** The fields, straight back: a text field fed through the reading would lag its own keys. */
        val input: StateFlow<CaptureTryInput> = form.asStateFlow()

        val uiState: StateFlow<CaptureTryUiState> =
            combine(
                settings.observe(),
                accounts.observeAccounts(includeArchived = false),
                form,
            ) { setup, active, input ->
                val app = input.packageName?.let(setup::sourceFor) ?: setup.sources.firstOrNull()
                // Active accounts only: the recorder skips an app whose account is archived.
                val account = app?.let { source -> active.firstOrNull { it.id == source.accountId } }
                val text = CaptureText.of(title = input.title, text = input.text, bigText = null)
                CaptureTryUiState(
                    apps = setup.sources,
                    app = app,
                    accountName = account?.name,
                    accountCurrency = account?.currency,
                    blocker =
                        when {
                            !setup.enabled -> CaptureTryBlocker.OFF
                            app == null -> CaptureTryBlocker.NO_APP
                            account == null -> CaptureTryBlocker.NO_ACCOUNT
                            else -> null
                        },
                    reading =
                        text?.let {
                            CaptureParser.read(
                                text = it,
                                expenseWords = setup.wordsFor(app?.packageName, TransactionKind.EXPENSE),
                                incomeWords = setup.wordsFor(app?.packageName, TransactionKind.INCOME),
                                ignoreWords = setup.ignoreWords,
                            )
                        },
                    isLoading = false,
                )
            }.flowOn(Dispatchers.Default) // Regexes over every keystroke: not on the main thread (rule 4).
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = CaptureTryUiState(),
                )

        fun setTitle(title: String) {
            form.update { it.copy(title = title) }
        }

        fun setText(text: String) {
            form.update { it.copy(text = text) }
        }

        fun choose(packageName: String) {
            form.update { it.copy(packageName = packageName) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
