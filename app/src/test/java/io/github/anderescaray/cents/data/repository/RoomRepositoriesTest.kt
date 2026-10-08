package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class RoomRepositoriesTest {
    private lateinit var database: CentsDatabase
    private lateinit var accounts: RoomAccountRepository
    private lateinit var categories: RoomCategoryRepository
    private lateinit var transactions: RoomTransactionRepository

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = createTestDatabase()
        accounts = RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock)
        categories = RoomCategoryRepository(database.categoryDao(), database, clock)
        transactions = RoomTransactionRepository(database.transactionDao(), database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an account reports its balance after movements are recorded`() =
        runTest {
            val id = database.accountDao().insert(account(openingBalanceMinor = 10_000))

            transactions.addTransaction(newTransaction(id, TransactionKind.INCOME, 200_000))
            transactions.addTransaction(newTransaction(id, TransactionKind.EXPENSE, 4_599))

            val withBalance = accounts.observeAccountsWithBalances().first().single()

            assertEquals(Money(205_401, "EUR"), withBalance.balance)
            assertEquals(AccountType.BANK, withBalance.account.type)
        }

    @Test
    fun `a saved transaction comes back with its category and account names`() =
        runTest {
            val accountId = database.accountDao().insert(account(name = "Checking"))
            val categoryId = database.categoryDao().insert(category(name = "Groceries"))

            transactions.addTransaction(
                newTransaction(accountId, TransactionKind.EXPENSE, 1_250).copy(
                    categoryId = categoryId,
                    note = "Market",
                ),
            )

            val details = transactions.observeTransactions().first().single()

            assertEquals("Checking", details.accountName)
            assertEquals("Groceries", details.categoryName)
            assertEquals("Market", details.transaction.note)
            assertEquals(Money(1_250, "EUR"), details.transaction.amount)
        }

    @Test
    fun `an expense counts as negative and income as positive in the same period`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            transactions.addTransaction(newTransaction(accountId, TransactionKind.INCOME, 150_000))
            transactions.addTransaction(newTransaction(accountId, TransactionKind.EXPENSE, 25_000))

            val summary =
                transactions
                    .observeSummary(
                        from = LocalDate.of(2026, 8, 1),
                        to = LocalDate.of(2026, 8, 31),
                        currency = "EUR",
                    ).first()

            assertEquals(Money(150_000, "EUR"), summary.income)
            assertEquals(Money(25_000, "EUR"), summary.expenses)
            assertEquals(Money(125_000, "EUR"), summary.net)
        }

    @Test
    fun `a period only counts the transactions inside it`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            transactions.addTransaction(
                newTransaction(accountId, TransactionKind.EXPENSE, 5_000)
                    .copy(date = LocalDate.of(2026, 7, 31)),
            )
            transactions.addTransaction(
                newTransaction(accountId, TransactionKind.EXPENSE, 3_000)
                    .copy(date = LocalDate.of(2026, 8, 15)),
            )

            val august =
                transactions
                    .observeSummary(
                        from = LocalDate.of(2026, 8, 1),
                        to = LocalDate.of(2026, 8, 31),
                        currency = "EUR",
                    ).first()

            assertEquals(Money(3_000, "EUR"), august.expenses)
        }

    @Test
    fun `editing a transaction keeps its original creation instant`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val id = transactions.addTransaction(newTransaction(accountId, TransactionKind.EXPENSE, 1_000))
            val createdAt = database.transactionDao().findById(id)!!.createdAt

            val later =
                RoomTransactionRepository(
                    database.transactionDao(),
                    database,
                    Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC),
                )
            later.updateTransaction(
                requireNotNull(transactions.findById(id)).copy(amount = Money(2_000, "EUR")),
            )

            val stored = requireNotNull(database.transactionDao().findById(id))
            assertEquals(createdAt, stored.createdAt)
            assertEquals(Instant.parse("2026-09-01T12:00:00Z"), stored.updatedAt)
            assertEquals(2_000L, stored.amountMinor)
        }

    @Test
    fun `categories are offered per kind`() =
        runTest {
            database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))
            database.categoryDao().insert(category(name = "Rent", kind = CategoryKind.EXPENSE))

            assertEquals(
                listOf("Salary"),
                categories.observeCategories(CategoryKind.INCOME).first().map { it.name },
            )
            assertEquals(
                listOf("Rent"),
                categories.observeCategories(CategoryKind.EXPENSE).first().map { it.name },
            )
        }

    @Test
    fun `reordering stores consecutive positions, whatever they were before`() =
        runTest {
            val first = database.categoryDao().insert(category(name = "First", sortOrder = 5))
            val second = database.categoryDao().insert(category(name = "Second", sortOrder = 40))
            val third = database.categoryDao().insert(category(name = "Third", sortOrder = 41))

            categories.reorder(listOf(third, first, second))

            val stored = categories.observeCategories(CategoryKind.EXPENSE).first()
            assertEquals(listOf("Third", "First", "Second"), stored.map { it.name })
            assertEquals(listOf(0, 1, 2), stored.map { it.sortOrder })
        }

    @Test
    fun `a deleted transaction is gone`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val id = transactions.addTransaction(newTransaction(accountId, TransactionKind.EXPENSE, 900))

            transactions.deleteTransaction(id)

            assertNull(transactions.findById(id))
        }

    @Test
    fun `reassigning carries the movements over and lets the emptied account go`() =
        runTest {
            val from = database.accountDao().insert(account(name = "Old bank", openingBalanceMinor = 5_000))
            val to = database.accountDao().insert(account(name = "New bank", openingBalanceMinor = 0))
            transactions.addTransaction(newTransaction(from, TransactionKind.INCOME, 200_000))
            transactions.addTransaction(newTransaction(from, TransactionKind.EXPENSE, 4_599))

            accounts.reassignAndDelete(id = from, targetId = to)

            assertEquals(listOf("New bank"), accounts.observeAccounts(includeArchived = true).first().map { it.name })
            // Every movement is intact and now counted in the account that received them, and
            // the 50 € opening balance travels too, so the money in the books is unchanged.
            assertEquals(
                Money(200_401, "EUR"),
                accounts
                    .observeAccountsWithBalances()
                    .first()
                    .single()
                    .balance,
            )
        }

    @Test
    fun `reassigning refuses an account as its own destination`() =
        runTest {
            val id = database.accountDao().insert(account(name = "Only account"))
            transactions.addTransaction(newTransaction(id, TransactionKind.EXPENSE, 1_000))

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { accounts.reassignAndDelete(id = id, targetId = id) }
            }

            // The guard matters because the query would have deleted the account after
            // pointing its movements at it: a foreign key violation, or worse, silence.
            assertEquals(1, accounts.observeAccounts().first().size)
            assertEquals(1, database.transactionDao().count())
        }

    @Test
    fun `deleting an account with its movements takes only its own`() =
        runTest {
            val doomed = database.accountDao().insert(account(name = "Wrong account"))
            val kept = database.accountDao().insert(account(name = "Real account"))
            transactions.addTransaction(newTransaction(doomed, TransactionKind.EXPENSE, 3_000))
            transactions.addTransaction(newTransaction(doomed, TransactionKind.INCOME, 8_000))
            transactions.addTransaction(newTransaction(kept, TransactionKind.EXPENSE, 999))

            accounts.deleteAccountWithTransactions(doomed)

            assertEquals(
                listOf("Real account"),
                accounts.observeAccounts(includeArchived = true).first().map { it.name },
            )
            assertEquals(1, database.transactionDao().count())
            assertEquals(
                listOf(999L),
                database
                    .transactionDao()
                    .observeByAccount(kept)
                    .first()
                    .map { it.amountMinor },
            )
        }

    @Test
    fun `restoring movements writes them in one database transaction with their ids`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 100_000))
            val savings = database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 100_000))

            val outLeg =
                Transaction(
                    id = 10,
                    accountId = checking,
                    categoryId = null,
                    kind = TransactionKind.EXPENSE,
                    amount = Money(50_300, "EUR"),
                    date = LocalDate.of(2026, 9, 14),
                    note = "Transfer to savings",
                    transferId = 10,
                )
            val inLeg =
                Transaction(
                    id = 11,
                    accountId = savings,
                    categoryId = null,
                    kind = TransactionKind.INCOME,
                    amount = Money(50_000, "EUR"),
                    date = LocalDate.of(2026, 9, 14),
                    note = "Transfer to savings",
                    transferId = 10,
                )

            transactions.restore(listOf(outLeg, inLeg))

            val legs = transactions.findTransferLegs(10)
            assertEquals(2, legs.size)
            assertEquals(listOf(10L, 11L), legs.map { it.id }.sorted())
            val out = legs.single { it.kind == TransactionKind.EXPENSE }
            val income = legs.single { it.kind == TransactionKind.INCOME }
            assertEquals(checking, out.accountId)
            assertEquals(savings, income.accountId)
            assertEquals(Money(50_300, "EUR"), out.amount)
            assertEquals(Money(50_000, "EUR"), income.amount)
            assertEquals(10L, out.transferId)
            assertEquals(10L, income.transferId)
            assertEquals(LocalDate.of(2026, 9, 14), out.date)

            val balances =
                accounts.observeAccountsWithBalances().first().associate {
                    it.account.id to
                        it.balance.minorUnits
                }
            assertEquals(49_700L, balances[checking])
            assertEquals(150_000L, balances[savings])
        }

    @Test
    fun `a failed restore rolls back and restores neither movement`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 100_000))
            val savings = database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 100_000))

            val validLeg =
                Transaction(
                    id = 20,
                    accountId = checking,
                    categoryId = null,
                    kind = TransactionKind.EXPENSE,
                    amount = Money(50_000, "EUR"),
                    date = LocalDate.of(2026, 9, 14),
                    note = null,
                    transferId = 20,
                )
            // Non-existent account violates RESTRICT foreign key
            val invalidLeg =
                Transaction(
                    id = 21,
                    accountId = 999_999L,
                    categoryId = null,
                    kind = TransactionKind.INCOME,
                    amount = Money(50_000, "EUR"),
                    date = LocalDate.of(2026, 9, 14),
                    note = null,
                    transferId = 20,
                )

            assertThrows(Exception::class.java) {
                runBlocking {
                    transactions.restore(listOf(validLeg, invalidLeg))
                }
            }

            assertNull(transactions.findById(20))
            assertNull(transactions.findById(21))
            assertEquals(emptyList<Transaction>(), transactions.findTransferLegs(20))

            val balances =
                accounts.observeAccountsWithBalances().first().associate {
                    it.account.id to
                        it.balance.minorUnits
                }
            assertEquals(100_000L, balances[checking])
            assertEquals(100_000L, balances[savings])
        }

    private fun newTransaction(
        accountId: Long,
        kind: TransactionKind,
        minor: Long,
    ) = Transaction(
        id = 0,
        accountId = accountId,
        categoryId = null,
        kind = kind,
        amount = Money(minor, "EUR"),
        date = LocalDate.of(2026, 8, 18),
        note = null,
    )
}
