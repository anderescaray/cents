package io.github.anderescaray.cents.domain.model

import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate

/**
 * What kind of thing is being held.
 *
 * The portfolio is listed under one heading per class because that is how the owner asked to
 * read it, and because the classes stop being interchangeable the moment prices come from a
 * provider: a ticker is looked up, an ISIN mostly is not (PRD 5.1), and crypto trades around
 * the clock.
 */
enum class AssetClass {
    STOCK,
    ETF,
    INDEX_FUND,
    CRYPTO,
}

/** Whether a trade added to the position or took away from it. */
enum class TradeSide {
    BUY,
    SELL,
}

/** Where a price came from, so a screen can say how much to trust it. */
enum class QuoteSource {
    /** Typed by the owner. Until the provider branch lands, every quote is one of these. */
    MANUAL,
    PROVIDER,
}

/**
 * How much of something is held: an exact integer of hundred-millionths of a unit.
 *
 * The same argument as [Money], for the same reason. A tenth of a share and 0.00000001 BTC are
 * both real quantities, and a `Double` holding either is a number that is nearly right — which,
 * multiplied by a price and added to a total, is a portfolio that does not reconcile with the
 * broker's own app. Eight decimals is what a satoshi needs and more than a share ever will.
 */
@JvmInline
value class Quantity(
    val scaled: Long,
) : Comparable<Quantity> {
    val isZero: Boolean get() = scaled == 0L

    val isNegative: Boolean get() = scaled < 0L

    operator fun plus(other: Quantity): Quantity = Quantity(scaled + other.scaled)

    operator fun minus(other: Quantity): Quantity = Quantity(scaled - other.scaled)

    override fun compareTo(other: Quantity): Int = scaled.compareTo(other.scaled)

    /**
     * What this many units are worth at [unitPrice], rounded half away from zero.
     *
     * The multiplication goes through [BigInteger] rather than `Long`, because the product of a
     * quantity and a price overflows a `Long` long before either factor is unreasonable: a
     * thousand units at a thousand euros is already 10^17 before the scale is divided out.
     * Overflowing silently here would produce a negative portfolio, which is the exact class of
     * bug the integer arithmetic in this app exists to make impossible.
     */
    operator fun times(unitPrice: Money): Money =
        Money(
            minorUnits =
                divideRoundingHalfUp(
                    BigInteger.valueOf(scaled) * BigInteger.valueOf(unitPrice.minorUnits),
                    SCALE_BIG,
                ),
            currency = unitPrice.currency,
        )

    companion object {
        /** Eight decimal places. */
        const val SCALE = 100_000_000L

        val ZERO = Quantity(0)

        private val SCALE_BIG: BigInteger = BigInteger.valueOf(SCALE)

        fun ofUnits(units: Long): Quantity = Quantity(units * SCALE)
    }
}

/**
 * Something the owner holds, inside one of their accounts.
 *
 * [symbol] is the ticker, ISIN or crypto symbol as the owner typed it, upper-cased. It is
 * required even while every price is typed by hand, because it is what a price is stored
 * against: two accounts holding NVDA share one quote, and they must never be able to disagree
 * about what NVDA is worth.
 *
 * [currency] is the currency the *instrument* is priced in. It is on the row from the start,
 * even though the app writes the device's currency into it today, because a holding priced in
 * dollars is the one place PRD 4.5 says conversion has to come back — and adding the column
 * later would mean a migration that has to guess what the existing rows meant.
 */
data class Holding(
    val id: Long,
    val accountId: Long,
    val assetClass: AssetClass,
    val symbol: String,
    val name: String,
    val currency: String,
    val note: String?,
)

/**
 * One purchase or sale. [quantity] and [pricePerUnit] are always positive: the direction lives
 * in [side], for the same reason a transaction's amount is positive and its kind carries the
 * sign.
 */
data class Trade(
    val id: Long,
    val holdingId: Long,
    val side: TradeSide,
    val quantity: Quantity,
    val pricePerUnit: Money,
    val fee: Money,
    val date: LocalDate,
    val note: String?,
) {
    /** What the trade moved, before the fee. */
    val gross: Money get() = quantity * pricePerUnit

    /** What it cost (a buy) or returned (a sale), the fee counted against you either way. */
    val net: Money get() = if (side == TradeSide.BUY) gross + fee else gross - fee
}

/** The last known price of an instrument, and how old it is. */
data class PriceQuote(
    val symbol: String,
    val price: Money,
    val asOf: Instant,
    val source: QuoteSource,
)

