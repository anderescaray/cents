package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.BalanceOverTime
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
import java.time.LocalDate
import java.time.YearMonth

/**
 * The cumulative arithmetic behind the balance line.
 *
 * Two things are being pinned down here, and they are different. One is that the running
 * total is right — that a day with nothing in it repeats yesterday rather than dropping to
 * zero, that income and expense on the same day net into one point, that the ends of the
 * period are inside it. The other is **where the line starts**, which is a decision rather
 * than an implementation detail: it starts from the money carried into the period, and the
 * test that says so is `the closing balance is the number the Accounts tab shows` — if that
 * one is ever deleted, the decision has quietly been reversed.
 */
@RunWith(RobolectricTestRunner::class)
class RoomBalanceOverTimeRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var repository: RoomBalanceOverTimeRepository

    @Before
    fun setUp() {
        database = createTestDatabase()
        repository = RoomBalanceOverTimeRepository(database.transactionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the line carries the running total forward, one point per day of the period`() =
        runTest {
            val accountId = database.accountDao().insert(account(openingBalanceMinor = 100_000))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, amountMinor = 10_000, date = august(2)),
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 30_000, date = august(4)),
                ),
            )

            val line = repository.observeBalanceOverTime(scope(week(1, 5))).first()

            // Five days, five points: the flat stretches are what a balance does between
            // movements, and a chart missing them would draw a slope that never happened.
            assertEquals(FIVE_DAYS, line.points.size)
            assertEquals(
                listOf(100_000L, 90_000L, 90_000L, 120_000L, 120_000L),
                line.points.map { it.balance.minorUnits },
            )
            assertEquals((1..FIVE_DAYS).map(::august), line.points.map { it.date })
        }

    @Test
    fun `the line starts from the opening balances plus everything recorded before the period`() =
        runTest {
            val accountId = database.accountDao().insert(account(openingBalanceMinor = 50_000))
            database.transactionDao().insertAll(
                listOf(
                    // July: before the period, so it belongs to where the line starts and
                    // must not appear as a step inside it.
                    transaction(accountId, amountMinor = 20_000, date = LocalDate.of(2026, 7, 30)),
                    transaction(accountId, amountMinor = 5_000, date = august(3)),
                ),
            )

            val line = repository.observeBalanceOverTime(scope(week(1, 5))).first()

            assertEquals(Money(30_000, "EUR"), line.opening)
            assertEquals(Money(30_000, "EUR"), line.points.first().balance)
            assertEquals(Money(25_000, "EUR"), line.closing)
            assertEquals(Money(-5_000, "EUR"), line.change)
        }

    @Test
    fun `the closing balance is the number the Accounts tab shows`() =
        runTest {
            // The whole argument for not starting at zero. Two accounts, one of them opened
            // in the red for money owed on a card, and history on both sides of the period.
            val checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 250_000))
            val card = database.accountDao().insert(account(name = "Card", openingBalanceMinor = -40_000))
            database.transactionDao().insertAll(
                listOf(
                    transaction(checking, kind = TransactionKind.INCOME, amountMinor = 180_000, date = july(15)),
                    transaction(card, amountMinor = 12_500, date = july(20)),
                    transaction(checking, amountMinor = 60_000, date = august(3)),
                    transaction(card, amountMinor = 7_500, date = august(28)),
                ),
            )

            val line = repository.observeBalanceOverTime(scope(ReportPeriod.Month(YearMonth.of(2026, 8)))).first()
            val accountsTabTotal =
                database
                    .accountDao()
                    .observeBalances()
                    .first()
                    .sumOf { it.balanceMinor }

            assertEquals(accountsTabTotal, line.closing.minorUnits)
        }

    @Test
    fun `one account's line starts from that account's opening balance, not everyone's`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 250_000))
            val savings = database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 900_000))
            database.transactionDao().insertAll(
                listOf(
                    transaction(checking, amountMinor = 20_000, date = july(15)),
                    transaction(savings, amountMinor = 500_000, date = july(15)),
                    transaction(checking, amountMinor = 30_000, date = august(2)),
                    transaction(savings, amountMinor = 100_000, date = august(3)),
                ),
            )

            val line = repository.observeBalanceOverTime(scope(week(1, 5), checking)).first()

            // Both halves narrow together or neither does. Carrying in every account's
            // opening balance and then moving it by one account's movements is the failure
            // this exists to catch, and it draws a line that looks entirely plausible.
            assertEquals(Money(230_000, "EUR"), line.opening)
            assertEquals(Money(200_000, "EUR"), line.closing)
            assertEquals(Money(-30_000, "EUR"), line.change)
        }

    @Test
    fun `one account's line ends on what the Accounts tab shows for it`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 250_000))
            database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 900_000))
            database.transactionDao().insertAll(
                listOf(
                    transaction(checking, kind = TransactionKind.INCOME, amountMinor = 40_000, date = july(15)),
                    transaction(checking, amountMinor = 30_000, date = august(2)),
                ),
            )

            val line =
                repository
                    .observeBalanceOverTime(
                        scope(ReportPeriod.Month(YearMonth.of(2026, 8)), checking),
                    ).first()
            val onTheAccountsTab =
                database
                    .accountDao()
                    .observeBalances()
                    .first()
                    .single { it.accountId == checking }
                    .balanceMinor

            // The same check the whole-app line already makes, narrowed: the last point of a
            // line ending today is the number that account shows, and it can be read off the
            // phone in ten seconds.
            assertEquals(onTheAccountsTab, line.closing.minorUnits)
        }

    @Test
    fun `a period with nothing in it is empty, and the line it would draw is flat`() =
        runTest {
            val accountId = database.accountDao().insert(account(openingBalanceMinor = 80_000))
            database.transactionDao().insert(transaction(accountId, amountMinor = 5_000, date = july(4)))

            val line = repository.observeBalanceOverTime(scope(week(1, 5))).first()

            // Empty is a state, not a missing answer: the section says so in words rather
            // than drawing a flat line that is true and tells you nothing.
            assertTrue(line.isEmpty)
            assertEquals(0, line.activeDays)
            assertEquals(Money(75_000, "EUR"), line.opening)
            assertEquals(Money(75_000, "EUR"), line.closing)
            assertEquals(Money.zero("EUR"), line.change)
            assertEquals(FIVE_DAYS, line.points.size)
            assertTrue(line.points.all { it.balance == Money(75_000, "EUR") })
        }

    @Test
    fun `a period that is all income only ever goes up`() =
        runTest {
            val accountId = database.accountDao().insert(account(openingBalanceMinor = 10_000))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 200_000, date = august(1)),
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 5_000, date = august(3)),
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 45_000, date = august(5)),
                ),
            )

            val line = repository.observeBalanceOverTime(scope(week(1, 5))).first()

            assertFalse(line.isEmpty)
            assertEquals(
                listOf(210_000L, 210_000L, 215_000L, 215_000L, 260_000L),
                line.points.map { it.balance.minorUnits },
            )
            // Never a step down, and the change is the whole of the income.
            assertTrue(line.points.zipWithNext().all { (a, b) -> b.balance.minorUnits >= a.balance.minorUnits })
            assertEquals(Money(250_000, "EUR"), line.change)
        }

    @Test
    fun `income and an expense on the same day net into one point`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 30_000, date = august(2)),
                    transaction(accountId, amountMinor = 12_000, date = august(2)),
                    transaction(accountId, amountMinor = 18_000, date = august(2)),
                ),
            )

            val line = repository.observeBalanceOverTime(scope(week(1, 5))).first()

            // Three movements, one day, and they happen to cancel out exactly: the day is
            // still an active day, which is what keeps this apart from an empty period.
            assertEquals(listOf(0L, 0L, 0L, 0L, 0L), line.points.map { it.balance.minorUnits })
            assertEquals(1, line.activeDays)
            assertFalse(line.isEmpty)
        }

    @Test
    fun `both ends of the period are inside it`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 1_000, date = august(1)),
                    transaction(accountId, kind = TransactionKind.INCOME, amountMinor = 2_000, date = august(5)),
                ),
            )

            val line = repository.observeBalanceOverTime(scope(week(1, 5))).first()

            assertEquals(Money.zero("EUR"), line.opening)
            assertEquals(
                1_000L,
                line.points
                    .first()
                    .balance.minorUnits,
            )
            assertEquals(
                3_000L,
                line.points
                    .last()
                    .balance.minorUnits,
            )
            assertEquals(2, line.activeDays)
        }

    @Test
    fun `an account with an opening balance and no movements at all still counts`() =
        runTest {
            // A LEFT JOIN filtered on the transaction date drops this account entirely, which
            // is the easy way to write this query and the reason it is two sub-selects.
            database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 500_000))

            val line = repository.observeBalanceOverTime(scope(week(1, 5))).first()

            assertEquals(Money(500_000, "EUR"), line.opening)
            assertEquals(Money(500_000, "EUR"), line.closing)
        }

    @Test
    fun `an archived account is still money, so it is still on the line`() =
        runTest {
            // Archiving means "stop offering it", not "the money left" — and the donut above
            // this chart already counts an archived account's spending.
            val archived =
                database.accountDao().insert(
                    account(name = "Old", openingBalanceMinor = 20_000, archived = true),
                )
            database.transactionDao().insert(
                transaction(archived, kind = TransactionKind.INCOME, amountMinor = 3_000, date = august(2)),
            )

            val line = repository.observeBalanceOverTime(scope(week(1, 5))).first()

            assertEquals(Money(20_000, "EUR"), line.opening)
            assertEquals(Money(23_000, "EUR"), line.closing)
        }

    @Test
    fun `a year is a point a day, and the last of them is the year's closing balance`() =
        runTest {
            val accountId = database.accountDao().insert(account(openingBalanceMinor = 1_000))
            database.transactionDao().insertAll(
                listOf(
                    transaction(
                        accountId,
                        kind = TransactionKind.INCOME,
                        amountMinor = 100,
                        date = LocalDate.of(2026, 1, 1),
                    ),
                    transaction(accountId, amountMinor = 400, date = LocalDate.of(2026, 12, 31)),
                ),
            )

            val line: BalanceOverTime = repository.observeBalanceOverTime(scope(ReportPeriod.Year(2026))).first()

            assertEquals(DAYS_IN_2026, line.points.size)
            assertEquals(
                1_100L,
                line.points
                    .first()
                    .balance.minorUnits,
            )
            assertEquals(700L, line.closing.minorUnits)
            assertEquals(Money(-300, "EUR"), line.change)
        }

    @Test
    fun `a backwards range draws nothing rather than throwing`() =
        runTest {
            database.accountDao().insert(account(openingBalanceMinor = 1_000))

            val line =
                repository
                    .observeBalanceOverTime(scope(ReportPeriod.Custom(august(9), august(2))))
                    .first()

            // The view model straightens these before they get here; a chart is not the place
            // to discover that it failed to.
            assertTrue(line.points.isEmpty())
            assertEquals(Money(1_000, "EUR"), line.closing)
        }

    private fun week(
        first: Int,
        last: Int,
    ) = ReportPeriod.Custom(august(first), august(last))

    private fun august(day: Int) = LocalDate.of(2026, 8, day)

    private fun july(day: Int) = LocalDate.of(2026, 7, day)

    /**
     * The scope these tests report on. `accountId` defaults to null — every account — so a
     * test only names one when narrowing is what it is about.
     */
    private fun scope(
        period: ReportPeriod,
        accountId: Long? = null,
    ) = ReportScope(period, "EUR", accountId)

    private companion object {
        const val FIVE_DAYS = 5
        const val DAYS_IN_2026 = 365
    }
}
