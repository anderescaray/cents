package io.github.anderescaray.cents.domain

import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Portfolio
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.domain.model.PriceQuote
import io.github.anderescaray.cents.domain.model.Quantity
import io.github.anderescaray.cents.domain.model.QuoteSource
import io.github.anderescaray.cents.domain.usecase.netWorthOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Cash, holdings, and the two together (`DECISIONS.md` D19).
 *
 * The sum is only meaningful because a purchase takes its cost out of the account it is held in:
 * before that, an investments account's balance and the portfolio described the same euros, and
 * adding them counted the money twice. Most of what is below is about the cases where there is
 * no honest sum to give.
 */
class NetWorthTest {
    @Test
    fun `cash and holdings add up`() {
        val worth =
            netWorthOf(
                active = listOf(account("Checking", 60_000), account("Broker", 20_000)),
                archived = emptyList(),
                portfolio = portfolio(position(units = 2, priceMinor = 20_000)),
            )

        // 600 € of cash, 200 € in the broker, 400 € of shares.
        assertEquals(Money(80_000, "EUR"), worth?.inAccounts)
        assertEquals(Money(40_000, "EUR"), worth?.invested)
        assertEquals(Money(120_000, "EUR"), worth?.total)
    }

    @Test
    fun `with no holdings the cash is the whole of it`() {
        val worth = netWorthOf(listOf(account("Checking", 60_000)), emptyList(), Portfolio(emptyList()))

        // The "without the portfolio" half of what was asked for. A known zero rather than a
        // null, so the screen can tell "nothing held" from "nothing priced" and leave the row
        // out rather than print `Invested 0,00 €`.
        assertEquals(Money(60_000, "EUR"), worth?.inAccounts)
        assertEquals(Money.zero("EUR"), worth?.invested)
        assertEquals(Money(60_000, "EUR"), worth?.total)
        assertFalse(worth!!.hasUnpriced)
    }

    @Test
    fun `a holding nobody has priced leaves the total unknown rather than low`() {
        val worth =
            netWorthOf(
                active = listOf(account("Broker", 60_000)),
                archived = emptyList(),
                portfolio = portfolio(position(units = 2, priceMinor = null)),
            )

        // The failure this refuses: a net worth of 600 € over a portfolio that cost real money,
        // which is a number, and numbers get believed.
        assertNull(worth?.invested)
        assertNull(worth?.total)
        assertEquals(1, worth?.unpricedCount)
    }

    @Test
    fun `a priced holding beside an unpriced one counts what it can and says what it did not`() {
        val worth =
            netWorthOf(
                active = listOf(account("Broker", 60_000)),
                archived = emptyList(),
                portfolio =
                    portfolio(
                        position(units = 2, priceMinor = 20_000),
                        position(units = 1, priceMinor = null, symbol = "VWCE"),
                    ),
            )

        assertEquals(Money(40_000, "EUR"), worth?.invested)
        assertEquals(Money(100_000, "EUR"), worth?.total)
        assertTrue(worth!!.hasUnpriced)
        assertEquals(1, worth.unpricedCount)
    }

    @Test
    fun `money in an archived account is named rather than quietly left out`() {
        val worth =
            netWorthOf(
                active = listOf(account("Checking", 60_000)),
                archived = listOf(account("Old savings", 5_000, archived = true)),
                portfolio = Portfolio(emptyList()),
            )

        // Counting it would make this disagree with the total the accounts list has always
        // shown under the active ones; leaving it out silently would understate net worth.
        assertEquals(Money(60_000, "EUR"), worth?.inAccounts)
        assertEquals(Money(5_000, "EUR"), worth?.inArchivedAccounts)
        assertTrue(worth!!.hasArchivedMoney)
    }

    @Test
    fun `an empty archived account says nothing at all`() {
        val worth =
            netWorthOf(
                active = listOf(account("Checking", 60_000)),
                archived = listOf(account("Closed", 0, archived = true)),
                portfolio = Portfolio(emptyList()),
            )

        // Which is the normal case: archiving an account usually follows emptying it.
        assertFalse(worth!!.hasArchivedMoney)
    }

    @Test
    fun `accounts that disagree about their currency have no total`() {
        val worth =
            netWorthOf(
                active = listOf(account("Checking", 60_000), account("Dollars", 10_000, currency = "USD")),
                archived = emptyList(),
                portfolio = Portfolio(emptyList()),
            )

        // The refusal the accounts list has always made rather than inventing a rate (rule 2).
        assertNull(worth)
    }

    @Test
    fun `holdings priced in another currency have no total either`() {
        val worth =
            netWorthOf(
                active = listOf(account("Broker", 60_000)),
                archived = emptyList(),
                portfolio = portfolio(position(units = 1, priceMinor = 20_000, currency = "USD")),
            )

        // The same refusal from the other side, and the one an install predating D2 can still
        // reach.
        assertNull(worth)
    }

    @Test
    fun `a sold-out holding is not worth anything and does not stop the sum`() {
        val worth =
            netWorthOf(
                active = listOf(account("Broker", 110_000)),
                archived = emptyList(),
                portfolio = portfolio(position(units = 0, priceMinor = 20_000)),
            )

        // A closed position has no quantity, so `Portfolio.totals()` never sees it (D17) and
        // what the sale returned is already in the account's balance.
        assertEquals(Money(110_000, "EUR"), worth?.total)
        assertFalse(worth!!.hasUnpriced)
    }

    private fun account(
        name: String,
        balanceMinor: Long,
        currency: String = "EUR",
        archived: Boolean = false,
    ) = AccountWithBalance(
        account =
            Account(
                id = name.hashCode().toLong(),
                name = name,
                type = AccountType.BANK,
                currency = currency,
                openingBalance = Money.zero(currency),
                colorArgb = 0,
                archived = archived,
                sortOrder = 0,
            ),
        balance = Money(balanceMinor, currency),
    )

    private fun portfolio(vararg positions: Position) = Portfolio(positions.toList())

    private fun position(
        units: Long,
        priceMinor: Long?,
        symbol: String = "NVD.DE",
        currency: String = "EUR",
    ) = Position(
        holding =
            Holding(
                id = symbol.hashCode().toLong(),
                accountId = 1,
                assetClass = AssetClass.STOCK,
                symbol = symbol,
                name = symbol,
                currency = currency,
                note = null,
            ),
        quantity = Quantity.ofUnits(units),
        costBasis = Money(10_000 * units, currency),
        realisedPnl = Money.zero(currency),
        quote =
            priceMinor?.let {
                PriceQuote(
                    symbol = symbol,
                    price = Money(it, currency),
                    asOf = Instant.parse("2026-09-17T10:00:00Z"),
                    source = QuoteSource.MANUAL,
                )
            },
    )
}
