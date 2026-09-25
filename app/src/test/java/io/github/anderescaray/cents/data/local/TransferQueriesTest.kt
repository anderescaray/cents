package io.github.anderescaray.cents.data.local

import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The one thing transfers must get right: **they move a balance and they move no statistic.**
 *
 * Moving 500 € from checking to savings is not spending 500 € and not earning 500 €. Get it
 * wrong in the reports and every number in the app inflates, in both directions at once; get
 * it wrong in the balances and the app disagrees with the bank. So every query is asked here,
 * about the same transfer, with the answer written out rather than derived — a test that
 * computes its expectation the way the query does would agree with a wrong query.
 */
@RunWith(RobolectricTestRunner::class)
class TransferQueriesTest {
    private lateinit var database: CentsDatabase

    private val from = LocalDate.of(2026, 9, 1)
    private val to = LocalDate.of(2026, 9, 30)
    private val date = LocalDate.of(2026, 9, 14)

    /** A day of its own, so a question about the transfer's day is about the transfer. */
    private val groceriesDate = LocalDate.of(2026, 9, 10)

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a transfer is absent from the period totals`() =
        runTest {
            val world = world()
            val dao = database.transactionDao()

            val totals = dao.observeTotalsBetween(from, to).first()

            // The groceries, and nothing else. The transfer contributed 500 € of expense and
            // 500 € of income to a ledger that neither spent nor earned anything.
            assertEquals(2_550L, totals.expenseMinor)
            assertEquals(0L, totals.incomeMinor)

            val source = dao.observeAccountTotalsBetween(world.checking, from, to).first()
            assertEquals("the leg leaving is not spending", 2_550L, source.expenseMinor)
            val destination = dao.observeAccountTotalsBetween(world.savings, from, to).first()
            assertEquals("the leg arriving is not income", 0L, destination.incomeMinor)
        }

    @Test
    fun `a transfer is absent from every chart that adds money up`() =
        runTest {
            world()
            val dao = database.transactionDao()

            val byCategory = dao.observeTotalsByCategory(TransactionKind.EXPENSE, from, to, null).first()
            assertEquals(1, byCategory.size)
            assertEquals(2_550L, byCategory.single().totalMinor)

            // A leg has no category, so an uncategorized group made of transfers is exactly
            // the shape this would take if the clause were missing.
            assertTrue(
                "no uncategorized slice should appear",
                byCategory.none { it.categoryId == null },
            )

            val monthly = dao.observeMonthlyTotals(from, to, null).first()
            assertEquals(2_550L, monthly.single().expenseMinor)
            assertEquals(0L, monthly.single().incomeMinor)

            val weekdays = dao.observeTotalsByWeekday(TransactionKind.EXPENSE, from, to, null).first()
            assertEquals(2_550L, weekdays.sumOf { it.totalMinor })

            val trend = dao.observeCategoriesWithMovements(from, to, null).first()
            assertEquals(1, trend.size)
            assertEquals(2_550L, trend.single().totalMinor)
        }

    @Test
    fun `a transfer moves both balances`() =
        runTest {
            val world = world()

            val balances =
                database
                    .accountDao()
                    .observeBalances()
                    .first()
                    .associateBy { it.accountId }

            // 100 000 opening, minus 2 550 of groceries, minus the 50 000 that left — plus
            // the 300 the move cost.
            assertEquals(47_150L, balances.getValue(world.checking).balanceMinor)
            assertEquals(50_000L, balances.getValue(world.savings).balanceMinor)
        }

    @Test
    fun `the balance line counts a transfer and the reports beside it do not`() =
        runTest {
            val world = world()
            val dao = database.transactionDao()

            val daily = dao.observeDailyNet(from, to, world.checking).first()
            val transferDay = daily.single { it.date == date }
            assertEquals("what left the account, fee included", -50_300L, transferDay.netMinor)

            // Narrowed to the far side, the same day is a step up rather than a step down.
            val arriving = dao.observeDailyNet(from, to, world.savings).first().single()
            assertEquals(50_000L, arriving.netMinor)

            // Over every account the two cancel, and what is left is what the move cost.
            val everywhere = dao.observeDailyNet(from, to, null).first().single { it.date == date }
            assertEquals(-300L, everywhere.netMinor)
        }

    @Test
    fun `a transfer leg stays on the list and names the account at the other end`() =
        runTest {
            val world = world()
            val dao = database.transactionDao()

            val rows = dao.observeByAccountWithNames(world.checking).first()
            val leg = rows.single { it.transferId != null }

            assertEquals("Savings", leg.counterpartAccountName)
            assertEquals(TransactionKind.EXPENSE, leg.kind)
            assertNull("a leg is never classified", leg.categoryId)

            val ordinary = rows.single { it.transferId == null }
            assertNull("an ordinary movement has no counterpart", ordinary.counterpartAccountName)
        }

    @Test
    fun `the uncategorized filter means what the ring's slice meant`() =
        runTest {
            world()
            val dao = database.transactionDao()

            val uncategorized =
                dao
                    .observeFilteredWithNames(
                        accountId = null,
                        categoryId = null,
                        uncategorized = true,
                        kind = null,
                        groupId = null,
                        from = from,
                        to = to,
                        weekday = null,
                        minAmountMinor = null,
                        maxAmountMinor = null,
                        query = null,
                    ).first()

            // A leg carries no category because a transfer is not classified, not because
            // nobody has got round to classifying it. The ring's *Uncategorized* slice counted
            // no legs, so the list it drills into must not show them — otherwise tapping a
            // 20 € slice opens a 500 € transfer.
            assertTrue(
                "a transfer leg is not an unclassified movement",
                uncategorized.none { it.transferId != null },
            )

            // And the list at large still shows them, because they happened.
            val everything =
                dao
                    .observeFilteredWithNames(
                        accountId = null,
                        categoryId = null,
                        uncategorized = false,
                        kind = null,
                        groupId = null,
                        from = from,
                        to = to,
                        weekday = null,
                        minAmountMinor = null,
                        maxAmountMinor = null,
                        query = null,
                    ).first()
            assertEquals(2, everything.count { it.transferId != null })
        }

