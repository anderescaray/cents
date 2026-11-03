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
 * takes it out of [normal] and puts it in [invested]; a purchase turns a broker's cash into an
 * asset, which leaves [normal] alone. That only holds because a purchase takes its cost out of
 * the account it is held in.
 */
data class NetWorth(
    val currency: String,
    /** The balances of the active cash and bank accounts. Archived ones are in [inArchivedAccounts]. */
    val normal: Money,
    /**
     * The active broker accounts' cash plus what their open assets are worth at the last value
     * typed (D40). An asset always has a worth: until a value is typed it is what was paid.
     */
    val invested: Money,
    /**
     * Money sitting in archived accounts, which neither part counts.
     *
     * Usually zero, because archiving an account usually follows emptying it. When it is not, the
     * screen says so rather than quietly leaving the money out of a figure about what the owner
     * has.
     */
    val inArchivedAccounts: Money,
) {
    /** Normal money plus invested. */
    val total: Money get() = normal + invested

    /** Whether there is money the figures do not count. */
    val hasArchivedMoney: Boolean get() = inArchivedAccounts.minorUnits != 0L
}
