package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.TransactionKind
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** One test class per report repository, the same way there is one file per view. */
@RunWith(RobolectricTestRunner::class)
class RoomSpendingByWeekdayRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var repository: RoomSpendingByWeekdayRepository

    @Before
    fun setUp() {
        database = createTestDatabase()
        repository = RoomSpendingByWeekdayRepository(database.transactionDao())
        // The Mondays these tests use really are Mondays.
        assertEquals(DayOfWeek.MONDAY, MONDAY.dayOfWeek)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `each weekday is summed across the period and the quiet ones come back as zero`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, amountMinor = 1_000, date = MONDAY),
                    // A second Monday, one week earlier: the same bucket, added to.
                    transaction(accountId, amountMinor = 800, date = MONDAY.minusWeeks(1)),
                    transaction(accountId, amountMinor = 3_000, date = MONDAY.plusDays(2)),
                    transaction(accountId, amountMinor = 5_000, date = MONDAY.plusDays(5)),
                ),
            )

            val spending = repository.observeSpendingByWeekday(scope(august)).first()

            // The full week, Monday first, with Tuesday and the rest present as zeros.
            assertEquals(DayOfWeek.entries.toList(), spending.days.map { it.weekday })
            assertEquals(Money(1_800, "EUR"), spending.on(DayOfWeek.MONDAY).total)
            assertEquals(Money.zero("EUR"), spending.on(DayOfWeek.TUESDAY).total)
            assertEquals(Money(3_000, "EUR"), spending.on(DayOfWeek.WEDNESDAY).total)
            assertEquals(Money(5_000, "EUR"), spending.on(DayOfWeek.SATURDAY).total)
            assertEquals(Money(9_800, "EUR"), spending.total)
        }

    @Test
    fun `Sunday lands on its own bucket rather than folding onto Monday`() =
        runTest {
            // SQLite's strftime counts weekdays from Sunday 0; the query rotates that onto the
            // ISO numbering, and this is the case that would break if it did not.
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insert(
                transaction(accountId, amountMinor = 4_000, date = MONDAY.plusDays(6)),
            )

            val spending = repository.observeSpendingByWeekday(scope(august)).first()

            assertEquals(Money(4_000, "EUR"), spending.on(DayOfWeek.SUNDAY).total)
            assertTrue(spending.on(DayOfWeek.MONDAY).total.isZero)
        }

    @Test
    fun `the weekend is Saturday and Sunday, weighed against the other five`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, amountMinor = 10_000, date = MONDAY.plusDays(5)),
                    transaction(accountId, amountMinor = 12_000, date = MONDAY.plusDays(6)),
                    transaction(accountId, amountMinor = 3_000, date = MONDAY),
                ),
            )

            val spending = repository.observeSpendingByWeekday(scope(august)).first()

            assertEquals(Money(22_000, "EUR"), spending.weekendTotal)
            assertEquals(Money(3_000, "EUR"), spending.weekdayTotal)
        }

    @Test
    fun `income and movements outside the period are left out`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, amountMinor = 2_000, date = MONDAY),
                    transaction(
                        accountId,
                        kind = TransactionKind.INCOME,
                        amountMinor = 200_000,
                        date = MONDAY,
                    ),
                    // A Monday in July, before the period.
                    transaction(accountId, amountMinor = 99_000, date = LocalDate.of(2026, 7, 6)),
                ),
            )

            val spending = repository.observeSpendingByWeekday(scope(august)).first()

            assertEquals(Money(2_000, "EUR"), spending.on(DayOfWeek.MONDAY).total)
            assertEquals(Money(2_000, "EUR"), spending.total)
        }

    @Test
    fun `only the chosen account is counted`() =
        runTest {
            val current = database.accountDao().insert(account(name = "Current"))
            val savings = database.accountDao().insert(account(name = "Savings"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(current, amountMinor = 3_000, date = MONDAY),
                    transaction(savings, amountMinor = 90_000, date = MONDAY),
                ),
            )

            val everything = repository.observeSpendingByWeekday(scope(august)).first()
            val justCurrent = repository.observeSpendingByWeekday(scope(august, current)).first()

            assertEquals(Money(93_000, "EUR"), everything.on(DayOfWeek.MONDAY).total)
            assertEquals(Money(3_000, "EUR"), justCurrent.on(DayOfWeek.MONDAY).total)
        }

    @Test
    fun `a bar opens the movements recorded on that weekday, in the chosen account`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insert(
                transaction(accountId, amountMinor = 5_000, date = MONDAY.plusDays(5)),
            )

            val spending = repository.observeSpendingByWeekday(scope(august, accountId)).first()
            val filter = spending.filterFor(DayOfWeek.SATURDAY)

            assertEquals(DayOfWeek.SATURDAY, filter.weekday)
            assertEquals(TransactionKind.EXPENSE, filter.kind)
            assertEquals(LocalDate.of(2026, 8, 1), filter.from)
            assertEquals(LocalDate.of(2026, 8, 31), filter.to)
            // The account travels with the drill-down, or a bar that added up one account
            // would open every account's movements.
            assertEquals(accountId, filter.accountId)
        }

    @Test
    fun `an empty period is empty, and still seven days wide`() =
        runTest {
            database.accountDao().insert(account())

            val spending = repository.observeSpendingByWeekday(scope(august)).first()

            assertTrue(spending.isEmpty)
            assertFalse(spending.days.isEmpty())
            assertEquals(WEEK_LENGTH, spending.days.size)
        }

    private val august = ReportPeriod.Month(YearMonth.of(2026, 8))

    private fun scope(
        period: ReportPeriod,
        accountId: Long? = null,
    ) = ReportScope(period, "EUR", accountId)

    private companion object {
        /** 17 August 2026 is a Monday; every dated movement here is offset from it. */
        val MONDAY: LocalDate = LocalDate.of(2026, 8, 17)
        const val WEEK_LENGTH = 7
    }
}
