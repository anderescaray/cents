package io.github.anderescaray.cents.domain

import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.usecase.liveBalances
import io.github.anderescaray.cents.domain.usecase.netWorthOf
import io.github.anderescaray.cents.domain.usecase.positionOf
import io.github.anderescaray.cents.domain.usecase.tradeCashOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** What the owner has, normal money and invested apart, and each broker's live total (D34, D40). */
class NetWorthTest {
    private val day = LocalDate.of(2026, 11, 2)
    private val checking = account(1, AccountType.BANK)
    private val broker = account(2, AccountType.BROKER)
    private val fund = Holding(5, broker.id, AssetClass.INDEX_FUND, "World", "EUR", null)

    @Test
    fun `invested is the brokers' cash plus what the open assets are worth now`() {
        val position = positionOf(fund, listOf(buy(30_000)), listOf(HoldingValue(1, fund.id, day, eur(36_000))))

        val worth = netWorthOf(listOf(with(checking, 100_000), with(broker, 20_000)), listOf(position))!!

        assertEquals(eur(100_000), worth.normal)
        assertEquals(eur(56_000), worth.invested)
        assertEquals(eur(156_000), worth.total)
    }

    @Test
    fun `a sold asset is worth nothing more than the cash it returned`() {
        val sale = Trade(2, fund.id, TradeSide.SELL, eur(35_000), day, null, sellsAll = true)
        val position = positionOf(fund, listOf(buy(30_000), sale), emptyList())

        val worth = netWorthOf(listOf(with(broker, 55_000)), listOf(position))!!

        assertEquals(eur(55_000), worth.invested)
    }

    @Test
    fun `accounts in two currencies give no figure rather than a wrong one`() {
        val dollars = account(3, AccountType.BANK, currency = "USD")

        assertNull(netWorthOf(listOf(with(checking, 1), with(dollars, 1)), emptyList()))
    }

    @Test
    fun `a broker shows its cash plus what its assets are worth`() {
        val position = positionOf(fund, listOf(buy(30_000)), listOf(HoldingValue(1, fund.id, day, eur(33_000))))

        val live = liveBalances(listOf(with(checking, 100_000), with(broker, 20_000)), listOf(position))

        assertEquals(listOf(eur(100_000), eur(53_000)), live.map { it.balance })
    }

    @Test
    fun `a sale returns its cost and writes its result apart, and a purchase spends its amount`() {
        val sale = Trade(2, fund.id, TradeSide.SELL, eur(15_000), day, null)
        val trades = listOf(buy(30_000), sale)
        val position = positionOf(fund, trades, listOf(HoldingValue(1, fund.id, day, eur(40_000))))

        val cash = tradeCashOf(trades, position)

        // Worth 40 000 on cost 30 000: selling 15 000 sells 3/8, so 11 250 of cost comes back.
        assertEquals(listOf(-30_000L, 11_250L, 3_750L), cash.map { it.signedMinor })
        assertEquals(listOf(false, false, true), cash.map { it.isResult })
    }

    private fun buy(minor: Long) = Trade(1, fund.id, TradeSide.BUY, eur(minor), day.minusDays(7), null)

    private fun with(
        account: Account,
        minor: Long,
    ) = AccountWithBalance(account, Money(minor, account.currency))

    private fun account(
        id: Long,
        type: AccountType,
        currency: String = "EUR",
    ) = Account(id, "Account $id", type, currency, Money.zero(currency), 0, archived = false, sortOrder = id.toInt())

    private fun eur(minor: Long) = Money(minor, "EUR")
}
