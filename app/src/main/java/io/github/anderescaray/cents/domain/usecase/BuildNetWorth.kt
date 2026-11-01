package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.domain.model.Position

/**
 * Adds up what the owner has, normal money and invested apart (`DECISIONS.md` D34), or declines
 * to. Wallet and Home both read this, so the two can never disagree.
 *
 * **Normal money** is the active cash and bank accounts. **Invested** is the active broker
 * accounts' cash plus every open asset at its last typed value, wherever it is held: an asset left
 * in a bank account from before only brokers could hold investments is still invested.
 *
 * **Null means no honest answer exists**: the accounts disagree about their currency, or an open
 * asset is in one they are not. That is the refusal the accounts list has always made rather than
 * inventing a rate (rule 2, `DECISIONS.md` D2).
 */
fun netWorthOf(
    active: List<AccountWithBalance>,
    archived: List<AccountWithBalance>,
    positions: List<Position>,
): NetWorth? {
    val currency = active.map { it.account.currency }.distinct().singleOrNull()
    val open = positions.filter { it.isOpen }
    if (currency == null || open.any { it.holding.currency != currency }) return null

    val zero = Money.zero(currency)
    val (brokers, normal) = active.partition { it.account.type == AccountType.BROKER }
    val brokerCash = brokers.fold(zero) { sum, item -> sum + item.balance }
    return NetWorth(
        currency = currency,
        normal = normal.fold(zero) { sum, item -> sum + item.balance },
        invested = open.fold(brokerCash) { sum, position -> sum + position.value },
        inArchivedAccounts =
            archived
                .filter { it.account.currency == currency }
                .fold(zero) { sum, item -> sum + item.balance },
    )
}

/** The same, from every account at once: the archived ones are told apart here. */
fun netWorthOf(
    accounts: List<AccountWithBalance>,
    positions: List<Position>,
): NetWorth? {
    val (archived, active) = accounts.partition { it.account.archived }
    return netWorthOf(active = active, archived = archived, positions = positions)
}

/**
 * Each account's balance as Wallet shows it: a broker's is its cash plus what its open assets are
 * worth now, the live total (D40). Every other account's is its balance.
 */
fun liveBalances(
    accounts: List<AccountWithBalance>,
    positions: List<Position>,
): List<AccountWithBalance> {
    val investedByAccount =
        positions
            .filter { it.isOpen }
            .groupBy { it.holding.accountId }
    return accounts.map { item ->
        val held = investedByAccount[item.account.id].orEmpty().filter { it.holding.currency == item.balance.currency }
        if (held.isEmpty()) {
            item
        } else {
            item.copy(
                balance =
                    held.fold(item.balance) { sum, position ->
                        sum +
                            position.value
                    },
            )
        }
    }
}
