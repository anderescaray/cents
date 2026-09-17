package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.domain.model.Portfolio

/**
 * Adds up what the owner has, or declines to.
 *
 * **Null means no honest single answer exists**, and there are exactly two ways to get there.
 * Either the accounts disagree about their currency, which is the refusal the accounts list has
 * always made rather than inventing a rate (rule 2, `DECISIONS.md` D2); or the holdings are
 * priced in something the accounts are not, which is the same refusal from the other side. In
 * both cases the screen falls back to the total it showed before, which is still true.
 *
 * **An empty portfolio is not a refusal**: an owner with no investments gets their cash as the
 * whole of it, which is exactly the "without the portfolio" half of what was asked for. What
 * they do not get is a row reading *Invested 0,00 €*, because that is noise rather than news —
 * deciding whether to show it is the screen's job, and [NetWorth.invested] being a real zero is
 * what lets it decide.
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
    return NetWorth(
        currency = currency,
        inAccounts = active.fold(zero) { sum, item -> sum + item.balance },
        // No holdings at all is a known zero; holdings with no price is an unknown, and the two
        // must not render the same way.
        invested = if (holdings == null) zero else holdings.marketValue,
        unpricedCount = holdings?.unpricedCount ?: 0,
        inArchivedAccounts =
            archived
                .filter { it.account.currency == currency }
                .fold(zero) { sum, item -> sum + item.balance },
    )
}
