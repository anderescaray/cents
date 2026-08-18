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
) {
    /** The amount as it affects a balance: negative for an expense. */
    val signedAmount: Money
        get() = if (kind == TransactionKind.EXPENSE) -amount else amount
}

/** A transaction joined with the names it needs to be shown in a list. */
data class TransactionDetails(
    val transaction: Transaction,
    val accountName: String,
    val categoryName: String?,
    val categoryColorArgb: Int?,
)

/** Money in versus money out over a period, in one currency. */
data class PeriodSummary(
    val income: Money,
    val expenses: Money,
) {
    val net: Money get() = income - expenses
}
