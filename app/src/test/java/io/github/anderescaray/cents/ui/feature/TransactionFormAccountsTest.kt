package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomGroupRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.transactionform.TransactionFormViewModel
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.undo.DeletedTransactionBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import java.time.ZoneOffset

/**
 * Which account a movement goes in (`docs/next-improvements.md`, items 5 and 9): a new one starts on
 * the account the last new one went in, if that account can still take it; a broker is offered for
 * an income but not an expense (`DECISIONS.md` D32), and never started on; and an old movement keeps
 * the account it is in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionFormAccountsTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private lateinit var viewModel: TransactionFormViewModel
    private lateinit var transactions: RoomTransactionRepository
    private val entryDefaults = FakeEntryDefaults()

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        transactions = RoomTransactionRepository(database.transactionDao(), database, clock)
        viewModel = formFor()
    }

    @After
    fun tearDown() {
        try {
            viewModels.cancelAll()
            if (::database.isInitialized) database.close()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun formFor(transactionId: Long = Destination.TransactionForm.NEW) =
        viewModels.keep(
            TransactionFormViewModel(
                transactionRepository = transactions,
                deletedTransactions = DeletedTransactionBuffer(CoroutineScope(Dispatchers.Unconfined)),
                entryDefaults = entryDefaults,
                accountRepository =
                    RoomAccountRepository(
                        database.accountDao(),
                        database.transactionDao(),
                        database,
                        clock,
                    ),
                categoryRepository = RoomCategoryRepository(database.categoryDao(), database, clock),
                groupRepository = RoomGroupRepository(database.groupDao(), database.transactionDao(), clock),
                savedStateHandle = SavedStateHandle(mapOf("transactionId" to transactionId)),
                clock = clock,
            ),
        )

    @Test
    fun `a new movement starts on the account the last one went in`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val savings = database.accountDao().insert(account(name = "Savings"))
            entryDefaults.lastAccountId.value = savings

            formFor().uiState.test {
                assertEquals(savings, awaitStateThat { it.selectedAccountId != null }.selectedAccountId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving a new movement remembers its account for the next one`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val savings = database.accountDao().insert(account(name = "Savings"))

            viewModel.uiState.test {
                awaitStateThat { it.accounts.size == 2 }
                viewModel.selectAccount(savings)
                "12".forEach(viewModel::appendDigit)
                awaitStateThat { it.canSave && it.selectedAccountId == savings }
                viewModel.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(savings, entryDefaults.lastAccountId.value)
        }

    @Test
    fun `choosing an account and cancelling remembers nothing`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val savings = database.accountDao().insert(account(name = "Savings"))

            viewModel.uiState.test {
                awaitStateThat { it.accounts.size == 2 }
                viewModel.selectAccount(savings)
                awaitStateThat { it.selectedAccountId == savings }
                cancelAndIgnoreRemainingEvents()
            }

            assertNull(entryDefaults.lastAccountId.value)
        }

    @Test
    fun `correcting an old movement does not move where the next one starts`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking"))
            val savings = database.accountDao().insert(account(name = "Savings"))
            entryDefaults.lastAccountId.value = savings
            val id =
                transactions.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = checking,
                        categoryId = null,
                        kind = TransactionKind.EXPENSE,
                        amount = Money(500, "EUR"),
                        date = LocalDate.of(2026, 8, 18),
                        note = null,
                    ),
                )
            val editor = formFor(id)

            editor.uiState.test {
                awaitStateThat { it.isEditing && it.selectedAccountId == checking }
                editor.setNote("Fixed")
                awaitStateThat { it.note == "Fixed" }
                editor.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(savings, entryDefaults.lastAccountId.value)
        }

    @Test
    fun `a broker is not offered for an expense, nor started on when remembered`() =
        runTest {
            // A broker first in the list, and remembered too: neither is enough to choose it.
            val broker = database.accountDao().insert(account(name = "A broker", type = AccountType.BROKER))
            val checking = database.accountDao().insert(account(name = "Checking"))
            entryDefaults.lastAccountId.value = broker

            formFor().uiState.test {
                val state = awaitStateThat { it.selectedAccountId != null }
                assertEquals(checking, state.selectedAccountId)
                assertEquals(listOf("Checking"), state.accounts.map { it.name })
                assertTrue("the picker says where the broker went", state.brokersLeftOut)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a broker is offered for an income, a dividend, but still not started on`() =
        runTest {
            val broker = database.accountDao().insert(account(name = "A broker", type = AccountType.BROKER))
            val checking = database.accountDao().insert(account(name = "Checking"))
            val card = database.accountDao().insert(account(name = "Card"))
            entryDefaults.lastAccountId.value = card

            val form = formFor()

            form.uiState.test {
                awaitStateThat { it.selectedAccountId != null }
                form.setKind(TransactionKind.INCOME)
                val income = awaitStateThat { it.kind == TransactionKind.INCOME }
                assertEquals(setOf("A broker", "Checking", "Card"), income.accounts.map { it.name }.toSet())
                assertFalse("nothing is left out of an income", income.brokersLeftOut)
                assertEquals("chosen only by tapping it", card, income.selectedAccountId)
                assertTrue(income.accounts.any { it.id == checking })

                form.selectAccount(broker)
                "12".forEach(form::appendDigit)
                awaitStateThat { it.canSave && it.selectedAccountId == broker }
                form.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            val saved =
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .single()
            assertEquals(broker, saved.accountId)
            assertEquals(TransactionKind.INCOME, saved.kind)
            assertEquals(
                "a dividend does not move where the next movement starts",
                card,
                entryDefaults.lastAccountId.value,
            )
        }

    @Test
    fun `an old income in a broker turned into an expense leaves the broker`() =
        runTest {
            val broker = database.accountDao().insert(account(name = "A broker", type = AccountType.BROKER))
            val checking = database.accountDao().insert(account(name = "Checking"))
            val id =
                transactions.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = broker,
                        categoryId = null,
                        kind = TransactionKind.INCOME,
                        amount = Money(4_200, "EUR"),
                        date = LocalDate.of(2026, 8, 18),
                        note = "Dividend",
                    ),
                )
            val editor = formFor(id)

            editor.uiState.test {
                awaitStateThat { it.isEditing && it.selectedAccountId == broker }
                editor.setKind(TransactionKind.EXPENSE)

                // Kept, it would be a new expense in a broker (D32).
                val expense = awaitStateThat { it.kind == TransactionKind.EXPENSE }
                assertEquals(checking, expense.selectedAccountId)
                assertTrue(expense.accounts.none { it.id == broker })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a broker made from an income's picker is the one chosen on the way back`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))

            viewModel.uiState.test {
                awaitStateThat { it.selectedAccountId != null }
                viewModel.setKind(TransactionKind.INCOME)
                awaitStateThat { it.kind == TransactionKind.INCOME }
                viewModel.makingAccount()
                val broker = database.accountDao().insert(account(name = "Broker", type = AccountType.BROKER))

                assertEquals(broker, awaitStateThat { it.accounts.size == 2 }.selectedAccountId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an income in a broker turned into an expense moves to where an expense starts`() =
        runTest {
            val broker = database.accountDao().insert(account(name = "A broker", type = AccountType.BROKER))
            val checking = database.accountDao().insert(account(name = "Checking"))

            viewModel.uiState.test {
                awaitStateThat { it.selectedAccountId != null }
                viewModel.setKind(TransactionKind.INCOME)
                awaitStateThat { it.kind == TransactionKind.INCOME }
                viewModel.selectAccount(broker)
                awaitStateThat { it.selectedAccountId == broker }

                viewModel.setKind(TransactionKind.EXPENSE)

                val expense = awaitStateThat { it.kind == TransactionKind.EXPENSE }
                assertEquals(checking, expense.selectedAccountId)
                assertTrue(expense.brokersLeftOut)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a remembered account that was archived or deleted is let go for one that can take it`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking"))
            val old = database.accountDao().insert(account(name = "Old", archived = true))
            entryDefaults.lastAccountId.value = old

            formFor().uiState.test {
                assertEquals(checking, awaitStateThat { it.selectedAccountId != null }.selectedAccountId)
                cancelAndIgnoreRemainingEvents()
            }

            entryDefaults.lastAccountId.value = 999
            formFor().uiState.test {
                assertEquals(checking, awaitStateThat { it.selectedAccountId != null }.selectedAccountId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `with only a broker nothing is chosen, and the form offers to make an account`() =
        runTest {
            database.accountDao().insert(account(name = "Broker", type = AccountType.BROKER))
            database.categoryDao().insert(category())

            viewModel.uiState.test {
                val state = awaitStateThat { it.hasAccounts && it.categories.isNotEmpty() }
                assertNull("never silently a broker", state.selectedAccountId)
                assertTrue(state.accounts.isEmpty())
                "12".forEach(viewModel::appendDigit)
                assertFalse(awaitStateThat { it.amountInput == "12" }.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an account archived while the form is open is let go for one that can take the movement`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking"))
            val savings = database.accountDao().insert(account(name = "Savings"))

            viewModel.uiState.test {
                awaitStateThat { it.accounts.size == 2 }
                viewModel.selectAccount(savings)
                awaitStateThat { it.selectedAccountId == savings }

                database.accountDao().setArchived(savings, archived = true)

                val state = awaitStateThat { it.accounts.size == 1 }
                assertEquals(checking, state.selectedAccountId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an old movement in a broker account keeps it, shown, through an edit`() =
        runTest {
            val broker = database.accountDao().insert(account(name = "Broker", type = AccountType.BROKER))
            database.accountDao().insert(account(name = "Checking"))
            val id =
                transactions.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = broker,
                        categoryId = null,
                        kind = TransactionKind.INCOME,
                        amount = Money(1_200, "EUR"),
                        date = LocalDate.of(2026, 8, 18),
                        note = "Dividend",
                    ),
                )
            val editor = formFor(id)

            editor.uiState.test {
                val state = awaitStateThat { it.isEditing && it.accounts.size == 2 }
                assertEquals(broker, state.selectedAccountId)
                assertEquals("Broker", state.selectedAccount?.name)
                editor.setNote("Dividend, June")
                awaitStateThat { it.note == "Dividend, June" }
                editor.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(broker, transactions.findById(id)?.accountId)
        }

    @Test
    fun `an account made from the picker is the one chosen on the way back`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking"))

            viewModel.uiState.test {
                awaitStateThat { it.selectedAccountId == checking }
                viewModel.makingAccount()
                val cash = database.accountDao().insert(account(name = "Cash"))

                assertEquals(cash, awaitStateThat { it.accounts.size == 2 }.selectedAccountId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a category made from the picker is chosen, if it is of this movement's kind`() =
        runTest {
            database.accountDao().insert(account())

            viewModel.uiState.test {
                awaitStateThat { it.accounts.isNotEmpty() }
                viewModel.makingCategory()
                val coffee = database.categoryDao().insert(category(name = "Coffee"))
                assertEquals(coffee, awaitStateThat { it.categories.isNotEmpty() }.selectedCategoryId)

                // One made for the other side is not forced onto an expense. The state does not
                // change for it, so a second expense category made afterwards, without the picker,
                // is what shows the database has been heard from since.
                viewModel.makingCategory()
                database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))
                database.categoryDao().insert(category(name = "Tea"))
                assertEquals(coffee, awaitStateThat { it.categories.size == 2 }.selectedCategoryId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `choosing the category already chosen keeps it, and leaving it out is its own action`() =
        runTest {
            database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))

            viewModel.uiState.test {
                awaitStateThat { it.categories.isNotEmpty() }
                viewModel.selectCategory(groceries)
                awaitStateThat { it.selectedCategoryId == groceries }
                viewModel.selectCategory(groceries)
                assertEquals(groceries, viewModel.uiState.value.selectedCategoryId)

                viewModel.clearCategory()
                assertNull(awaitStateThat { it.selectedCategoryId == null }.selectedCategoryId)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
