package io.github.anderescaray.cents.readme

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Backup
import io.github.anderescaray.cents.domain.model.BackupAccount
import io.github.anderescaray.cents.domain.model.BackupBudget
import io.github.anderescaray.cents.domain.model.BackupCategory
import io.github.anderescaray.cents.domain.model.BackupHolding
import io.github.anderescaray.cents.domain.model.BackupHoldingValue
import io.github.anderescaray.cents.domain.model.BackupRule
import io.github.anderescaray.cents.domain.model.BackupTrade
import io.github.anderescaray.cents.domain.model.BackupTransaction
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TradeSide
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

/**
 * An invented ledger for the README's animations: a year and a half of one person's money, with a
 * brokerage account whose values were typed every week. Nothing in it is anybody's real data.
 *
 * Built through the same restore a backup goes through, so the screens recorded are the app's
 * own, reading the app's own database.
 */
@Suppress("MagicNumber", "LongMethod")
internal object ReadmeLedger {
    const val EUR = "EUR"
    const val CHECKING = 1L
    private const val CASH = 2L
    const val BROKERAGE = 3L

    private const val SALARY = 1L
    private const val RENT = 2L
    private const val GROCERIES = 3L
    const val EATING_OUT = 4L
    private const val TRANSPORT = 5L
    private const val UTILITIES = 6L
    private const val SUBSCRIPTIONS = 7L
    private const val LEISURE = 8L
    private const val HEALTH = 9L

    private const val RENT_RULE = 1L
    private const val STREAMING_RULE = 2L
    private const val GYM_RULE = 3L

    private const val ALL_WORLD = 1L
    private const val SP500 = 2L
    private const val BITCOIN = 3L

    private const val MONTHS = 18

