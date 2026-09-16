package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.theme.CentsTheme

/*
 * How a movement reads as a row, wherever it is listed.
 *
 * Two screens list movements — the Transactions tab and one account's detail — and both had
 * their own copy of "title is the category, green if it is income". That was harmless while
 * every row was an income or an expense. A transfer leg is neither, and a copy that has not
 * been told would show it as *Uncategorized* money earned: exactly the misreading transfers
 * exist to prevent. So the two rules live here, once.
 */

/**
 * What the row is called.
 *
 * A transfer leg has no category, so the heading a movement uses would be *Uncategorized* —
 * which is not what it is, and files it beside the movements that genuinely want classifying.
 * What is worth knowing about a leg is the other end of it, so the row names that: *Transfer
 * to Savings*, read in the account the money left.
 */
@Composable
fun TransactionDetails.movementTitle(): String =
    when {
        !transaction.isTransferLeg -> {
            categoryName ?: stringResource(R.string.transactions_uncategorized)
        }

        counterpartAccountName == null -> {
            stringResource(R.string.transfer_row)
        }

        transaction.kind == TransactionKind.EXPENSE -> {
            stringResource(R.string.transfer_row_to, counterpartAccountName)
        }

        else -> {
            stringResource(R.string.transfer_row_from, counterpartAccountName)
        }
    }

/**
 * What colour the amount is written in.
 *
 * Money arriving on the far side of a transfer is shown in the ordinary colour rather than the
 * green income is written in: nothing was earned, and a list where half the transfers read as
 * income is the misreading this whole feature exists to prevent.
 */
@Composable
fun TransactionDetails.movementValueColor(): Color =
    when {
        transaction.isTransferLeg -> CentsTheme.colors.secondaryLabel
        transaction.kind == TransactionKind.INCOME -> CentsTheme.colors.positive
        else -> CentsTheme.colors.label
    }
