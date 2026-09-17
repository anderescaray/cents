package io.github.anderescaray.cents.domain.model

/**
 * What the owner has: the money in their accounts, the money in their holdings, and the two
 * added together.
 *
 * **Three numbers rather than one**, because the owner asked for all three (`DECISIONS.md` D19)
 * and because each answers a different question: [inAccounts] is what could be spent today,
 * [invested] is what is tied up, and [total] is what it comes to. A single net-worth figure
 * hides which of the two moved when it changes.
 *
 * The three only add up because a purchase now takes its cost out of the account it is held in.
 * Before that, an investments account's balance and the portfolio described the same euros twice
 * and adding them would have counted the money twice.
 */
data class NetWorth(
    val currency: String,
    /**
     * What the accounts hold, which is **cash**: whatever has been invested has already left
     * them. Archived accounts are not in it — see [inArchivedAccounts].
     */
    val inAccounts: Money,
    /**
     * What the holdings are worth, and **null when none of them has a price**.
     *
     * Only the priced ones are counted, exactly as [PortfolioTotal] counts them one screen
     * down. A portfolio that shows 0,00 € for something nobody has priced is worse than one
     * that admits it does not know: the first is a number, and numbers get believed.
     */
    val invested: Money?,
    /** How many holdings were left out of [invested] for want of a price. */
    val unpricedCount: Int,
    /**
     * Money sitting in archived accounts, which [inAccounts] does not count.
     *
     * Usually zero, because archiving an account usually follows emptying it. When it is not,
     * the screen says so rather than quietly leaving the money out of a figure called *net
     * worth* — the alternative, counting it, would make this disagree with the total the
     * accounts list has always shown under the active ones.
     */
    val inArchivedAccounts: Money,
) {
    /** Cash plus holdings, and null while nothing is priced — there is nothing to add yet. */
    val total: Money? get() = invested?.let { inAccounts + it }

    /** Whether anything was left out for want of a price. */
    val hasUnpriced: Boolean get() = unpricedCount > 0

    /** Whether there is money the figure above does not count. */
    val hasArchivedMoney: Boolean get() = inArchivedAccounts.minorUnits != 0L
}
