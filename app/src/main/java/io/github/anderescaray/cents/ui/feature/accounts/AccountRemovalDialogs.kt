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
 *
 * It renders an [AccountRemoval] and nothing else, so the editor and a swipe on the list
 * put the same question on screen.
 */
@Composable
internal fun RemovalDialogs(
    state: AccountRemovalState,
    removal: AccountRemoval,
) {
    when (state.step) {
        RemovalStep.NONE -> Unit
        RemovalStep.CONFIRM_EMPTY -> ConfirmEmptyAlert(state, removal)
        RemovalStep.CHOOSE -> RemovalChoiceSheet(state, removal)
        RemovalStep.PICK_TARGET -> ReassignTargetSheet(state, removal)
        RemovalStep.CONFIRM_DESTRUCTIVE -> ConfirmDestructiveAlert(state, removal)
        RemovalStep.BLOCKED_BY_HOLDINGS -> HeldInvestmentsAlert(state, removal::cancel)
        RemovalStep.FAILED -> DeletionFailedAlert(removal::cancel)
    }
}

/** Nothing lives in it, so there is only one thing deleting it could mean. */
@Composable
private fun ConfirmEmptyAlert(
    state: AccountRemovalState,
    removal: AccountRemoval,
) {
    CentsAlert(
        title = stringResource(R.string.account_delete_title, state.name),
        message = stringResource(R.string.account_delete_message),
        confirmLabel = stringResource(R.string.action_delete),
        onConfirm = removal::confirmEmpty,
        onDismiss = removal::cancel,
        dismissLabel = stringResource(R.string.action_cancel),
        destructive = true,
    )
}

/** The two honest meanings of "delete" for an account that holds movements. */
@Composable
private fun RemovalChoiceSheet(
    state: AccountRemovalState,
    removal: AccountRemoval,
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
                    onClick = removal::chooseReassign,
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
                    onClick = removal::chooseDestructive,
                    destructive = true,
                ),
            ),
        cancelLabel = stringResource(R.string.action_cancel),
        onDismiss = removal::cancel,
    )
}

/** Where the movements go before this account does. */
@Composable
private fun ReassignTargetSheet(
    state: AccountRemovalState,
    removal: AccountRemoval,
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
                CentsSheetAction(label = target.name, onClick = { removal.reassignTo(target.id) })
            },
        cancelLabel = stringResource(R.string.action_cancel),
        onDismiss = removal::cancel,
    )
}

/** The one place in the app that destroys recorded history, asked alone and by name. */
@Composable
private fun ConfirmDestructiveAlert(
    state: AccountRemovalState,
    removal: AccountRemoval,
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
        onConfirm = removal::confirmDestructive,
        onDismiss = removal::cancel,
        dismissLabel = stringResource(R.string.action_cancel),
        destructive = true,
    )
}

/**
 * Nothing to decide, only something to acknowledge — so one button, and it says OK.
 *
 * Shared with the category list, because "the database would not let that go" is the same
 * sentence whichever row it was.
 */
@Composable
internal fun HeldInvestmentsAlert(
    state: AccountRemovalState,
    onDismiss: () -> Unit,
) {
    CentsAlert(
        title = stringResource(R.string.account_delete_title, state.name),
        message =
            pluralStringResource(
                R.plurals.account_delete_blocked_by_holdings,
                state.holdingCount,
                state.holdingCount,
            ),
        confirmLabel = stringResource(R.string.action_ok),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    )
}

/** The database refused a deletion this flow believed it could carry out. */
@Composable
internal fun DeletionFailedAlert(onDismiss: () -> Unit) {
    CentsAlert(
        title = stringResource(R.string.delete_failed_title),
        message = stringResource(R.string.delete_failed_message),
        confirmLabel = stringResource(R.string.action_ok),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    )
}
