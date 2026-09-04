package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Narrowing the list, which is the whole of "what did I spend on restaurants in July".
 *
 * Every case goes through the repository rather than the DAO, because the escaping of the
 * LIKE wildcards happens on the way past and is exactly the part that is easy to get subtly
 * wrong.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionFilterTest {
    private lateinit var database: CentsDatabase
    private lateinit var transactions: RoomTransactionRepository

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    private var checking = 0L
    private var savings = 0L
    private var restaurants = 0L
    private var rent = 0L
    private var salary = 0L

    @Before
    fun setUp() {
        database = createTestDatabase()
        transactions = RoomTransactionRepository(database.transactionDao(), clock)
    }

    @After
    fun tearDown() = database.close()

    /** A small ledger with something in every corner the filter can reach. */
    private suspend fun seed() {
        checking = database.accountDao().insert(account(name = "Checking"))
        savings = database.accountDao().insert(account(name = "Savings"))
        restaurants = database.categoryDao().insert(category(name = "Restaurants"))
        rent = database.categoryDao().insert(category(name = "Rent"))
        salary = database.categoryDao().insert(category(name = "Pay", kind = CategoryKind.INCOME))

        val dao = database.transactionDao()
        // July, the month the question is about.
        dao.insert(
            transaction(
                accountId = checking,
                categoryId = restaurants,
                amountMinor = 3_450,
                date = LocalDate.of(2026, 7, 4),
                note = "Lunch with Ana",
            ),
        )
        dao.insert(
            transaction(
                accountId = checking,
                categoryId = restaurants,
                amountMinor = 1_200,
                date = LocalDate.of(2026, 7, 22),
            ),
        )
        dao.insert(
            transaction(
                accountId = savings,
                categoryId = rent,
                amountMinor = 90_000,
                date = LocalDate.of(2026, 7, 1),
            ),
        )
        // August, which the July question must not pick up.
        dao.insert(
            transaction(
                accountId = checking,
                categoryId = restaurants,
                amountMinor = 5_000,
                date = LocalDate.of(2026, 8, 3),
            ),
        )
        dao.insert(
            transaction(
                accountId = checking,
                categoryId = salary,
                kind = TransactionKind.INCOME,
                amountMinor = 200_000,
                date = LocalDate.of(2026, 8, 1),
                note = "Salary",
            ),
        )
        // Never classified: the inbox.
        dao.insert(
            transaction(
                accountId = savings,
                amountMinor = 700,
                date = LocalDate.of(2026, 8, 5),
                note = "Cash withdrawal, 50% of the envelope",
            ),
        )
    }

    private suspend fun amountsFor(filter: TransactionFilter): List<Long> =
        transactions
            .observeTransactions(filter)
            .first()
            .map { it.transaction.amount.minorUnits }

    @Test
    fun `no filter is every movement, newest first`() =
        runTest {
            seed()

            assertEquals(
                listOf(700L, 5_000L, 200_000L, 1_200L, 3_450L, 90_000L),
                amountsFor(TransactionFilter.None),
            )
        }

    @Test
    fun `what did I spend on restaurants in July`() =
        runTest {
            seed()

            val answer =
                amountsFor(
                    TransactionFilter(
                        category = CategoryFilter.One(restaurants),
                        from = LocalDate.of(2026, 7, 1),
                        to = LocalDate.of(2026, 7, 31),
                    ),
                )

            // The August restaurant meal is not part of the answer, and neither is the rent.
            assertEquals(listOf(1_200L, 3_450L), answer)
            assertEquals(4_650L, answer.sum())
        }

    @Test
    fun `each filter narrows on its own`() =
        runTest {
            seed()

            assertEquals(
                listOf(700L, 90_000L),
                amountsFor(TransactionFilter(accountId = savings)),
            )
            assertEquals(
                listOf(200_000L),
                amountsFor(TransactionFilter(kind = TransactionKind.INCOME)),
            )
            assertEquals(
                listOf(700L),
                amountsFor(TransactionFilter(category = CategoryFilter.Uncategorized)),
            )
            assertEquals(
                listOf(5_000L, 1_200L, 3_450L),
                amountsFor(TransactionFilter(minAmountMinor = 1_000, maxAmountMinor = 10_000)),
            )
        }

    @Test
    fun `text matches the note, the category and the account`() =
        runTest {
            seed()

            assertEquals(listOf(3_450L), amountsFor(TransactionFilter(query = "lunch with")))
            assertEquals(
                listOf(5_000L, 1_200L, 3_450L),
                amountsFor(TransactionFilter(query = "restaurants")),
            )
            assertEquals(listOf(700L, 90_000L), amountsFor(TransactionFilter(query = "savings")))
            // Case does not matter, which is what makes a search field usable at all.
            assertEquals(listOf(200_000L), amountsFor(TransactionFilter(query = "SALARY")))
            // Nor does surrounding whitespace.
            assertEquals(listOf(200_000L), amountsFor(TransactionFilter(query = "  salary  ")))
        }

    @Test
    fun `the LIKE wildcards are searched for, not obeyed`() =
        runTest {
            seed()

            // Escaped, this finds the note that literally contains "50%".
            assertEquals(listOf(700L), amountsFor(TransactionFilter(query = "50%")))
            // An underscore is the single-character wildcard: "l_nch" must not find "Lunch".
            assertEquals(emptyList<Long>(), amountsFor(TransactionFilter(query = "l_nch")))
        }

    @Test
    fun `a weekday narrows to that day of the week, in any month, and composes with a range`() =
        runTest {
            seed()
            // The July restaurant meals are the 4th and the 22nd, seven days and two weeks
            // apart — so the 4th and the 1st of August share a weekday and the 22nd does not.
            val saturday = LocalDate.of(2026, 7, 4).dayOfWeek

            // Across every month: the 3 450 on Saturday 4 July and the 200 000 on Saturday
            // 1 August, newest first. The two Wednesday movements are left out.
            assertEquals(
                listOf(200_000L, 3_450L),
                amountsFor(TransactionFilter(weekday = saturday)),
            )

            // With July's range added, only the one that falls in July survives.
            assertEquals(
                listOf(3_450L),
                amountsFor(
                    TransactionFilter(
                        weekday = saturday,
                        from = LocalDate.of(2026, 7, 1),
                        to = LocalDate.of(2026, 7, 31),
                    ),
                ),
            )
        }

    @Test
    fun `filters compose rather than replacing one another`() =
        runTest {
            seed()

            assertEquals(
                listOf(1_200L),
                amountsFor(
                    TransactionFilter(
                        accountId = checking,
                        category = CategoryFilter.One(restaurants),
                        kind = TransactionKind.EXPENSE,
                        from = LocalDate.of(2026, 7, 10),
                        to = LocalDate.of(2026, 7, 31),
                        maxAmountMinor = 2_000,
                    ),
                ),
            )
        }

    @Test
    fun `an empty search is not a filter that matches nothing`() =
        runTest {
            seed()

            // A blank field means "do not narrow by text", not "find movements whose note is
            // the empty string" — the difference between a full list and an empty screen.
            assertEquals(6, amountsFor(TransactionFilter(query = "   ")).size)
        }
}
