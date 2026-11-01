package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.theme.CentsTheme

/*
 * How a movement reads as a row and where tapping it goes, wherever it is listed.
 *
 * Two screens list movements — the Transactions tab and one account's detail — and both had
 * their own copy of "title is the category, green if it is income". That was harmless while
 * every row was an income or an expense. A transfer leg is neither, and neither is the cash a
 * purchase took out; a copy that has not been told would show either as *Uncategorized* money
 * earned, which is exactly the misreading both features exist to prevent. So the rules live
 * here, once.
 */

/**
 * What the row is called.
 *
 * Neither a transfer leg nor a trade's cash has a category, so the heading a movement uses would
 * be *Uncategorized* — which is not what either is, and files them beside the movements that
 * genuinely want classifying. What is worth knowing is the other end: for a leg, the account the
 * money went to or came from (*Transfer to Savings*, read in the account it left); for a
 * purchase, what was bought (*Bought NVD.DE*).
 */
@Composable
fun TransactionDetails.movementTitle(): String =
    when {
        transaction.isTradeCash -> {
            tradeTitle()
        }

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
 * What a trade's row is called. The only one the lists show is a sale's result (D40), named by
 * the asset it was made on; the direction is in the amount.
 */
@Composable
private fun TransactionDetails.tradeTitle(): String {
    val name = tradeAssetName ?: return stringResource(R.string.trade_row)
    return stringResource(R.string.trade_row_result, name)
}

/**
 * What colour the amount is written in.
 *
 * Money arriving on the far side of a transfer, or coming back from a sale, is shown in the
 * ordinary colour rather than the green income is written in: nothing was earned, and a list
 * where half the transfers read as income is the misreading this whole feature exists to
 * prevent.
 */
@Composable
fun TransactionDetails.movementValueColor(): Color =
    when {
        // What a sale made reads as a gain, though no report counts it (D40).
        tradeResult && transaction.kind == TransactionKind.INCOME -> CentsTheme.colors.positiveText

        tradeResult -> CentsTheme.colors.label

        transaction.movesNoIncomeOrExpense -> CentsTheme.colors.secondaryLabel

        transaction.kind == TransactionKind.INCOME -> CentsTheme.colors.positiveText

        else -> CentsTheme.colors.label
    }

/**
 * Where tapping the row goes.
 *
 * A row that is half of something bigger opens the bigger thing: a transfer leg opens the
 * transfer, because the other half has to change with it, and a trade's cash opens the trade,
 * because the money is a consequence of the units and editing it alone would make the two
 * disagree. Everything else opens itself.
 *
 * It lives beside the two rules about how a row *reads* so that a screen cannot get one right
 * and the other wrong.
 */
fun TransactionDetails.open(
    onOpenTransaction: (Long) -> Unit,
    onOpenTransfer: (Long) -> Unit,
    onOpenTrade: (Long, Long) -> Unit,
) {
    val transferId = transaction.transferId
    val tradeId = transaction.tradeId
    val holdingId = tradeHoldingId
    when {
        tradeId != null && holdingId != null -> onOpenTrade(holdingId, tradeId)
        transferId != null -> onOpenTransfer(transferId)
        else -> onOpenTransaction(transaction.id)
    }
}

/**
 * The mark at the start of a movement's row: its category's letter in its colour, or — for the
 * rows that have no category on purpose — what they are instead: two arrows for a transfer leg,
 * a rising line for the cash of a trade. Uncategorized is a grey question mark, which is what it is.
 */
@Composable
fun TransactionDetails.MovementBadge(modifier: Modifier = Modifier) {
    when {
        transaction.isTransferLeg -> {
            IconBadge(iconRes = R.drawable.ic_transfer, color = CentsTheme.colors.accent, modifier = modifier)
        }

        transaction.isTradeCash -> {
            IconBadge(iconRes = R.drawable.ic_trend_up, color = CentsTheme.colors.positive, modifier = modifier)
        }

        else -> {
            CategoryBadge(
                name = categoryName,
                colorArgb = categoryColorArgb,
                modifier = modifier,
                emoji = categoryEmoji,
            )
        }
    }
}
