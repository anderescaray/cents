package io.github.anderescaray.cents.domain.model

// Which accounts a new record may be put in. Both rules are about what is *offered* for something
// new: a record already in an account keeps it, and every balance, total and transfer counts every
// account whatever these say (`docs/next-improvements.md` §9 and §10).

/**
 * Whether a new ordinary income or expense may be recorded in this account.
 *
 * Not a broker: the money in a broker account arrives and leaves as a transfer, and what is spent
 * there is spent by a trade. Not archived, which already meant *stop offering it*.
 */
val Account.takesIncomeAndExpenses: Boolean
    get() = !archived && type != AccountType.BROKER

/** Whether a new holding may be placed in this account: a broker that is not archived. */
val Account.holdsInvestments: Boolean
    get() = !archived && type == AccountType.BROKER
