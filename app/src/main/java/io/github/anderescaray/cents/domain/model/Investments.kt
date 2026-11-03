package io.github.anderescaray.cents.domain.model

import java.math.BigInteger
import java.time.LocalDate

/*
 * Investments as the owner keeps them (`DECISIONS.md` D40): an asset is a type and a free name,
 * held in a broker account, and every number about it is an amount of money. There are no units,
 * no prices per unit and no symbols to match against a market; what an asset is worth is the
 * value the owner typed last, carried forward week to week until the next one.
 */

/** The four kinds of thing an asset can be, in the order screens list them. */
enum class AssetClass {
    STOCK,
    ETF,
    INDEX_FUND,
    CRYPTO,
}

/** A purchase puts money into an asset; a sale takes it out. */
enum class TradeSide {
    BUY,
    SELL,
}

/**
 * Something held in a broker account. [name] is the owner's own and matches nothing; [currency] is
 * the account's, taken when the asset is created (rule 2).
 */
data class Holding(
    val id: Long,
    val accountId: Long,
    val assetClass: AssetClass,
    val name: String,
    val currency: String,
    val note: String?,
)

/**
 * One purchase or sale, as an amount. [amount] is always positive; the direction is [side].
 *
 * A purchase's amount is what left the account's cash. A sale's is what came back into it, and
 * [sellsAll] says whether it emptied the asset: a part sale removes cost in the proportion of the
 * value it sold, a whole one removes all of it, whatever the last typed value said.
 */
data class Trade(
    val id: Long,
    val holdingId: Long,
    val side: TradeSide,
    val amount: Money,
    val date: LocalDate,
    val note: String?,
    val sellsAll: Boolean = false,
)

/** What the owner said an asset was worth on [date]: all of it, not a price per unit. */
data class HoldingValue(
    val id: Long,
    val holdingId: Long,
    val date: LocalDate,
    val value: Money,
)

/**
 * A sale with the cost it took out of the asset. [result] is what the owner made or lost by it,
 * and is what becomes a movement in the account (D40).
 */
data class Sale(
    val holding: Holding,
    val trade: Trade,
    val cost: Money,
) {
    val result: Money get() = trade.amount - cost

    /** The result against what the sold part cost: 0.12 is up 12%. Null against no cost. */
    val ratio: Double? get() = ratioOf(result, cost)
}

/** What an asset was worth at the end of [date], once everything that happened that day is in. */
data class ValueStep(
    val date: LocalDate,
    val value: Money,
)

/**
 * An asset, with everything its purchases, sales and values add up to.
 *
 * [steps] is the asset's value through time, one step per day something happened, which is what
 * every chart reads. [value] and [cost] are where those steps end.
 */
data class Position(
    val holding: Holding,
    val value: Money,
    val cost: Money,
    val sales: List<Sale>,
    val steps: List<ValueStep>,
    /** The day of the last value the owner typed, or null if none has been. */
    val lastValuedOn: LocalDate?,
    /** Every purchase and sale, oldest first: the asset's history. */
    val trades: List<Trade> = emptyList(),
    /** Every value typed, oldest first. */
    val values: List<HoldingValue> = emptyList(),
) {
    /** Still held: something is left to be worth or to have cost. */
    val isOpen: Boolean get() = !value.isZero || !cost.isZero

    val profit: Money get() = value - cost

    val profitRatio: Double? get() = ratioOf(profit, cost)

    val realised: Money get() = sales.fold(Money.zero(holding.currency)) { sum, sale -> sum + sale.result }

    /** What the asset was worth at the end of [date]: zero before its first purchase. */
    fun valueOn(date: LocalDate): Money =
        steps.lastOrNull { !it.date.isAfter(date) }?.value ?: Money.zero(value.currency)

    /** The first day anything happened to it, or null for an asset with no history yet. */
    val firstDate: LocalDate? get() = steps.firstOrNull()?.date
}

/**
 * A broker account and what is in it: the [cash] not invested, and the [positions] that are.
 *
 * [cashSteps] is the cash through time, one step per day it changed, starting from what there was
 * before the first of them; together with each position's steps it is the account's chart.
 */