/**
 * A holding, the position left after every trade, and what that position is worth now.
 *
 * [costBasis] is what the units still held cost, fees included — a running total the average is
 * derived from rather than a stored average, so a sale reduces it proportionally and no
 * rounding accumulates across trades.
 *
 * [marketValue] and everything derived from it are **null when no price is known**. A portfolio
 * showing 0,00 EUR for an instrument nobody has priced is worse than one that says it does not
 * know: the first is a number, and numbers get believed.
 */
data class Position(
    val holding: Holding,
    val quantity: Quantity,
    val costBasis: Money,
    val realisedPnl: Money,
    val quote: PriceQuote?,
) {
    val isOpen: Boolean get() = !quantity.isZero

    /** What one unit cost on average, or null for a position that is fully sold. */
    val averageCost: Money?
        get() =
            if (quantity.isZero) {
                null
            } else {
                Money(
                    minorUnits =
                        divideRoundingHalfUp(
                            BigInteger.valueOf(costBasis.minorUnits) * BigInteger.valueOf(Quantity.SCALE),
                            BigInteger.valueOf(quantity.scaled),
                        ),
                    currency = costBasis.currency,
                )
            }

    val marketValue: Money? get() = quote?.let { quantity * it.price }

    val unrealisedPnl: Money? get() = marketValue?.let { it - costBasis }

    /**
     * The gain as a fraction of what was paid — 0.12 meaning up 12%. A ratio is the one number
     * here that is honestly a real number rather than an amount of money, and it is only ever
     * shown, never added to anything.
     */
    val unrealisedReturn: Double?
        get() {
            val pnl = unrealisedPnl ?: return null
            if (costBasis.minorUnits == 0L) return null
            return pnl.minorUnits.toDouble() / costBasis.minorUnits.toDouble()
        }
}

/**
 * The whole portfolio, grouped the way it is read.
 *
 * Totals are **per currency** rather than one number. Every holding carries the currency its
 * instrument is priced in, and adding dollars to euros without a rate is the invented number
 * this app refuses to show anywhere else (`DECISIONS.md` D2). Today they all agree, so there is
 * one entry; the day a dollar holding arrives the screen says so instead of lying.
 */
data class Portfolio(
    val positions: List<Position>,
) {
    val openPositions: List<Position> get() = positions.filter { it.isOpen }

    /** The classes that actually hold something, in the order [AssetClass] declares them. */
    fun openPositionsByClass(): List<Pair<AssetClass, List<Position>>> =
        AssetClass.entries
            .map { assetClass -> assetClass to openPositions.filter { it.holding.assetClass == assetClass } }
            .filter { (_, positions) -> positions.isNotEmpty() }

    /**
     * Market value, cost and profit per currency — and only over the positions that have a
     * price, so a total can never be the sum of *some* of it. [PortfolioTotal.unpricedCount]
     * is how the screen says what was left out.
     */
    fun totals(): List<PortfolioTotal> =
        openPositions
            .groupBy { it.holding.currency }
            .map { (currency, held) -> totalFor(currency, held) }
            .filter { it.marketValue.minorUnits != 0L || it.unpricedCount > 0 }
            .sortedBy { it.currency }

    private fun totalFor(
        currency: String,
        held: List<Position>,
    ): PortfolioTotal {
        val priced = held.filter { it.marketValue != null }
        return PortfolioTotal(
            currency = currency,
            marketValue = priced.fold(Money.zero(currency)) { acc, position -> acc + position.marketValue!! },
            costBasis = priced.fold(Money.zero(currency)) { acc, position -> acc + position.costBasis },
            unpricedCount = held.size - priced.size,
        )
    }
}

/** One currency's worth of the portfolio, and how much of it could not be priced. */
data class PortfolioTotal(
    val currency: String,
    val marketValue: Money,
    val costBasis: Money,
    val unpricedCount: Int,
) {
    val profit: Money get() = marketValue - costBasis

    val profitRatio: Double?
        get() =
            if (costBasis.minorUnits == 0L) {
                null
            } else {
                profit.minorUnits.toDouble() / costBasis.minorUnits.toDouble()
            }
}

/**
 * Integer division that rounds half away from zero, which is what rounding means everywhere
 * outside a compiler. Kept next to the two places that need it rather than exported, so it
 * cannot quietly become the app's general-purpose rounding rule.
 */
internal fun divideRoundingHalfUp(
    numerator: BigInteger,
    denominator: BigInteger,
): Long {
    if (denominator.signum() == 0) return 0L
    val sign = numerator.signum() * denominator.signum()
    val absNumerator = numerator.abs()
    val absDenominator = denominator.abs()
    val doubled = absNumerator * BigInteger.TWO + absDenominator
    val magnitude = doubled / (absDenominator * BigInteger.TWO)
    return if (sign < 0) magnitude.negate().toLong() else magnitude.toLong()
}
