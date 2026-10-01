package io.github.anderescaray.cents.ui.feature.capture

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens

/** One kind's words (`DECISIONS.md` D29), to add to, prune, or put back as they came. */
@Composable
fun CaptureWordsScreen(
    onBack: () -> Unit,
    viewModel: CaptureWordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CaptureWordsContent(
        state = state,
        actions =
            CaptureWordsActions(
                onBack = onBack,
                onDraftChange = viewModel::setDraft,
                onAdd = viewModel::add,
                onRequestRemove = viewModel::requestRemove,
                onConfirmRemove = viewModel::confirmRemove,
                onCancelRemove = viewModel::cancelRemove,
                onRequestReset = viewModel::requestReset,
                onConfirmReset = viewModel::confirmReset,
                onCancelReset = viewModel::cancelReset,
            ),
    )
}

/** What the screen can be asked to do, so [CaptureWordsContent] holds no view model. */
internal data class CaptureWordsActions(
    val onBack: () -> Unit = {},
    val onDraftChange: (String) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onRequestRemove: (String) -> Unit = {},
    val onConfirmRemove: () -> Unit = {},
    val onCancelRemove: () -> Unit = {},
    val onRequestReset: () -> Unit = {},
    val onConfirmReset: () -> Unit = {},
    val onCancelReset: () -> Unit = {},
)

/** The screen without its view model, so a test can draw it from a state. */
@Composable
internal fun CaptureWordsContent(
    state: CaptureWordsUiState,
    actions: CaptureWordsActions,
) {
    val expense = state.kind == TransactionKind.EXPENSE
    CentsLargeTitleScaffold(
        title = stringResource(if (expense) R.string.capture_expense_words else R.string.capture_income_words),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.capture_title), onClick = actions.onBack) },
    ) {
        if (state.isLoading) return@CentsLargeTitleScaffold
        addSection(state, actions)
        wordList(state, actions)
        if (!state.isDefault) {
            item(key = "reset") {
                CentsListSection {
                    CentsActionRow(
                        label = stringResource(R.string.capture_words_reset),
                        onClick = actions.onRequestReset,
                    )
                }
            }
        }
    }

    WordAlerts(state, actions)
}

private fun LazyListScope.addSection(
    state: CaptureWordsUiState,
    actions: CaptureWordsActions,
) {
    item(key = "add") {
        CentsListSection {
            CentsTextField(
                value = state.draft,
                onValueChange = actions.onDraftChange,
                placeholder = stringResource(R.string.capture_words_add_placeholder),
                showSeparator = true,
                modifier = Modifier.testTag(WORD_FIELD_TAG),
            )
            CentsListRow(
                title = stringResource(R.string.capture_words_add),
                subtitle = if (state.draftIsDuplicate) stringResource(R.string.capture_words_duplicate) else null,
                showSeparator = false,
                onClick = actions.onAdd.takeIf { state.canAdd },
            )
        }
    }
}

private fun LazyListScope.wordList(
    state: CaptureWordsUiState,
    actions: CaptureWordsActions,
) {
    item(key = "words") {
        CentsListSection(footer = stringResource(R.string.capture_words_list_footer)) {
            if (state.words.isEmpty()) {
                CentsListRow(
                    title =
                        stringResource(
                            if (state.kind == TransactionKind.EXPENSE) {
                                R.string.capture_words_empty_expense
                            } else {
                                R.string.capture_words_empty_income
                            },
                        ),
                    showSeparator = false,
                )
            }
            state.words.forEachIndexed { index, word ->
                CentsListRow(
                    title = word,
                    showSeparator = index != state.words.lastIndex,
                    onClick = { actions.onRequestRemove(word) },
                    modifier = Modifier.testTag(WORD_ROW_TAG),
                )
            }
        }
    }
}

@Composable
private fun WordAlerts(
    state: CaptureWordsUiState,
    actions: CaptureWordsActions,
) {
    state.removing?.let { word ->
        CentsAlert(
            title = stringResource(R.string.capture_words_remove_title, word),
            message =
                stringResource(
                    if (state.kind == TransactionKind.EXPENSE) {
                        R.string.capture_words_remove_expense
                    } else {
                        R.string.capture_words_remove_income
                    },
                ),
            confirmLabel = stringResource(R.string.capture_words_remove_confirm),
            onConfirm = actions.onConfirmRemove,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = actions.onCancelRemove,
            destructive = true,
        )
    }
    if (state.confirmingReset) {
        CentsAlert(
            title = stringResource(R.string.capture_words_reset_title),
            message = stringResource(R.string.capture_words_reset_message),
            confirmLabel = stringResource(R.string.capture_words_reset_confirm),
            onConfirm = actions.onConfirmReset,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = actions.onCancelReset,
        )
    }
}

internal const val WORD_FIELD_TAG = "captureWordField"
internal const val WORD_ROW_TAG = "captureWord"
