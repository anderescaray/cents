package io.github.anderescaray.cents.ui.feature.accounts

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsActionSheet
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsSheetAction

/**
 * Deleting an account, asked one step at a time.
 *
 * An empty account is a yes/no. One with movements in it is a real question, so it gets a
 * sheet with the two things "delete" could mean — and the answer that destroys history is
 * asked for twice, on its own, naming what goes (`DECISIONS.md` D7).
 */
@Composable
internal fun RemovalDialogs(
    state: AccountEditUiState,
    viewModel: AccountEditViewModel,
) {
    when (state.removalStep) {
        RemovalStep.NONE -> Unit
        RemovalStep.CONFIRM_EMPTY -> ConfirmEmptyAlert(state, viewModel)
        RemovalStep.CHOOSE -> RemovalChoiceSheet(state, viewModel)
        RemovalStep.PICK_TARGET -> ReassignTargetSheet(state, viewModel)
        RemovalStep.CONFIRM_DESTRUCTIVE -> ConfirmDestructiveAlert(state, viewModel)
    }
}

/** Nothing lives in it, so there is only one thing deleting it could mean. */
@Composable
private fun ConfirmEmptyAlert(
    state: AccountEditUiState,
    viewModel: AccountEditViewModel,
) {
    CentsAlert(
        title = stringResource(R.string.account_delete_title, state.name),
        message = stringResource(R.string.account_delete_message),
        confirmLabel = stringResource(R.string.action_delete),
        onConfirm = viewModel::confirmEmptyRemoval,
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = viewModel::cancelRemoval,
        destructive = true,
    )
}

/** The two honest meanings of "delete" for an account that holds movements. */
@Composable
private fun RemovalChoiceSheet(
    state: AccountEditUiState,
    viewModel: AccountEditViewModel,
) {
    CentsActionSheet(
        title = stringResource(R.string.account_delete_title, state.name),
        message =
            pluralStringResource(
                R.plurals.account_delete_choice_message,
                state.transactionCount,
                state.transactionCount,
            ),
        actions =
            listOf(
                CentsSheetAction(
                    label = stringResource(R.string.account_delete_reassign),
                    onClick = viewModel::chooseReassign,
                    enabled = state.canReassign,
                    // Disabled rather than hidden: an option that vanishes for reasons the
                    // owner cannot see reads as the app having forgotten it.
                    reason =
                        stringResource(R.string.account_delete_reassign_impossible)
                            .takeUnless { state.canReassign },
                ),
                CentsSheetAction(
                    label =
                        pluralStringResource(
                            R.plurals.account_delete_with_movements,
                            state.transactionCount,
                            state.transactionCount,
                        ),
                    onClick = viewModel::chooseDestructive,
                    destructive = true,
                ),
            ),
        cancelLabel = stringResource(R.string.action_cancel),
        onDismiss = viewModel::cancelRemoval,
    )
}

/** Where the movements go before this account does. */
@Composable
private fun ReassignTargetSheet(
    state: AccountEditUiState,
    viewModel: AccountEditViewModel,
) {
    CentsActionSheet(
        title = stringResource(R.string.account_reassign_title),
        message =
            pluralStringResource(
                R.plurals.account_reassign_message,
                state.transactionCount,
                state.transactionCount,
            ),
        actions =
            state.reassignTargets.map { target ->
                CentsSheetAction(label = target.name, onClick = { viewModel.reassignTo(target.id) })
            },
        cancelLabel = stringResource(R.string.action_cancel),
        onDismiss = viewModel::cancelRemoval,
    )
}

/** The one place in the app that destroys recorded history, asked alone and by name. */
@Composable
private fun ConfirmDestructiveAlert(
    state: AccountEditUiState,
    viewModel: AccountEditViewModel,
) {
    CentsAlert(
        title = stringResource(R.string.account_delete_everything_title, state.name),
        message =
            pluralStringResource(
                R.plurals.account_delete_everything_message,
                state.transactionCount,
                state.transactionCount,
            ),
        confirmLabel = stringResource(R.string.action_delete),
        onConfirm = viewModel::confirmDestructiveRemoval,
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = viewModel::cancelRemoval,
        destructive = true,
    )
}
