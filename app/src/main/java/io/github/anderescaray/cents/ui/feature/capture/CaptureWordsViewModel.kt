package io.github.anderescaray.cents.ui.feature.capture

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import io.github.anderescaray.cents.ui.navigation.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

@Immutable
data class CaptureWordsUiState(
    val kind: TransactionKind,
    val words: List<String> = emptyList(),
    val draft: String = "",
    /** The word waiting on "remove it?", while that alert is open. */
    val removing: String? = null,
    val confirmingReset: Boolean = false,
    val isLoading: Boolean = true,
) {
    /** Already in the list, read the way the parser reads it: case and accents aside. */
    val draftIsDuplicate: Boolean
        get() = draft.isNotBlank() && words.any { sameWord(it, draft) }

    val canAdd: Boolean get() = draft.isNotBlank() && !draftIsDuplicate

    /** The list is D29's own, so putting the defaults back would change nothing. */
    val isDefault: Boolean
        get() = words == defaultsFor(kind)
}

/**
 * One kind's words (`DECISIONS.md` D29): the ones that make a notification an expense, or an
 * income. Adding, removing, and putting D29's list back.
 */
@HiltViewModel
class CaptureWordsViewModel
    @Inject
    constructor(
        private val settings: CaptureSettingsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val kind = savedStateHandle.toRoute<Destination.CaptureWords>().kind
        private val form = MutableStateFlow(Form())

        val uiState: StateFlow<CaptureWordsUiState> =
            combine(settings.observe(), form) { setup, current ->
                CaptureWordsUiState(
                    kind = kind,
                    words = if (kind == TransactionKind.EXPENSE) setup.expenseWords else setup.incomeWords,
                    draft = current.draft,
                    removing = current.removing,
                    confirmingReset = current.confirmingReset,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = CaptureWordsUiState(kind = kind),
            )

        fun setDraft(draft: String) {
            form.update { it.copy(draft = draft) }
        }

        fun add() {
            val state = uiState.value
            if (state.isLoading || !state.canAdd) return
            val word = state.draft.trim()
            form.update { it.copy(draft = "") }
            viewModelScope.launch { settings.setWords(kind, state.words + word) }
        }

        fun requestRemove(word: String) {
            form.update { it.copy(removing = word) }
        }

        fun cancelRemove() {
            form.update { it.copy(removing = null) }
        }

        fun confirmRemove() {
            val word = form.value.removing ?: return
            val words = uiState.value.words
            form.update { it.copy(removing = null) }
            viewModelScope.launch { settings.setWords(kind, words - word) }
        }

        fun requestReset() {
            form.update { it.copy(confirmingReset = true) }
        }

        fun cancelReset() {
            form.update { it.copy(confirmingReset = false) }
        }

        fun confirmReset() {
            form.update { it.copy(confirmingReset = false) }
            viewModelScope.launch { settings.resetWords(kind) }
        }

        private data class Form(
            val draft: String = "",
            val removing: String? = null,
            val confirmingReset: Boolean = false,
        )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private fun defaultsFor(kind: TransactionKind): List<String> =
    when (kind) {
        TransactionKind.EXPENSE -> CaptureSettings.DEFAULT_EXPENSE_WORDS
        TransactionKind.INCOME -> CaptureSettings.DEFAULT_INCOME_WORDS
    }

private val MARKS = Regex("\\p{Mn}+")
private val SPACES = Regex("\\s+")

/** The comparison `CaptureParser` makes: *Nómina* and *nomina* are one word to it, so one here. */
private fun sameWord(
    a: String,
    b: String,
): Boolean = foldWord(a) == foldWord(b)

private fun foldWord(word: String): String =
    Normalizer
        .normalize(word.trim(), Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(SPACES, " ")
