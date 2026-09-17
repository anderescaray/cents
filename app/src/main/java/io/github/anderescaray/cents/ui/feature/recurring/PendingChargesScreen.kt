package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsSwipeableRow
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.LocalDate

/**
 * The bills the app refused to guess.
 *
 * Everything waiting for a figure, in one sitting — the electricity, the water and the gas arrive
 * in the same week, and three separate forms would be three separate trips. The shape is the
 * price screen's, for the same reason: a column of numbers being copied off something else.
 *
 * A row left empty is left alone. Swiping one sideways says *there was no bill that month*, which
 * is a different answer from zero and is why it is a separate gesture rather than a figure.
 */
@Composable
fun PendingChargesScreen(
    onDone: () -> Unit,
    viewModel: PendingChargesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                // Every row here is a text field with a keyboard under it.
                .imePadding(),
    ) {
        CentsModalBar(
            title = stringResource(R.string.pending_title),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
        )

        PendingList(state = state, viewModel = viewModel)
    }

    PendingDialogs(state = state, viewModel = viewModel)
}

@Composable
private fun PendingDialogs(
    state: PendingChargesUiState,
    viewModel: PendingChargesViewModel,
) {
    state.skipping?.let { row ->
        CentsAlert(
            title = stringResource(R.string.pending_skip_title, row.name),
            message = stringResource(R.string.pending_skip_message),
            confirmLabel = stringResource(R.string.pending_skip_action),
            onConfirm = viewModel::confirmSkip,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = viewModel::cancelSkip,
            destructive = true,
        )
    }

    if (state.writeFailed) {
        // Somewhere else answered it first, or the write was refused. Nothing is retried on its
        // own: the list has been read again, so what is on screen is what is really waiting.
        CentsAlert(
            title = stringResource(R.string.pending_failed_title),
            message = stringResource(R.string.pending_failed_message),
            confirmLabel = stringResource(R.string.action_ok),
            onConfirm = viewModel::dismissFailure,
            onDismiss = viewModel::dismissFailure,
        )
    }
}

@Composable
private fun PendingList(
    state: PendingChargesUiState,
    viewModel: PendingChargesViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
    ) {
        if (state.isEmpty) {
            item(key = "empty") {
                CentsListSection(footer = stringResource(R.string.pending_empty_footer)) {
                    CentsListRow(
                        title = stringResource(R.string.pending_empty_title),
                        showSeparator = false,
                    )
                }
            }
        }

        if (state.rows.isNotEmpty()) {
            item(key = "pending") {
                CentsListSection(
                    header = stringResource(R.string.pending_header),
                    footer =
                        state.answeredCount
                            .takeIf { it > 0 }
                            ?.let { pluralStringResource(R.plurals.pending_answered, it, it) }
                            ?: stringResource(R.string.pending_footer),
                ) {
                    state.rows.forEachIndexed { index, row ->
                        key(row.ruleId) {
                            CentsSwipeableRow(
                                onDelete = { viewModel.requestSkip(row) },
                                deleteLabel = stringResource(R.string.pending_skip),
                            ) { rowModifier ->
                                PendingField(
                                    modifier = rowModifier,
                                    row = row,
                                    today = state.today,
                                    showSeparator = index != state.rows.lastIndex,
                                    onTyped = { typed -> viewModel.onTyped(row.ruleId, typed) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One waiting bill: what it is for, when it fell due, and somewhere to type what it came to.
 *
 * **The placeholder is what the rule was set up expecting**, so the box is never empty of
 * meaning — it reads as *about sixty, tell me the real one* rather than as a blank the owner has
 * to remember the shape of. The subtitle says the date it is for, because a bill answered late
 * is still recorded on the day it fell due and that has to be visible before saving.
 */
@Composable
private fun PendingField(
    row: PendingRow,
    today: LocalDate,
    showSeparator: Boolean,
    onTyped: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val later = row.charge.laterCount
    CentsListRow(
        modifier = modifier,
        title = row.name,
        subtitle =
            buildString {
                append(row.dueDate.relativeLabel(today))
                if (later > 0) {
                    append(" · ")
                    append(pluralStringResource(R.plurals.pending_more_after, later, later))
                }
            },
        trailing = {
            CentsTextField(
                value = row.input,
                onValueChange = onTyped,
                placeholder = MoneyFormatter.format(row.charge.estimate),
                numeric = true,
                modifier = Modifier.testTag(pendingFieldTag(row.ruleId)),
            )
        },
        showSeparator = showSeparator,
    )
}

internal fun pendingFieldTag(ruleId: Long) = "pending-$ruleId"
