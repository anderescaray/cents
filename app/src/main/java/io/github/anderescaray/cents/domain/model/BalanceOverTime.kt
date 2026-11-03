package io.github.anderescaray.cents.domain.model

import java.time.LocalDate

/** The money there was at the end of one day. */
data class BalancePoint(
    val date: LocalDate,
    val balance: Money,
)

/**
 * How much money there was, day by day, across a period.
 *
 * **Over every account it is cash and bank, not brokers**: the money Home and Wallet call *Cash
 * and bank*. A broker's cash is invested (`DECISIONS.md` D34), and buying shares with it moves
 * money into an asset rather than spending it, so counting brokers drew a fall in a month whose
 * spending was small. One broker chosen on its own is its cash.
 *
 * **The line starts from the balance carried into the period, not from zero.** [opening] is
 * those accounts' opening balances plus every movement recorded in them *before* [period], which
 * is the same arithmetic `AccountDao.observeBalances` uses — so the last point of a line ending
 * today is the *Cash and bank* figure, and can be checked by hand in ten seconds. A
 * zero-based line would answer a question the income-versus-expenses columns beside it
 * already answer, and would answer it in a way nothing on the phone could falsify.
 *
 * Two consequences of that choice are deliberate and worth knowing:
 *
 * - **The y-axis must not be dragged to zero.** A balance of 10.000 € moving by 200 € over a
 *   month draws a flat line if the axis starts at zero, so the chart ranges itself to the
 *   balances it actually holds. The slope is the answer; the level is the context.
 * - **Every account counts, archived ones included**, because the two charts beside this one
 *   count their movements too — an archived account's spending is in the donut. The Accounts
 *   tab totals only *active* accounts, so an archived account holding money is the one case
 *   where this line and that total legitimately differ.
 *
 * [activeDays] is how many days of the period had any movement at all. Zero is the empty
 * state: the line would be flat and true and say nothing, so the section says it in words
 * instead — the same rule the other two charts follow.
 */
data class BalanceOverTime(
    val period: ReportPeriod,
    val opening: Money,
    val points: List<BalancePoint>,
    val activeDays: Int,
    /** True when the line is one chosen account; false for cash and bank together. */
    val oneAccount: Boolean = false,
) {
    val isEmpty: Boolean get() = activeDays == 0

    /** What was there on the last day of the period. */
    val closing: Money get() = points.lastOrNull()?.balance ?: opening

    /** What the period did to the balance: the slope, as one number. */
    val change: Money get() = closing - opening
}
