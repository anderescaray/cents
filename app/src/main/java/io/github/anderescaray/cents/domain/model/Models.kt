package io.github.anderescaray.cents.domain.model

import java.time.LocalDate

/** A container of money. Its balance is derived, never stored. */
data class Account(
    val id: Long,
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingBalance: Money,
    val colorArgb: Int,
    val archived: Boolean,
    val sortOrder: Int,
)

/** An account together with its current balance, which is what screens actually show. */
data class AccountWithBalance(
    val account: Account,
    val balance: Money,
)

/** A user-defined label. [parentId] gives one level of subcategories. */
data class Category(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
    val colorArgb: Int,
    val parentId: Long?,
    val archived: Boolean,
    val sortOrder: Int,
)

/** One income or expense. The amount is always positive; [kind] carries the direction. */
data class Transaction(
    val id: Long,
    val accountId: Long,
    val categoryId: Long?,
    val kind: TransactionKind,
    val amount: Money,
    val date: LocalDate,
    val note: String?,
    /**
     * The [Transfer] this is one leg of, and null for an ordinary movement.
     *
     * A leg is a real row with a real effect on a real balance — it is only the income and
     * expense *reports* it stays out of, because money moved between the owner's own accounts
     * was neither earned nor spent (PRD §3).
     */
    val transferId: Long? = null,
    /**
     * The trade this is the cash of, and null for an ordinary movement.
     *
     * Money that went into a holding or came back out of one. Like a transfer leg it moves a
     * balance and no report: buying 400 € of shares is the same money in a different shape.
     */
    val tradeId: Long? = null,
) {
    /** The amount as it affects a balance: negative for an expense. */
    val signedAmount: Money
        get() = if (kind == TransactionKind.EXPENSE) -amount else amount

    /** Half of a transfer rather than a movement of its own. */
    val isTransferLeg: Boolean get() = transferId != null

    /** The cash behind a purchase or a sale rather than a movement of its own. */
    val isTradeCash: Boolean get() = tradeId != null

    /** Neither earned nor spent: money that only changed shape or changed pockets. */
    val movesNoIncomeOrExpense: Boolean get() = isTransferLeg || isTradeCash
}

/** A transaction joined with the names it needs to be shown in a list. */
data class TransactionDetails(
    val transaction: Transaction,
    val accountName: String,
    val categoryName: String?,
    val categoryColorArgb: Int?,
    /** Where the money went, or came from, when this is a transfer leg. Null otherwise. */
    val counterpartAccountName: String? = null,
    /** What was bought or sold, when this is the cash of a trade. Null otherwise. */
    val tradeSymbol: String? = null,
    /** Which holding that trade belongs to, so the row can open it. */
    val tradeHoldingId: Long? = null,
)

/**
 * Money moved between two of the owner's own accounts.
 *
 * Stored as two ordinary rows — an expense in [fromAccountId] and an income in [toAccountId]
 * — sharing one `transferId`, which is what every report filters on. There is no transfers
 * table: a transfer is a *relationship between two movements*, and giving it a table of its
 * own would mean the same amount written in two places, free to disagree.
 *
 * [fee] is what the move cost — a wire charge, a broker's commission — and it comes out of
 * [fromAccountId] on top of [amount]: 500 € sent with a 3 € fee leaves 503 € and lands 500 €.
 * It is not stored separately either; it is the difference between what left and what
 * arrived, which is the one definition of it that cannot drift from the balances. The PRD
 * keeps it inside the transfer rather than counting it as spending (§4.2), so it is excluded
 * from the reports along with the rest.
 */
data class Transfer(
    /** The pair id: [Transfer.NEW] until it has been written. */
    val id: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    /** What arrives. Always positive. */
    val amount: Money,
    /** What the move cost, on top of [amount]. Zero when it was free, which is the usual case. */
    val fee: Money,
    val date: LocalDate,
    val note: String?,
) {
    /** What actually leaves the source account. */
    val amountLeaving: Money get() = amount + fee

    companion object {
        const val NEW = 0L
    }
}

/** Money in versus money out over a period, in one currency. */
data class PeriodSummary(
    val income: Money,
    val expenses: Money,
) {
    val net: Money get() = income - expenses
}
