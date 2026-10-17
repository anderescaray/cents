package io.github.anderescaray.cents.domain.model

/**
 * What the owner has, kept apart the way they asked (`DECISIONS.md` D34): the
 * **normal money** in cash and bank accounts, what is **invested** with brokers, and the two
 * together.
 *
 * **The parts rather than one figure**, because each answers a different question: [normal] is
 * what could be spent today, [invested] is what is tied up, and [total] is what it comes to. A
 * single figure hides which of the two moved when it changes. Which of the three are shown is
 * the owner's choice ([MoneyFigures]); this holds all of them either way.
 *
 * Money moves between the two, it is never counted twice. A transfer from a bank to a broker
 * takes it out of [normal] and puts it in [invested]; a purchase turns a broker's cash into a
 * holding, which leaves [normal] alone. That only holds because a purchase takes its cost out of
 * the account it is held in.
 */
data class NetWorth(
    val currency: String,
    /** The balances of the active cash and bank accounts. Archived ones are in [inArchivedAccounts]. */
    val normal: Money,
    /**
     * The active broker accounts' cash plus what the priced holdings are worth, and **null when
     * nothing is known**: holdings are held, none of them has a price, and the brokers hold no
     * cash.
     *
     * Only the priced holdings are counted, exactly as [PortfolioTotal] counts them one screen
     * down, and [unpricedCount] says how many were left out. An invested figure of 0,00 € over
     * holdings nobody has priced would be a number, and numbers get believed.
     */
    val invested: Money?,
    /** How many holdings were left out of [invested] for want of a price. */
    val unpricedCount: Int,
    /**
     * Money sitting in archived accounts, which neither part counts.
     *
     * Usually zero, because archiving an account usually follows emptying it. When it is not, the
     * screen says so rather than quietly leaving the money out of a figure about what the owner
     * has.
     */
    val inArchivedAccounts: Money,
) {
    /** Normal money plus invested, and null while the invested part is unknown. */
    val total: Money? get() = invested?.let { normal + it }

    /** Whether anything was left out for want of a price. */
    val hasUnpriced: Boolean get() = unpricedCount > 0

    /** Whether there is money the figures do not count. */
    val hasArchivedMoney: Boolean get() = inArchivedAccounts.minorUnits != 0L
}
