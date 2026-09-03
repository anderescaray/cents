package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.lastBuckets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class RoomCategoryTrendRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var repository: RoomCategoryTrendRepository

    @Before
    fun setUp() {
        database = createTestDatabase()
        // Standing after August has ended, so the buckets these tests name are whole. The
        // tests about a running period install their own clock.
        repository = RoomCategoryTrendRepository(database.transactionDao(), afterAugust)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the line is twelve buckets ending on the selected period`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, food, amountMinor = 5_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(accountId, food, amountMinor = 3_000, date = LocalDate.of(2026, 7, 4)),
                    // Thirteen months back: outside the twelve the line covers.
                    transaction(accountId, food, amountMinor = 99_000, date = LocalDate.of(2025, 7, 4)),
                ),
            )

            val trend = repository.observeCategoryTrend(scope(august), null).first()

            assertEquals(BUCKETS, trend.buckets.size)
            assertEquals(
                YearMonth.of(2025, 9).atDay(1),
                trend.buckets
                    .first()
                    .whole.from,
            )
            assertEquals(
                YearMonth.of(2026, 8).atEndOfMonth(),
                trend.buckets
                    .last()
                    .whole.to,
            )
            assertEquals(Money(5_000, "EUR"), trend.buckets.last().total)
            // The 990,00 in July 2025 is before the first bucket and must not appear anywhere.
            assertTrue(trend.buckets.none { it.total == Money(99_000, "EUR") })
        }

    @Test
    fun `an empty bucket is a zero, not a missing point`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insert(
                transaction(accountId, food, amountMinor = 5_000, date = LocalDate.of(2026, 8, 4)),
            )

            val trend = repository.observeCategoryTrend(scope(august), null).first()

            // A gap in a line reads as data that was not collected; a zero reads as a quiet
            // month, and only one of those is true.
            assertEquals(BUCKETS, trend.buckets.size)
            assertEquals(BUCKETS - 1, trend.buckets.count { it.total.isZero })
        }

    @Test
    fun `it opens on the category most is spent on, and can be pointed at another`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val rent = database.categoryDao().insert(category(name = "Rent"))
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, rent, amountMinor = 800_000, date = LocalDate.of(2026, 8, 1)),
                    transaction(accountId, food, amountMinor = 5_000, date = LocalDate.of(2026, 8, 4)),
                ),
            )

            val opened = repository.observeCategoryTrend(scope(august), null).first()
            val pointed = repository.observeCategoryTrend(scope(august), food).first()

            // The thing most worth watching is usually the thing most is spent on.
            assertEquals("Rent", opened.selected?.name)
            assertEquals(listOf("Rent", "Restaurants"), opened.choices.map { it.name })
            assertEquals("Restaurants", pointed.selected?.name)
            assertEquals(Money(5_000, "EUR"), pointed.buckets.last().total)
        }

    @Test
    fun `a category with nothing in the span falls back rather than drawing an empty line`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            val unused = database.categoryDao().insert(category(name = "Holidays"))
            database.transactionDao().insert(
                transaction(accountId, food, amountMinor = 5_000, date = LocalDate.of(2026, 8, 4)),
            )

            val trend = repository.observeCategoryTrend(scope(august), unused).first()

            // Watching a category, then narrowing to an account it was never used in, should
            // show something rather than a flat line under the wrong name.
            assertEquals("Restaurants", trend.selected?.name)
        }

    @Test
    fun `nothing at all is an empty state rather than a category with no name`() =
        runTest {
            database.accountDao().insert(account())

            val trend = repository.observeCategoryTrend(scope(august), null).first()

            assertNull(trend.selected)
            assertTrue(trend.isEmpty)
            assertTrue(trend.choices.isEmpty())
        }

    @Test
    fun `uncategorized money is not a category to follow`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insert(
                transaction(accountId, categoryId = null, amountMinor = 5_000, date = LocalDate.of(2026, 8, 4)),
            )

            val trend = repository.observeCategoryTrend(scope(august), null).first()

            // "Uncategorized" is the absence of a label, so a line following it over a year
            // would be following several unrelated things at once.
            assertTrue(trend.choices.isEmpty())
            assertNull(trend.selected)
        }

    @Test
    fun `only the chosen account counts, in every bucket`() =
        runTest {
            val current = database.accountDao().insert(account(name = "Current"))
            val savings = database.accountDao().insert(account(name = "Savings"))
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(current, food, amountMinor = 5_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(savings, food, amountMinor = 60_000, date = LocalDate.of(2026, 8, 5)),
                    transaction(savings, food, amountMinor = 70_000, date = LocalDate.of(2026, 7, 5)),
                ),
            )

            val trend = repository.observeCategoryTrend(scope(august, current), null).first()

            assertEquals(Money(5_000, "EUR"), trend.buckets.last().total)
            assertTrue(trend.buckets.dropLast(1).all { it.total.isZero })
        }

    @Test
    fun `a point opens that bucket's movements, in that category and that account`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insert(
                transaction(accountId, food, amountMinor = 5_000, date = LocalDate.of(2026, 7, 4)),
            )

            val trend = repository.observeCategoryTrend(scope(august, accountId), null).first()
            val july = trend.buckets[BUCKETS - 2]
            val filter = requireNotNull(trend.filterFor(july))

            // The bucket's own month, not the selected one: a point three months back has to
            // open that month.
            assertEquals(LocalDate.of(2026, 7, 1), filter.from)
            assertEquals(LocalDate.of(2026, 7, 31), filter.to)
            assertEquals(CategoryFilter.One(food), filter.category)
            assertEquals(accountId, filter.accountId)
        }

    @Test
    fun `the change is measured against the average of the earlier buckets`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, food, amountMinor = 22_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(accountId, food, amountMinor = 11_000, date = LocalDate.of(2026, 7, 4)),
                ),
            )

            val trend = repository.observeCategoryTrend(scope(august), null).first()

            // Eleven earlier buckets holding 110,00 between them average to 10,00, and this
            // month's 220,00 is 210,00 above that. Measured against the single previous month
            // one quiet month would make the next look like a spike.
            assertEquals(Money(1_000, "EUR"), trend.averageBefore)
            assertEquals(Money(21_000, "EUR"), trend.change)
        }

    @Test
    fun `buckets keep the calendar shape of the period selected`() {
        val months = ReportPeriod.Month(YearMonth.of(2026, 3)).lastBuckets(3)
        assertEquals(
            listOf(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
            months.map { YearMonth.from(it.from) },
        )

        // A week selected means weeks, not a year of months: the period picks the bucket.
        val weeks = ReportPeriod.Week(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23)).lastBuckets(3)
        assertEquals(LocalDate.of(2026, 8, 3), weeks.first().from)
        assertEquals(LocalDate.of(2026, 8, 23), weeks.last().to)
    }

    @Test
    fun `income and expenses are not added together, and a category is summed its own way`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val salary =
                database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))
            val rent = database.categoryDao().insert(category(name = "Rent"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(
                        accountId,
                        salary,
                        kind = TransactionKind.INCOME,
                        amountMinor = 300_000,
                        date = LocalDate.of(2026, 8, 1),
                    ),
                    transaction(accountId, rent, amountMinor = 80_000, date = LocalDate.of(2026, 8, 1)),
                ),
            )

            val trend = repository.observeCategoryTrend(scope(august), null).first()

            // Both are summed in their own direction, so both are positive and the larger one
            // leads. What must not happen is the two being treated as the same shape by
            // accident — an amount is always positive and the kind carries the sign.
            assertEquals(listOf("Salary", "Rent"), trend.choices.map { it.name })
            assertEquals(Money(300_000, "EUR"), trend.buckets.last().total)
        }

    @Test
    fun `a refund filed under a category is netted against it, not added to it`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, food, amountMinor = 9_000, date = LocalDate.of(2026, 8, 4)),
                    transaction(
                        accountId,
                        food,
                        kind = TransactionKind.INCOME,
                        amountMinor = 2_000,
                        date = LocalDate.of(2026, 8, 6),
                    ),
                ),
            )

            val trend = repository.observeCategoryTrend(scope(august), null).first()

            // 90,00 out and 20,00 back is 70,00 spent. Added together it would read as 110,00,
            // which is more than ever left the account.
            assertEquals(Money(7_000, "EUR"), trend.buckets.last().total)
        }

    @Test
    fun `a period that has not finished cuts every bucket to the same elapsed days`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insertAll(
                listOf(
                    // July: 30,00 in the first three days, 400,00 in the rest of the month.
                    transaction(accountId, food, amountMinor = 3_000, date = LocalDate.of(2026, 7, 2)),
                    transaction(accountId, food, amountMinor = 40_000, date = LocalDate.of(2026, 7, 20)),
                    // August so far.
                    transaction(accountId, food, amountMinor = 2_000, date = LocalDate.of(2026, 8, 2)),
                ),
            )
            val onTheThird = RoomCategoryTrendRepository(database.transactionDao(), august3)

            val trend = onTheThird.observeCategoryTrend(scope(august), null).first()
            val july = trend.buckets[BUCKETS - 2]

            // Against whole months this month would look like a collapse in spending, and
            // would look that way for most of every month — the same failure the period
            // comparison was fixed for, one section up the same screen.
            assertTrue(trend.partial)
            assertEquals(Money(3_000, "EUR"), july.total)
            assertEquals(LocalDate.of(2026, 7, 3), july.counted.to)
            // The bucket still knows what it is, so the axis can still say "Jul".
            assertEquals(LocalDate.of(2026, 7, 31), july.whole.to)
            assertEquals(Money(2_000, "EUR"), trend.buckets.last().total)
        }

    @Test
    fun `a point of a running line opens the days it counted, not the whole month`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val food = database.categoryDao().insert(category(name = "Restaurants"))
            database.transactionDao().insert(
                transaction(accountId, food, amountMinor = 2_000, date = LocalDate.of(2026, 8, 2)),
            )
            val onTheThird = RoomCategoryTrendRepository(database.transactionDao(), august3)

            val trend = onTheThird.observeCategoryTrend(scope(august), null).first()
            val filter = requireNotNull(trend.filterFor(trend.buckets[BUCKETS - 2]))

            assertEquals(LocalDate.of(2026, 7, 1), filter.from)
            assertEquals(LocalDate.of(2026, 7, 3), filter.to)
        }

    @Test
    fun `a finished period is not cut, and says it was not`() =
        runTest {
            database.accountDao().insert(account())

            val trend = repository.observeCategoryTrend(scope(august), null).first()

            assertFalse(trend.partial)
        }

    private val august = ReportPeriod.Month(YearMonth.of(2026, 8))

    /** The third of August, mid-period, which is what the cutting exists for. */
    private val august3 = Clock.fixed(Instant.parse("2026-08-03T10:00:00Z"), ZoneOffset.UTC)

    /** After August has ended, so every bucket is a whole month. */
    private val afterAugust = Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC)

    private fun scope(
        period: ReportPeriod,
        accountId: Long? = null,
    ) = ReportScope(period, "EUR", accountId)

    private companion object {
        const val BUCKETS = 12
    }
}