data class InvestmentAccount(
    val account: Account,
    val cash: Money,
    val positions: List<Position>,
    val cashSteps: List<ValueStep> = emptyList(),
    val cashBefore: Money = Money.zero(account.currency),
) {
    private val zero: Money get() = Money.zero(account.currency)

    val openPositions: List<Position> get() = positions.filter { it.isOpen }

    /** What the open assets are worth now. */
    val invested: Money get() = openPositions.fold(zero) { sum, position -> sum + position.value }

    /** What the open assets cost. */
    val cost: Money get() = openPositions.fold(zero) { sum, position -> sum + position.cost }

    /** Cash plus what is invested: the live total Wallet shows. */
    val total: Money get() = cash + invested

    /** What the open assets have made or lost so far, not yet sold. */
    val profit: Money get() = invested - cost

    val profitRatio: Double? get() = ratioOf(profit, cost)

    /** Every sale ever made in the account, newest first. */
    val sales: List<Sale>
        get() =
            positions
                .flatMap {
                    it.sales
                }.sortedWith(compareByDescending<Sale> { it.trade.date }.thenByDescending { it.trade.id })

    /** What those sales made or lost, all together. */
    val realised: Money get() = positions.fold(zero) { sum, position -> sum + position.realised }

    /** The cash at the end of [date]. */
    fun cashOn(date: LocalDate): Money = cashSteps.lastOrNull { !it.date.isAfter(date) }?.value ?: cashBefore

    /** Cash and assets at the end of [date]: the account's chart. */
    fun totalOn(date: LocalDate): Money = positions.fold(cashOn(date)) { sum, position -> sum + position.valueOn(date) }

    /** The first day anything happened in the account, or null if nothing ever has. */
    val firstDate: LocalDate?
        get() = (listOfNotNull(cashSteps.firstOrNull()?.date) + positions.mapNotNull { it.firstDate }).minOrNull()
}

/**
 * Every broker account at once: Invested's screen.
 *
 * The sums are over accounts in [currency] only. Accounts in another currency are listed but not
 * added, the refusal the rest of the app makes rather than inventing a rate (rule 2, D2).
 */
data class InvestmentOverview(
    val currency: String?,
    val accounts: List<InvestmentAccount>,
) {
    private val counted: List<InvestmentAccount> get() = accounts.filter { it.account.currency == currency }

    /** Every figure at once, or null when the accounts share no currency to add them in. */
    val sums: InvestmentSums?
        get() =
            currency?.let { code ->
                fun sum(of: (InvestmentAccount) -> Money) =
                    counted.fold(Money.zero(code)) { total, account ->
                        total +
                            of(account)
                    }
                val cost = sum { it.cost }
                val profit = sum { it.profit }
                InvestmentSums(
                    total = sum { it.total },
                    cost = cost,
                    invested = sum { it.invested },
                    profit = profit,
                    profitRatio = ratioOf(profit, cost),
                    realised = sum { it.realised },
                )
            }

    fun totalOn(date: LocalDate): Money? =
        currency?.let { code ->
            counted.fold(Money.zero(code)) { total, account ->
                total +
                    account.totalOn(date)
            }
        }

    val firstDate: LocalDate? get() = counted.mapNotNull { it.firstDate }.minOrNull()

    /** True when some account is in another currency and is left out of the sums. */
    val leavesSomeOut: Boolean get() = counted.size != accounts.size
}

/** What every broker account comes to together, in the currency they share. */
data class InvestmentSums(
    val total: Money,
    val cost: Money,
    val invested: Money,
    val profit: Money,
    val profitRatio: Double?,
    val realised: Money,
)

/** How far apart a chart's points are (D40). */
enum class ChartScale {
    WEEK,
    MONTH,
    YEAR,
}

/** One point of a chart: the period that starts on [start], and what it was worth. */
data class ChartPoint(
    val start: LocalDate,
    val value: Money,
)

/**
 * A gain as a fraction of what it is measured against: 0.12 is 12%. Only ever shown, never added,
 * so it is the one honest `Double` among the money; null against zero.
 */
internal fun ratioOf(
    gain: Money,
    base: Money,
): Double? = if (base.minorUnits == 0L) null else gain.minorUnits.toDouble() / base.minorUnits.toDouble()

/**
 * [numerator] / [denominator], rounded half away from zero, through [BigInteger] so the product
 * that usually comes before it cannot overflow.
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
