package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.domain.model.Portfolio

/**
 * Adds up what the owner has, normal money and invested apart (`DECISIONS.md` D34), or declines
 * to. Wallet and Home both read this, so the two can never disagree.
 *
 * **Normal money** is the active cash and bank accounts. **Invested** is the active broker
 * accounts' cash plus every open holding at its latest typed price, wherever it is held: a holding
 * left in a bank account from before only brokers could hold investments is still invested.
 *
 * **Null means no honest answer exists**, and there are exactly two ways to get there. Either the
 * accounts disagree about their currency, which is the refusal the accounts list has always made
 * rather than inventing a rate (rule 2, `DECISIONS.md` D2); or the holdings are priced in something
 * the accounts are not, which is the same refusal from the other side.
 *
 * **An empty portfolio is not a refusal**: an owner with no investments and no broker gets a known
 * zero as the invested part, which the screen can tell apart from *no price yet*.
 */
fun netWorthOf(
    active: List<AccountWithBalance>,
    archived: List<AccountWithBalance>,
    portfolio: Portfolio,
): NetWorth? {
    val currency = active.map { it.account.currency }.distinct().singleOrNull()
    val totals = portfolio.totals()
    // More than one currency in the portfolio, or one that is not the accounts' — either way
    // there is nothing that can be added without a rate.
    val holdings = totals.singleOrNull()?.takeIf { it.currency == currency }
    if (currency == null || (totals.isNotEmpty() && holdings == null)) return null

    val zero = Money.zero(currency)
    val (brokers, normal) = active.partition { it.account.type == AccountType.BROKER }
    val brokerCash = brokers.fold(zero) { sum, item -> sum + item.balance }
    // What is known: the brokers' cash, and the holdings that have a price. With holdings held,
    // none priced and no cash beside them, nothing is known, and a zero would be read as one.
    val pricedHoldings = holdings?.marketValue ?: zero
    val unknown = holdings != null && holdings.marketValue == null && brokerCash.isZero
    return NetWorth(
        currency = currency,
        normal = normal.fold(zero) { sum, item -> sum + item.balance },
        invested = if (unknown) null else brokerCash + pricedHoldings,
        unpricedCount = holdings?.unpricedCount ?: 0,
        inArchivedAccounts =
            archived
                .filter { it.account.currency == currency }
                .fold(zero) { sum, item -> sum + item.balance },
    )
}

/** The same, from every account at once: the archived ones are told apart here. */
fun netWorthOf(
    accounts: List<AccountWithBalance>,
    portfolio: Portfolio,
): NetWorth? {
    val (archived, active) = accounts.partition { it.account.archived }
    return netWorthOf(active = active, archived = archived, portfolio = portfolio)
}
