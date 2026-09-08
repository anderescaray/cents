package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.ComparisonSegment
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.previous
import io.github.anderescaray.cents.domain.model.samePeriodLastYear
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/** One test class per report repository, the same way there is one file per view. */
@RunWith(RobolectricTestRunner::class)
class RoomPeriodComparisonRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var repository: RoomPeriodComparisonRepository

    @Before
    fun setUp() {
        database = createTestDatabase()
        // Standing after August has ended, so the periods these tests name are compared
        // whole. The one test about a running period installs its own clock.
        repository = RoomPeriodComparisonRepository(database.transactionDao(), afterAugust)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the comparison is against the month before, not the last thirty days`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    // July, the period being compared against.
                    transaction(accountId, amountMinor = 40_000, date = LocalDate.of(2026, 7, 10)),
                    transaction(
                        accountId,
                        kind = TransactionKind.INCOME,
                        amountMinor = 200_000,
                        date = LocalDate.of(2026, 7, 1),
                    ),
                    // August, the period selected.
                    transaction(accountId, amountMinor = 25_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(
                        accountId,
                        kind = TransactionKind.INCOME,
                        amountMinor = 210_000,
                        date = LocalDate.of(2026, 8, 1),
                    ),
                    // June, which belongs to neither.
                    transaction(accountId, amountMinor = 999_000, date = LocalDate.of(2026, 6, 30)),
                ),
            )

            val comparison = repository.observePeriodComparison(scope(august)).first()

            assertEquals(Money(210_000, "EUR"), comparison.current.income)
            assertEquals(Money(25_000, "EUR"), comparison.current.expenses)
            assertEquals(Money(200_000, "EUR"), comparison.before.income)
            assertEquals(Money(40_000, "EUR"), comparison.before.expenses)
            // Spending fell and earnings rose, which is the whole question the view asks.
            assertEquals(Money(-15_000, "EUR"), comparison.expensesChange)
            assertEquals(Money(10_000, "EUR"), comparison.incomeChange)
            assertEquals(Money(25_000, "EUR"), comparison.netChange)
        }

    @Test
    fun `only the chosen account is compared, on both sides`() =
        runTest {
            val current = database.accountDao().insert(account(name = "Current"))
            val savings = database.accountDao().insert(account(name = "Savings"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(current, amountMinor = 10_000, date = LocalDate.of(2026, 7, 10)),
                    transaction(savings, amountMinor = 70_000, date = LocalDate.of(2026, 7, 10)),
                    transaction(current, amountMinor = 20_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(savings, amountMinor = 80_000, date = LocalDate.of(2026, 8, 4)),
                ),
            )

            val comparison = repository.observePeriodComparison(scope(august, current)).first()

            // Narrowing one side and not the other would compare one account against every
            // account, which is a chart that looks fine and says something false.
            assertEquals(Money(20_000, "EUR"), comparison.current.expenses)
            assertEquals(Money(10_000, "EUR"), comparison.before.expenses)
        }

    @Test
    fun `two empty periods are nothing to compare, but one empty period is an answer`() =
        runTest {
            val accountId = database.accountDao().insert(account())

            val nothing = repository.observePeriodComparison(scope(august)).first()
            assertTrue(nothing.isEmpty)

            database.transactionDao().insert(
                transaction(accountId, amountMinor = 25_000, date = LocalDate.of(2026, 8, 4)),
            )

            val spentThisMonthOnly = repository.observePeriodComparison(scope(august)).first()

            // "You spent nothing last month" is exactly what someone opens this view to see,
            // so it is a real comparison rather than an empty state.
            assertFalse(spentThisMonthOnly.isEmpty)
            assertEquals(Money.zero("EUR"), spentThisMonthOnly.before.expenses)
            assertEquals(Money(25_000, "EUR"), spentThisMonthOnly.expensesChange)
        }

    @Test
    fun `the previous period keeps the shape of the one it follows`() {
        // A calendar shape stays that shape: February's predecessor is January, whatever
        // their lengths, and subtracting 28 days would produce a range that is neither.
        assertEquals(
            ReportPeriod.Month(YearMonth.of(2026, 1)),
            ReportPeriod.Month(YearMonth.of(2026, 2)).previous(),
        )
        assertEquals(ReportPeriod.Year(2025), ReportPeriod.Year(2026).previous())

        val week = ReportPeriod.Week(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23))
        assertEquals(
            ReportPeriod.Week(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 16)),
            week.previous(),
        )

        // A quarter is contiguous with the one before it, so its predecessor ends the day
        // before it starts rather than being worked out from a length.
        val quarter = ReportPeriod.Quarter(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30))
        assertEquals(
            ReportPeriod.Quarter(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)),
            quarter.previous(),
        )
    }

    @Test
    fun `a custom range is compared against the same number of days ending the day before`() {
        val tenDays = ReportPeriod.Custom(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 19))

        val before = tenDays.previous()

        // Ten days, ending the day before the range starts — no overlap, and no gap either.
        assertEquals(ReportPeriod.Custom(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 9)), before)
    }

    @Test
    fun `the previous column opens the movements behind it, in the chosen account`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insert(
                transaction(accountId, amountMinor = 10_000, date = LocalDate.of(2026, 7, 10)),
            )

            val comparison = repository.observePeriodComparison(scope(august, accountId)).first()
            val filter = comparison.filterForPrevious()

            assertEquals(LocalDate.of(2026, 7, 1), filter.from)
            assertEquals(LocalDate.of(2026, 7, 31), filter.to)
            // The account travels with it: the comparison was narrowed, so the list it opens
            // has to be narrowed the same way or it answers a wider question than was asked.
            assertEquals(accountId, filter.accountId)
        }

    @Test
    fun `a period that has not finished is compared against the same stretch, not the whole`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    // The whole of July, of which only the first three days are comparable.
                    transaction(accountId, amountMinor = 30_000, date = LocalDate.of(2026, 7, 2)),
                    transaction(accountId, amountMinor = 170_000, date = LocalDate.of(2026, 7, 20)),
                    // August so far.
                    transaction(accountId, amountMinor = 25_000, date = LocalDate.of(2026, 8, 2)),
                ),
            )
            val onTheThird = RoomPeriodComparisonRepository(database.transactionDao(), august3)

            val comparison = onTheThird.observePeriodComparison(scope(august)).first()

            // Against the whole of July this would read as spending down 87%, on the third of
            // the month, and it would say that for most of every month. Against the same three
            // days it reads as slightly down, which is the truth.
            assertTrue(comparison.ranges.partial)
            assertEquals(LocalDate.of(2026, 8, 3), comparison.ranges.current.to)
            assertEquals(LocalDate.of(2026, 7, 1), comparison.ranges.previous.from)
            assertEquals(LocalDate.of(2026, 7, 3), comparison.ranges.previous.to)
            assertEquals(Money(25_000, "EUR"), comparison.current.expenses)
            assertEquals(Money(30_000, "EUR"), comparison.before.expenses)
        }

    @Test
    fun `a period that has ended is compared whole, and says it was not clamped`() =
        runTest {
            database.accountDao().insert(account())

            val comparison = repository.observePeriodComparison(scope(august)).first()

            assertFalse(comparison.ranges.partial)
            assertEquals(LocalDate.of(2026, 8, 31), comparison.ranges.current.to)
            assertEquals(LocalDate.of(2026, 7, 31), comparison.ranges.previous.to)
        }

    @Test
    fun `a running period's drill-down opens the days compared, not the whole month`() =
        runTest {
            database.accountDao().insert(account())
            val onTheThird = RoomPeriodComparisonRepository(database.transactionDao(), august3)

            val comparison = onTheThird.observePeriodComparison(scope(august)).first()

            // A tap under a column that added up three days must not open thirty-one.
            assertEquals(LocalDate.of(2026, 8, 3), comparison.filterForCurrent().to)
            assertEquals(LocalDate.of(2026, 7, 3), comparison.filterForPrevious().to)
        }

    @Test
    fun `the year-ago comparison is against the same month last year`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    // August last year, the period being compared against.
                    transaction(accountId, amountMinor = 30_000, date = LocalDate.of(2025, 8, 10)),
                    transaction(
                        accountId,
                        kind = TransactionKind.INCOME,
                        amountMinor = 190_000,
                        date = LocalDate.of(2025, 8, 1),
                    ),
                    // July this year (month before), which belongs to previous period, not year-ago.
                    transaction(accountId, amountMinor = 40_000, date = LocalDate.of(2026, 7, 10)),
                    // August this year, the period selected.
                    transaction(accountId, amountMinor = 25_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(
                        accountId,
                        kind = TransactionKind.INCOME,
                        amountMinor = 210_000,
                        date = LocalDate.of(2026, 8, 1),
                    ),
                ),
            )

            val comparison =
                repository
                    .observePeriodComparison(scope(august), ComparisonSegment.SAME_PERIOD_LAST_YEAR)
                    .first()

            assertEquals(Money(210_000, "EUR"), comparison.current.income)
            assertEquals(Money(25_000, "EUR"), comparison.current.expenses)
            assertEquals(Money(190_000, "EUR"), comparison.before.income)
            assertEquals(Money(30_000, "EUR"), comparison.before.expenses)
            assertEquals(Money(-5_000, "EUR"), comparison.expensesChange)
            assertEquals(Money(20_000, "EUR"), comparison.incomeChange)
            assertEquals(Money(25_000, "EUR"), comparison.netChange)
            assertEquals(ComparisonSegment.SAME_PERIOD_LAST_YEAR, comparison.segment)
            assertEquals(ReportPeriod.Month(YearMonth.of(2025, 8)), comparison.previous)
        }

    @Test
    fun `the same period last year keeps calendar shapes`() {
        // A calendar shape stays that shape: February's year-ago counterpart is February
        // of last year, even across leap years (2024 to 2023).
        assertEquals(
            ReportPeriod.Month(YearMonth.of(2023, 2)),
            ReportPeriod.Month(YearMonth.of(2024, 2)).samePeriodLastYear(),
        )
        assertEquals(ReportPeriod.Year(2025), ReportPeriod.Year(2026).samePeriodLastYear())

        // A week is shifted by 52 weeks (364 days) to land on the same weekdays.
        val week = ReportPeriod.Week(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23))
        assertEquals(
            ReportPeriod.Week(LocalDate.of(2025, 8, 18), LocalDate.of(2025, 8, 24)),
            week.samePeriodLastYear(),
        )

        // A quarter is shifted by 1 year, preserving quarter boundaries.
        val quarter = ReportPeriod.Quarter(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30))
        assertEquals(
            ReportPeriod.Quarter(LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30)),
            quarter.samePeriodLastYear(),
        )

        // A custom range shifts both dates by 1 year.
        val custom = ReportPeriod.Custom(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 19))
        assertEquals(
            ReportPeriod.Custom(LocalDate.of(2025, 8, 10), LocalDate.of(2025, 8, 19)),
            custom.samePeriodLastYear(),
        )
    }

    @Test
    fun `a running period compared with last year is clamped on both sides`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    // August last year: within the first 3 days vs later in the month.
                    transaction(accountId, amountMinor = 20_000, date = LocalDate.of(2025, 8, 2)),
                    transaction(accountId, amountMinor = 150_000, date = LocalDate.of(2025, 8, 20)),
                    // August this year so far (standing on August 3).
                    transaction(accountId, amountMinor = 25_000, date = LocalDate.of(2026, 8, 2)),
                ),
            )
            val onTheThird = RoomPeriodComparisonRepository(database.transactionDao(), august3)

            val comparison =
                onTheThird
                    .observePeriodComparison(scope(august), ComparisonSegment.SAME_PERIOD_LAST_YEAR)
                    .first()

            assertTrue(comparison.ranges.partial)
            assertEquals(LocalDate.of(2026, 8, 3), comparison.ranges.current.to)
            assertEquals(LocalDate.of(2025, 8, 1), comparison.ranges.previous.from)
            assertEquals(LocalDate.of(2025, 8, 3), comparison.ranges.previous.to)
            assertEquals(Money(25_000, "EUR"), comparison.current.expenses)
            assertEquals(Money(20_000, "EUR"), comparison.before.expenses)
            // Drill down opens the clamped range of last year.
            assertEquals(LocalDate.of(2025, 8, 3), comparison.filterForPrevious().to)
        }

    @Test
    fun `the year-ago column opens the movements behind it, in the chosen account`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insert(
                transaction(accountId, amountMinor = 10_000, date = LocalDate.of(2025, 8, 10)),
            )

            val comparison =
                repository
                    .observePeriodComparison(scope(august, accountId), ComparisonSegment.SAME_PERIOD_LAST_YEAR)
                    .first()
            val filter = comparison.filterForPrevious()

            assertEquals(LocalDate.of(2025, 8, 1), filter.from)
            assertEquals(LocalDate.of(2025, 8, 31), filter.to)
            assertEquals(accountId, filter.accountId)
        }

    private val august = ReportPeriod.Month(YearMonth.of(2026, 8))

    /** The third of August, mid-period, which is the case the clamping exists for. */
    private val august3 = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneOffset.UTC)

    /** After August has ended, so a whole month is compared against a whole month. */
    private val afterAugust = Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC)

    private fun scope(
        period: ReportPeriod,
        accountId: Long? = null,
    ) = ReportScope(period, "EUR", accountId)
}
