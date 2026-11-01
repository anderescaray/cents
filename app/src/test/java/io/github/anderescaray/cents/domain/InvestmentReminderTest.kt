package io.github.anderescaray.cents.domain

import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Trade
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.usecase.positionOf
import io.github.anderescaray.cents.domain.usecase.withoutValueThisWeek
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Which assets the Friday reminder asks about (`DECISIONS.md` D40). */
class InvestmentReminderTest {
    private val friday = LocalDate.of(2026, 11, 6)
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `an asset valued or bought this week is not asked about, an older one is`() {
        val valued = position(1, bought = monday.minusDays(30), valuedOn = monday)
        val boughtThisWeek = position(2, bought = monday.plusDays(1))
        val lastWeek = position(3, bought = monday.minusDays(30), valuedOn = monday.minusDays(1))

        val waiting = withoutValueThisWeek(listOf(valued, boughtThisWeek, lastWeek), friday)

        assertEquals(listOf(3L), waiting.map { it.holding.id })
    }

    @Test
    fun `a sold asset is not asked about`() {
        val holding = holding(4)
        val sold =
            positionOf(
                holding,
                listOf(
                    buy(holding, monday.minusDays(30)),
                    Trade(9, 4, TradeSide.SELL, eur(10_000), monday.minusDays(20), null, sellsAll = true),
                ),
                emptyList(),
            )

        assertEquals(emptyList<Long>(), withoutValueThisWeek(listOf(sold), friday).map { it.holding.id })
    }

    private fun position(
        id: Long,
        bought: LocalDate,
        valuedOn: LocalDate? = null,
    ) = holding(id).let { holding ->
        positionOf(
            holding,
            listOf(buy(holding, bought)),
            listOfNotNull(
                valuedOn?.let {
                    HoldingValue(id, id, it, eur(11_000))
                },
            ),
        )
    }

    private fun holding(id: Long) = Holding(id, 1, AssetClass.ETF, "Asset $id", "EUR", null)

    private fun buy(
        holding: Holding,
        date: LocalDate,
    ) = Trade(holding.id * 10, holding.id, TradeSide.BUY, eur(10_000), date, null)

    private fun eur(minor: Long) = Money(minor, "EUR")
}
