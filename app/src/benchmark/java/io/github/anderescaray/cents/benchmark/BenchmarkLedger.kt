package io.github.anderescaray.cents.benchmark

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.Backup
import io.github.anderescaray.cents.domain.model.BackupAccount
import io.github.anderescaray.cents.domain.model.BackupBudget
import io.github.anderescaray.cents.domain.model.BackupCategory
import io.github.anderescaray.cents.domain.model.BackupGroup
import io.github.anderescaray.cents.domain.model.BackupRule
import io.github.anderescaray.cents.domain.model.BackupTransaction
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/**
 * Two years of an invented ledger, for the benchmarks to measure against.
 *
 * A fresh install has nothing in it, and a startup or a scroll measured on empty screens says
 * nothing about the phone the owner carries. So the builds the benchmarks run put this in first,
 * through the same restore a backup goes through. Built in code rather than kept as a file, so a
 * change to the backup's shape stops it compiling instead of leaving it quietly out of date.
 *
 * Fixed seed: every run measures the same rows.
 *
 * @suppress MagicNumber, LongMethod — this is invented data, and its figures are the point: one
 * constant per amount would hide the ledger behind its own names, and splitting [build] would
 * scatter one month's movements over several functions.
 */
@Suppress("MagicNumber", "LongMethod")
internal object BenchmarkLedger {
    private const val MONTHS = 24
    private const val SEED = 42
    private const val EUR = "EUR"

    private const val BANK = 1L
    private const val CASH = 2L

    private const val SALARY = 1L
    private const val RENT = 2L
    private const val GROCERIES = 3L
    private const val EATING_OUT = 4L
    private const val TRANSPORT = 5L
    private const val UTILITIES = 6L
    private const val SUBSCRIPTIONS = 7L
    private const val LEISURE = 8L
    private const val HEALTH = 9L

    private const val RENT_RULE = 1L
    private const val STREAMING_RULE = 2L
    private const val TRIP = 1L

    fun build(today: LocalDate): Backup {
        val start = today.withDayOfMonth(1).minusMonths(MONTHS.toLong() - 1)
        val random = Random(SEED)
        val transactions = mutableListOf<BackupTransaction>()

        fun add(
            accountId: Long,
            categoryId: Long,
            kind: TransactionKind,
            amountMinor: Long,
            date: LocalDate,
            note: String? = null,
            ruleId: Long? = null,
            groupId: Long? = null,
        ) {
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
                    groupId = groupId,
                )
        }

        var month = start
        while (!month.isAfter(today)) {
            // What the two rules wrote, on the first of each month.
            add(BANK, RENT, TransactionKind.EXPENSE, 85_000, month, "Rent", ruleId = RENT_RULE)
            add(BANK, SUBSCRIPTIONS, TransactionKind.EXPENSE, 1_299, month, "Streaming", ruleId = STREAMING_RULE)

            val payday = month.withDayOfMonth(minOf(28, month.lengthOfMonth()))
            if (!payday.isAfter(today)) add(BANK, SALARY, TransactionKind.INCOME, 210_000, payday, "Salary")

            var day = month
            while (day.month == month.month && !day.isAfter(today)) {
                if (random.nextInt(3) == 0) {
                    add(BANK, GROCERIES, TransactionKind.EXPENSE, random.nextLong(1_500, 9_000), day, "Supermarket")
                }
                if (random.nextInt(5) == 0) {
                    add(CASH, EATING_OUT, TransactionKind.EXPENSE, random.nextLong(800, 4_500), day, "Lunch")
                }
                if (random.nextInt(6) == 0) {
                    add(BANK, TRANSPORT, TransactionKind.EXPENSE, random.nextLong(200, 6_000), day)
                }
                if (random.nextInt(14) == 0) {
                    add(BANK, LEISURE, TransactionKind.EXPENSE, random.nextLong(1_000, 12_000), day, "Cinema")
                }
                day = day.plusDays(1)
            }
            add(
                BANK,
                UTILITIES,
                TransactionKind.EXPENSE,
                random.nextLong(4_000, 11_000),
                month.plusDays(9),
                "Electricity",
            )
            if (random.nextInt(4) == 0) {
                add(
                    BANK,
                    HEALTH,
                    TransactionKind.EXPENSE,
                    random.nextLong(1_500, 8_000),
                    month.plusDays(15),
                    "Pharmacy",
                )
            }
            month = month.plusMonths(1)
        }
        // A trip two months back, filed under a group.
        val tripStart = today.minusMonths(2).withDayOfMonth(10)
        repeat(5) {
            add(
                CASH,
                EATING_OUT,
                TransactionKind.EXPENSE,
                3_500,
                tripStart.plusDays(it.toLong()),
                "Trip",
                groupId = TRIP,
            )
        }

        return Backup(
            schemaVersion = CentsDatabase.VERSION,
            exportedAt = Instant.EPOCH,
            accounts =
                listOf(
                    account(BANK, "Bank", AccountType.BANK, 250_000, 0xFF1E88E5.toInt(), 0),
                    account(CASH, "Cash", AccountType.CASH, 15_000, 0xFF43A047.toInt(), 1),
                ),
            categories =
                listOf(
                    category(SALARY, "Salary", CategoryKind.INCOME, 0xFF2E7D32.toInt(), 0),
                    category(RENT, "Rent", CategoryKind.EXPENSE, 0xFF6D4C41.toInt(), 1),
                    category(GROCERIES, "Groceries", CategoryKind.EXPENSE, 0xFF43A047.toInt(), 2),
                    category(EATING_OUT, "Eating out", CategoryKind.EXPENSE, 0xFFFB8C00.toInt(), 3),
                    category(TRANSPORT, "Transport", CategoryKind.EXPENSE, 0xFF1E88E5.toInt(), 4),
                    category(UTILITIES, "Utilities", CategoryKind.EXPENSE, 0xFF8E24AA.toInt(), 5),
                    category(SUBSCRIPTIONS, "Subscriptions", CategoryKind.EXPENSE, 0xFFE53935.toInt(), 6),
                    category(LEISURE, "Leisure", CategoryKind.EXPENSE, 0xFF00ACC1.toInt(), 7),
                    category(HEALTH, "Health", CategoryKind.EXPENSE, 0xFFD81B60.toInt(), 8),
                ),
            transactions = transactions,
            holdings = emptyList(),
            trades = emptyList(),
            quotes = emptyList(),
            rules =
                listOf(
                    rule(RENT_RULE, "Rent", RENT, 85_000, start, today),
                    rule(STREAMING_RULE, "Streaming", SUBSCRIPTIONS, 1_299, start, today),
                ),
            budgets =
                listOf(
                    BackupBudget(categoryId = GROCERIES, amountMinor = 40_000, currency = EUR),
                    BackupBudget(categoryId = EATING_OUT, amountMinor = 15_000, currency = EUR),
                ),
            groups =
                listOf(
                    BackupGroup(
                        id = TRIP,
                        name = "Trip",
                        startDate = tripStart.toEpochDay(),
                        endDate = tripStart.plusDays(4).toEpochDay(),
                    ),
                ),
        )
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

    /** Caught up to this month, so opening the app writes nothing on top of the ledger. */
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
        accountId = BANK,
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
        lastGenerated = today.withDayOfMonth(1).toEpochDay(),
    )
}