    fun build(today: LocalDate): Backup {
        val start = today.withDayOfMonth(1).minusMonths(MONTHS.toLong() - 1)
        val random = Random(7)
        val transactions = mutableListOf<BackupTransaction>()

        fun add(
            accountId: Long,
            categoryId: Long,
            kind: TransactionKind,
            amountMinor: Long,
            date: LocalDate,
            note: String?,
            ruleId: Long? = null,
        ) {
            if (date.isAfter(today)) return
            transactions +=
                BackupTransaction(
                    id = transactions.size + 1L,
                    accountId = accountId,
                    categoryId = categoryId,
                    kind = kind.name,
                    amountMinor = amountMinor,
                    currency = EUR,
                    date = date.toEpochDay(),
                    note = note,
                    transferId = null,
                    tradeId = null,
                    ruleId = ruleId,
                )
        }

        val shops = listOf("Mercado Central", "Lidl", "Carrefour", "Fruteria Ana")
        val places = listOf("Café Iruña", "Lunch with Marta", "Pizzeria Napoli", "Sushi bar")
        var month = start
        while (!month.isAfter(today)) {
            add(CHECKING, RENT, TransactionKind.EXPENSE, 95_000, month, "Rent", RENT_RULE)
            add(CHECKING, SUBSCRIPTIONS, TransactionKind.EXPENSE, 1_399, month.withDayOfMonth(3), "Streaming", STREAMING_RULE)
            add(CHECKING, LEISURE, TransactionKind.EXPENSE, 3_990, month.withDayOfMonth(5), "Gym", GYM_RULE)
            add(CHECKING, SALARY, TransactionKind.INCOME, 248_000, month.withDayOfMonth(25), "Salary")
            add(CHECKING, UTILITIES, TransactionKind.EXPENSE, random.nextLong(5_500, 9_800), month.withDayOfMonth(9), "Electricity")
            add(CHECKING, UTILITIES, TransactionKind.EXPENSE, 3_500, month.withDayOfMonth(12), "Internet")
            var day = month
            while (day.month == month.month && !day.isAfter(today)) {
                if (random.nextInt(3) == 0) {
                    add(CHECKING, GROCERIES, TransactionKind.EXPENSE, random.nextLong(1_800, 8_500), day, shops.random(random))
                }
                if (random.nextInt(4) == 0) {
                    add(CASH, EATING_OUT, TransactionKind.EXPENSE, random.nextLong(900, 4_800), day, places.random(random))
                }
                if (random.nextInt(5) == 0) {
                    add(CHECKING, TRANSPORT, TransactionKind.EXPENSE, random.nextLong(250, 4_500), day, "Metro")
                }
                if (random.nextInt(12) == 0) {
                    add(CHECKING, LEISURE, TransactionKind.EXPENSE, random.nextLong(1_200, 9_000), day, "Cinema")
                }
                day = day.plusDays(1)
            }
            if (random.nextInt(3) == 0) {
                add(CHECKING, HEALTH, TransactionKind.EXPENSE, random.nextLong(1_500, 6_000), month.withDayOfMonth(16), "Pharmacy")
            }
            month = month.plusMonths(1)
        }

        val portfolio = portfolio(today, start)
        return Backup(
            schemaVersion = CentsDatabase.VERSION,
            exportedAt = Instant.EPOCH,
            accounts =
                listOf(
                    account(CHECKING, "Checking", AccountType.BANK, 420_000, 0xFF3B6FE0.toInt(), 0),
                    account(CASH, "Cash", AccountType.CASH, 18_000, 0xFF2FA36B.toInt(), 1),
                    account(BROKERAGE, "Brokerage", AccountType.BROKER, 1_850_000, 0xFF7A4FD6.toInt(), 2),
                ),
            categories =
                listOf(
                    category(SALARY, "Salary", CategoryKind.INCOME, 0xFF2E9E5B.toInt(), 0),
                    category(RENT, "Rent", CategoryKind.EXPENSE, 0xFF8C5A3C.toInt(), 1),
                    category(GROCERIES, "Groceries", CategoryKind.EXPENSE, 0xFF34A853.toInt(), 2),
                    category(EATING_OUT, "Eating out", CategoryKind.EXPENSE, 0xFFF29D38.toInt(), 3),
                    category(TRANSPORT, "Transport", CategoryKind.EXPENSE, 0xFF3D8BF2.toInt(), 4),
                    category(UTILITIES, "Bills", CategoryKind.EXPENSE, 0xFF9B5DE5.toInt(), 5),
                    category(SUBSCRIPTIONS, "Subscriptions", CategoryKind.EXPENSE, 0xFFE5484D.toInt(), 6),
                    category(LEISURE, "Leisure", CategoryKind.EXPENSE, 0xFF12A4B6.toInt(), 7),
                    category(HEALTH, "Health", CategoryKind.EXPENSE, 0xFFE0569B.toInt(), 8),
                ),
            transactions = transactions,
            holdings =
                listOf(
                    holding(ALL_WORLD, AssetClass.ETF, "All-World ETF"),
                    holding(SP500, AssetClass.INDEX_FUND, "S&P 500 index fund"),
                    holding(BITCOIN, AssetClass.CRYPTO, "Bitcoin"),
                ),
            trades = portfolio.trades,
            values = portfolio.values,
            rules =
                listOf(
                    rule(RENT_RULE, "Rent", RENT, 95_000, start, today),
                    rule(STREAMING_RULE, "Streaming", SUBSCRIPTIONS, 1_399, start.withDayOfMonth(3), today),
                    rule(GYM_RULE, "Gym", LEISURE, 3_990, start.withDayOfMonth(5), today),
                ),
            budgets =
                listOf(
                    BackupBudget(categoryId = null, amountMinor = 160_000, currency = EUR),
                    BackupBudget(categoryId = GROCERIES, amountMinor = 35_000, currency = EUR),
                    BackupBudget(categoryId = EATING_OUT, amountMinor = 15_000, currency = EUR),
                ),
        )
    }

    private class Portfolio(
        val trades: List<BackupTrade>,
        val values: List<BackupHoldingValue>,
    )

