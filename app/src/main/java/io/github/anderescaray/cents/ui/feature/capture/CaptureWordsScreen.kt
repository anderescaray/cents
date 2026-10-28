package io.github.anderescaray.cents.ui.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CaptureWordList
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

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
    CentsLargeTitleScaffold(
        title =
            stringResource(
                when (state.list) {
                    CaptureWordList.EXPENSE -> R.string.capture_expense_words
                    CaptureWordList.INCOME -> R.string.capture_income_words
                    CaptureWordList.IGNORE -> R.string.capture_ignore_words
                },
            ),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.capture_title), onClick = actions.onBack) },
    ) {
        if (state.isLoading) return@CentsLargeTitleScaffold
        // Which app these words belong to: each has its own, so the screen says whose these are.
        state.appLabel?.let { label ->
            item(key = "app") {
                Text(
                    text = stringResource(R.string.capture_words_for, label),
                    style = CentsTheme.typography.subhead,
                    color = CentsTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
                )
            }
        }
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
    // The field in its card and, under it, a filled button: an action, not one more word in a list.
    item(key = "add") {
        Column(verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingM)) {
            CentsListSection(
                footer = if (state.draftIsDuplicate) stringResource(R.string.capture_words_duplicate) else null,
            ) {
                CentsTextField(
                    value = state.draft,
                    onValueChange = actions.onDraftChange,
                    placeholder = stringResource(R.string.capture_words_add_placeholder),
                    modifier = Modifier.testTag(WORD_FIELD_TAG),
                )
            }
            CentsButton(
                text = stringResource(R.string.capture_words_add),
                onClick = actions.onAdd,
                enabled = state.canAdd,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CentsDimens.screenMargin)
                        .testTag(WORD_ADD_TAG),
            )
        }
    }
}

private fun LazyListScope.wordList(
    state: CaptureWordsUiState,
    actions: CaptureWordsActions,
) {
    item(key = "words") {
        val footer =
            if (state.list == CaptureWordList.IGNORE) {
                stringResource(R.string.capture_ignore_words_footer)
            } else {
                stringResource(R.string.capture_words_list_footer)
            }
        CentsListSection(footer = footer) {
            if (state.words.isEmpty()) {
                CentsListRow(
                    title =
                        stringResource(
                            when (state.list) {
                                CaptureWordList.EXPENSE -> R.string.capture_words_empty_expense
                                CaptureWordList.INCOME -> R.string.capture_words_empty_income
                                CaptureWordList.IGNORE -> R.string.capture_words_empty_ignore
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
                    when (state.list) {
                        CaptureWordList.EXPENSE -> R.string.capture_words_remove_expense
                        CaptureWordList.INCOME -> R.string.capture_words_remove_income
                        CaptureWordList.IGNORE -> R.string.capture_words_remove_ignore
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
internal const val WORD_ADD_TAG = "captureWordAdd"
