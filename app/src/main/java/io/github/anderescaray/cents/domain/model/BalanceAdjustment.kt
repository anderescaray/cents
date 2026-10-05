package io.github.anderescaray.cents.domain.model

import java.time.LocalDate

/**
 * A correction to an account's balance from [date] on, made by telling the app what the account
 * really holds (`docs/next-improvements.md` §7).
 *
 * **Not an income or an expense.** It is what the movements missed, without inventing one to
 * explain it, so no report, budget, group or saved-this-month figure ever counts it. Only a
 * balance does: the account's, the net worth that adds the accounts up, and the balance line.
 */
data class BalanceAdjustment(
    val id: Long,
    val accountId: Long,
    /** Signed: positive raised the balance, negative lowered it. */
    val amount: Money,
    /** The first day the balance includes it. */
    val date: LocalDate,
)

/**
 * What setting an account's balance does to the balances it had before. The owner chooses it
 * every time the balance is set; neither is a default the app falls back on.
 */
enum class BalanceChange {
    /**
     * A correction dated the day it is made. Every balance before that day stays as it was, and
     * the balance line steps on that day.
     */
    CORRECT_FROM_TODAY,

    /**
     * The opening balance moves by the difference, so the account reaches the new balance as if
     * it had always held that much more or less. Every past balance moves with it, which is why
     * the screen says so before it is chosen.
     */
    REBASE_OPENING,
}