    @Test
    fun `deleting one account takes whole transfers with it, counted first`() =
        runTest {
            val world = world()
            val dao = database.transactionDao()

            // What the confirmation has to say: two rows live in Checking, and a third — the
            // far leg, sitting in Savings — goes with them.
            assertEquals(2, database.accountDao().countTransactions(world.checking))
            assertEquals(1, dao.countTransferLegsOutside(world.checking))

            val transfers = dao.transferIdsInAccount(world.checking)
            dao.deleteTransfers(transfers)
            dao.deleteByAccount(world.checking)

            assertTrue(
                "no half transfer is left behind",
                dao.observeAll().first().isEmpty(),
            )
        }

    /**
     * One ledger every test here asks questions about: two accounts, one expense of 25,50 €
     * in Checking, and 500 € moved from Checking to Savings with a 3 € fee.
     */
    private suspend fun world(): World {
        val checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 100_000))
        val savings = database.accountDao().insert(account(name = "Savings"))
        val groceries = database.categoryDao().insert(category(name = "Groceries"))
        val dao = database.transactionDao()

        dao.insert(
            transaction(
                accountId = checking,
                categoryId = groceries,
                amountMinor = 2_550,
                date = groceriesDate,
            ),
        )
        val outId =
            dao.insert(
                transaction(
                    accountId = checking,
                    kind = TransactionKind.EXPENSE,
                    amountMinor = 50_300,
                    date = date,
                ),
            )
        dao.setTransferId(id = outId, transferId = outId, updatedAt = FIXED_INSTANT)
        dao.insert(
            transaction(
                accountId = savings,
                kind = TransactionKind.INCOME,
                amountMinor = 50_000,
                date = date,
                transferId = outId,
            ),
        )
        return World(checking = checking, savings = savings)
    }

    private data class World(
        val checking: Long,
        val savings: Long,
    )
}
