package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R

/**
 * What is being recorded: money going out, money coming in, or money changing pockets.
 *
 * A transfer sits beside the other two rather than behind a menu because it is the third
 * answer to the same question — *what happened to this money* — and the owner does not
 * always know which of the three they meant until they are looking at the form.
 */
enum class EntryKind {
    EXPENSE,
    INCOME,
    TRANSFER,
}

/**
 * The control at the head of both entry forms.
 *
 * It lives here, shared, rather than once per screen: the two forms are different screens —
 * a transfer has two accounts and no category, so folding them into one would be a form made
 * of conditionals — but they have to look and read like one thing with three settings.
 * Choosing a segment the current screen cannot record is what navigates to the one that can.
 */
@Composable
fun EntryKindControl(
    selected: EntryKind,
    onSelect: (EntryKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolved here: a lambda handed to a component is not a composable scope.
    val expense = stringResource(R.string.kind_expense)
    val income = stringResource(R.string.kind_income)
    val transfer = stringResource(R.string.kind_transfer)
    CentsSegmentedControl(
        options = listOf(EntryKind.EXPENSE, EntryKind.INCOME, EntryKind.TRANSFER),
        selected = selected,
        onSelect = onSelect,
        label = { kind ->
            when (kind) {
                EntryKind.EXPENSE -> expense
                EntryKind.INCOME -> income
                EntryKind.TRANSFER -> transfer
            }
        },
        modifier = modifier,
    )
}