    /**
     * Monthly purchases into two funds and two into bitcoin, and a value typed every Friday: a
     * market that climbs, dips for a couple of months and recovers, so the chart has a story.
     */
    private fun portfolio(
        today: LocalDate,
        start: LocalDate,
    ): Portfolio {
        val trades = mutableListOf<BackupTrade>()
        fun buy(
            holdingId: Long,
            amountMinor: Long,
            date: LocalDate,
        ) {
            if (date.isAfter(today)) return
            trades +=
                BackupTrade(
                    id = trades.size + 1L,
                    holdingId = holdingId,
                    side = TradeSide.BUY.name,
                    amountMinor = amountMinor,
                    sellsAll = false,
                    currency = EUR,
                    date = date.toEpochDay(),
                    note = null,
                )
        }
        buy(ALL_WORLD, 600_000, start.withDayOfMonth(2))
        buy(BITCOIN, 100_000, start.plusMonths(4).withDayOfMonth(8))
        buy(BITCOIN, 50_000, start.plusMonths(11).withDayOfMonth(14))
        var month = start
        while (!month.isAfter(today)) {
            buy(ALL_WORLD, 30_000, month.withDayOfMonth(27))
            buy(SP500, 20_000, month.withDayOfMonth(27))
            month = month.plusMonths(1)
        }

        val values = mutableListOf<BackupHoldingValue>()
        val first = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        val weeks = (today.toEpochDay() - first.toEpochDay()) / 7
        val random = Random(11)
        var friday = first
        while (!friday.isAfter(today)) {
            val t = (friday.toEpochDay() - first.toEpochDay()) / 7.0 / weeks.coerceAtLeast(1)
            for (holdingId in listOf(ALL_WORLD, SP500, BITCOIN)) {
                val invested = trades.filter { it.holdingId == holdingId && it.date <= friday.toEpochDay() }.sumOf { it.amountMinor }
                if (invested == 0L) continue
                val growth = growth(holdingId, t) * (1 + random.nextDouble(-0.006, 0.006))
                values += BackupHoldingValue(holdingId, friday.toEpochDay(), (invested * growth).roundToLong(), EUR)
            }
            friday = friday.plusWeeks(1)
        }
        return Portfolio(trades, values)
    }

    /** How far above what was put in an asset stands at [t], from 0 (the start) to 1 (today). */
    private fun growth(
        holdingId: Long,
        t: Double,
    ): Double {
        val dip = -0.07 * sin(PI * ((t - 0.45) / 0.2).coerceIn(0.0, 1.0))
        return when (holdingId) {
            ALL_WORLD -> 1 + 0.19 * t + dip + 0.015 * sin(t * 19)
            SP500 -> 1 + 0.15 * t + dip * 1.2 + 0.02 * sin(t * 23)
            else -> 1 + 0.55 * t + dip * 3 + 0.08 * sin(t * 13)
        }
    }

    private fun account(
        id: Long,
        name: String,
        type: AccountType,
        openingBalanceMinor: Long,
        colorArgb: Int,
        sortOrder: Int,
    ) = BackupAccount(id, name, type.name, EUR, openingBalanceMinor, colorArgb, archived = false, sortOrder = sortOrder)

    private fun category(
        id: Long,
        name: String,
        kind: CategoryKind,
        colorArgb: Int,
        sortOrder: Int,
    ) = BackupCategory(id, name, kind.name, colorArgb, parentId = null, archived = false, sortOrder = sortOrder)

    private fun holding(
        id: Long,
        assetClass: AssetClass,
        name: String,
    ) = BackupHolding(id, BROKERAGE, assetClass.name, name, EUR, note = null)

    private fun rule(
        id: Long,
        name: String,
        categoryId: Long,
        amountMinor: Long,
        start: LocalDate,
        today: LocalDate,
    ) = BackupRule(
        id = id,
        name = name,
        accountId = CHECKING,
        categoryId = categoryId,
        kind = TransactionKind.EXPENSE.name,
        amountMinor = amountMinor,
        currency = EUR,
        frequency = Frequency.MONTHLY.name,
        intervalDays = null,
        startDate = start.toEpochDay(),
        endDate = null,
        mode = RuleMode.AUTOMATIC.name,
        active = true,
        lastGenerated = start.withMonth(today.monthValue).withYear(today.year).toEpochDay(),
    )
}
