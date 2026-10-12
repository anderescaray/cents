package io.github.anderescaray.cents.domain.model

// Which accounts a new record may be put in. Every rule here is about what is *offered* for
// something new: a record already in an account keeps it, and every balance, total and transfer
// counts every account whatever these say (`docs/next-improvements.md`, items 9 and 10).

/**
 * Whether a new income or expense of [kind] may be typed into this account.
 *
 * A broker takes an **income**, a dividend or interest, but not an expense: what is spent there is
 * spent by a trade, and money leaves it as a transfer (`DECISIONS.md` D32). Not archived, which
 * already meant *stop offering it*.
 */
fun Account.takesNew(kind: TransactionKind): Boolean =
    !archived && (type != AccountType.BROKER || kind == TransactionKind.INCOME)

/**
 * Whether a subscription or an app chosen for capture may be tied to this account, and where a new
 * movement may start: not a broker, and not archived (D32). Those write movements unattended, of
 * either kind, so they keep to the stricter rule; a broker is only ever chosen by tapping it.
 */
val Account.takesIncomeAndExpenses: Boolean
    get() = takesNew(TransactionKind.EXPENSE)

/** Whether a new holding may be placed in this account: a broker that is not archived. */
val Account.holdsInvestments: Boolean
    get() = !archived && type == AccountType.BROKER
