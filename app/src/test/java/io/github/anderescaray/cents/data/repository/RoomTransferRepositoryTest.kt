package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.Transfer
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A transfer is two rows or none.
 *
 * Nothing in the database enforces that — there is no transfers table and no foreign key
 * between the legs — so what holds the invariant up is this repository writing both sides
 * inside one transaction, and these tests are what say so.
 */
@RunWith(RobolectricTestRunner::class)
class RoomTransferRepositoryTest {
    private lateinit var database: CentsDatabase
    private lateinit var transfers: RoomTransferRepository
    private lateinit var accounts: RoomAccountRepository

    private var checking: Long = 0
    private var savings: Long = 0

    private val clock = Clock.fixed(Instant.parse("2026-09-14T10:00:00Z"), ZoneOffset.UTC)
    private val date = LocalDate.of(2026, 9, 14)

    @Before
    fun setUp() {
        database = createTestDatabase()
        transfers = RoomTransferRepository(database.transactionDao(), database, clock)
        accounts = RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a transfer is written as an expense and an income sharing one id`() =
        runTest {
            world()

            val id = transfers.saveTransfer(transfer())

            val legs = database.transactionDao().findTransferLegs(id)
            assertEquals(2, legs.size)
            val out = legs.single { it.kind == TransactionKind.EXPENSE }
            val income = legs.single { it.kind == TransactionKind.INCOME }

            assertEquals(checking, out.accountId)
            assertEquals(savings, income.accountId)
            assertEquals(50_000L, out.amountMinor)
            assertEquals(50_000L, income.amountMinor)
            assertEquals("both legs carry the pair id", listOf(id, id), legs.map { it.transferId })
            assertNull("a leg is never classified", out.categoryId)
        }

    @Test
    fun `the fee leaves the source account and is not a third row`() =
        runTest {
            world()

            val id = transfers.saveTransfer(transfer(fee = 300))

            val legs = database.transactionDao().findTransferLegs(id)
            assertEquals("a fee adds nothing to the ledger but a difference", 2, legs.size)
            assertEquals(50_300L, legs.single { it.kind == TransactionKind.EXPENSE }.amountMinor)
            assertEquals(50_000L, legs.single { it.kind == TransactionKind.INCOME }.amountMinor)

            // And it comes back as the fee it was, because it is read as the difference
            // rather than stored beside the amounts it has to agree with.
            val read = transfers.findTransfer(id)
            assertEquals(Money(50_000, "EUR"), read?.amount)
            assertEquals(Money(300, "EUR"), read?.fee)
            assertEquals(Money(50_300, "EUR"), read?.amountLeaving)
        }

    @Test
    fun `editing a transfer rewrites both legs in place`() =
        runTest {
            world()
            val id = transfers.saveTransfer(transfer())
            val originalIds =
                database
                    .transactionDao()
                    .findTransferLegs(id)
                    .map { it.id }
                    .sorted()

            // The two ends swapped and the amount changed: an update that patched one leg
            // would leave the money flowing the way it used to.
            transfers.saveTransfer(
                transfer(fromAccountId = savings, toAccountId = checking, amountMinor = 20_000).copy(id = id),
            )

            val legs = database.transactionDao().findTransferLegs(id)
            assertEquals(2, legs.size)
            assertEquals("the rows keep their ids", originalIds, legs.map { it.id }.sorted())
            assertEquals(savings, legs.single { it.kind == TransactionKind.EXPENSE }.accountId)
            assertEquals(checking, legs.single { it.kind == TransactionKind.INCOME }.accountId)
            assertEquals(20_000L, legs.first().amountMinor)
        }

    @Test
    fun `deleting a transfer removes both sides and puts both balances back`() =
        runTest {
            world()
            val before = balances()
            val id = transfers.saveTransfer(transfer(fee = 300))

            transfers.deleteTransfer(id)

            assertTrue(database.transactionDao().findTransferLegs(id).isEmpty())
            assertEquals("the fee comes back too, because nothing was spent", before, balances())
        }

    @Test
    fun `deleting an account takes whole transfers and says how many rows go`() =
        runTest {
            world()
            transfers.saveTransfer(transfer())

            // One row lives in Checking; the other lives in Savings and goes with it.
            assertEquals(1, accounts.countTransactions(checking))
            assertEquals(2, accounts.countTransactionsRemovedWith(checking))

            accounts.deleteAccountWithTransactions(checking)

            assertTrue(
                "a leg with nobody on the other end is money arriving from nowhere",
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `moving an account into the other side of its transfers counts those transfers first`() =
        runTest {
            world()
            val cash = database.accountDao().insert(account(name = "Cash"))
            transfers.saveTransfer(transfer())
            transfers.saveTransfer(transfer())

            // Two transfers each way round, none with Cash, so only Savings is warned about.
            assertEquals(2, accounts.countTransfersBetween(checking, savings))
            assertEquals(2, accounts.countTransfersBetween(savings, checking))
            assertEquals(0, accounts.countTransfersBetween(checking, cash))
        }

    @Test
    fun `a transfer that is gone reads as nothing rather than as half of one`() =
        runTest {
            world()
            val id = transfers.saveTransfer(transfer())
            database.transactionDao().deleteById(
                database
                    .transactionDao()
                    .findTransferLegs(id)
                    .first()
                    .id,
            )

            assertNull("one leg is not a transfer", transfers.findTransfer(id))
        }

    private suspend fun world() {
        checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 100_000))
        savings = database.accountDao().insert(account(name = "Savings", openingBalanceMinor = 100_000))
    }

    private suspend fun balances(): Map<Long, Long> =
        database
            .accountDao()
            .observeBalances()
            .first()
            .associate { it.accountId to it.balanceMinor }

    private fun transfer(
        fromAccountId: Long = checking,
        toAccountId: Long = savings,
        amountMinor: Long = 50_000,
        fee: Long = 0,
    ) = Transfer(
        id = Transfer.NEW,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amount = Money(amountMinor, "EUR"),
        fee = Money(fee, "EUR"),
        date = date,
        note = null,
    )
}
