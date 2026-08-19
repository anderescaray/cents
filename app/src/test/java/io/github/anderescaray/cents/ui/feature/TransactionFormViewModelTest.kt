package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionFormViewModelTest {
    private lateinit var database: CentsDatabase
    private lateinit var viewModel: TransactionFormViewModel
    private lateinit var transactions: RoomTransactionRepository
    private lateinit var deleted: DeletedTransactionBuffer

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        transactions = RoomTransactionRepository(database.transactionDao(), clock)
        // Real time on purpose: runTest races virtual time ahead while the database
        // works, which would expire the undo window mid-test. Its expiry has its
        // own test, with a scheduler it controls.
        deleted = DeletedTransactionBuffer(CoroutineScope(Dispatchers.Unconfined))
        viewModel = formFor()
    }

    /** The form as the navigation graph builds it: adding by default, editing with an id. */
    private fun formFor(transactionId: Long = Destination.TransactionForm.NEW) =
        TransactionFormViewModel(
            transactionRepository = transactions,
            deletedTransactions = deleted,
            accountRepository = RoomAccountRepository(database.accountDao(), clock),
            categoryRepository = RoomCategoryRepository(database.categoryDao(), database, clock),
            savedStateHandle = SavedStateHandle(mapOf("transactionId" to transactionId)),
            clock = clock,
        )

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `typed digits are minor units, so 1250 means twelve euros fifty`() =
        runTest {
            database.accountDao().insert(account())

            viewModel.uiState.test {
                awaitStateThat { it.accounts.isNotEmpty() }
                "1250".forEach(viewModel::appendDigit)

                val state = awaitStateThat { it.amountDigits == "1250" }
                assertEquals(Money(1_250, "EUR"), state.amount)
                assertTrue(state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `leading zeros never accumulate`() =
        runTest {
            database.accountDao().insert(account())

            viewModel.uiState.test {
                awaitStateThat { it.accounts.isNotEmpty() }
                "0007".forEach(viewModel::appendDigit)

                val state = awaitStateThat { it.amountDigits.isNotEmpty() && it.amountDigits.last() == '7' }
                assertEquals(Money(7, "EUR"), state.amount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an empty amount cannot be saved`() =
        runTest {
            database.accountDao().insert(account())

            viewModel.uiState.test {
                val state = awaitStateThat { it.accounts.isNotEmpty() }
                assertFalse(state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `switching kind clears a category that belonged to the other kind`() =
        runTest {
            database.accountDao().insert(account())
            val expenseCategory =
                database.categoryDao().insert(
                    category(name = "Groceries", kind = CategoryKind.EXPENSE),
                )
            database.categoryDao().insert(category(name = "Salary", kind = CategoryKind.INCOME))

            viewModel.uiState.test {
                awaitStateThat { it.categories.isNotEmpty() }
                viewModel.selectCategory(expenseCategory)
                awaitStateThat { it.selectedCategoryId == expenseCategory }

                viewModel.setKind(TransactionKind.INCOME)

                val state = awaitStateThat { it.kind == TransactionKind.INCOME }
                assertNull(state.selectedCategoryId)
                assertEquals(listOf("Salary"), state.categories.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `saving writes exactly what was entered and reports completion`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val categoryId = database.categoryDao().insert(category(name = "Groceries"))

            viewModel.uiState.test {
                awaitStateThat { it.accounts.isNotEmpty() && it.categories.isNotEmpty() }
                "4599".forEach(viewModel::appendDigit)
                viewModel.selectCategory(categoryId)
                viewModel.setNote("Weekly shop")
                awaitStateThat { it.amountDigits == "4599" && it.selectedCategoryId == categoryId }

                viewModel.save()

                assertTrue(awaitStateThat { it.finished }.finished)
                cancelAndIgnoreRemainingEvents()
            }

            val stored = transactions.observeTransactions().first().single()
            assertEquals(Money(4_599, "EUR"), stored.transaction.amount)
            assertEquals(TransactionKind.EXPENSE, stored.transaction.kind)
            assertEquals("Groceries", stored.categoryName)
            assertEquals("Weekly shop", stored.transaction.note)
        }

    @Test
    fun `editing loads the movement into the form, note and date included`() =
        runTest {
            val accountId = database.accountDao().insert(account(name = "Checking"))
            val categoryId = database.categoryDao().insert(category(name = "Groceries"))
            val id =
                transactions.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = accountId,
                        categoryId = categoryId,
                        kind = TransactionKind.EXPENSE,
                        amount = Money(4_599, "EUR"),
                        date = LocalDate.of(2026, 7, 4),
                        note = "Weekly shop",
                    ),
                )

            val editor = formFor(id)

            editor.uiState.test {
                val state = awaitStateThat { it.isEditing && it.amountDigits.isNotEmpty() }
                assertEquals("4599", state.amountDigits)
                assertEquals(accountId, state.selectedAccountId)
                assertEquals(categoryId, state.selectedCategoryId)
                assertEquals(LocalDate.of(2026, 7, 4), state.date)
                assertEquals("Weekly shop", state.note)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `correcting an amount rewrites the movement instead of recording a second one`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val id =
                transactions.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = accountId,
                        categoryId = null,
                        kind = TransactionKind.EXPENSE,
                        amount = Money(9_999, "EUR"),
                        date = LocalDate.of(2026, 8, 18),
                        note = null,
                    ),
                )
            val editor = formFor(id)

            editor.uiState.test {
                awaitStateThat { it.amountDigits == "9999" }
                repeat(4) { editor.deleteDigit() }
                "1250".forEach(editor::appendDigit)
                editor.setDate(LocalDate.of(2026, 8, 1))
                editor.setNote("Fixed")
                awaitStateThat { it.amountDigits == "1250" && it.note == "Fixed" }

                editor.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            val stored = transactions.observeTransactions().first().single()
            assertEquals(id, stored.transaction.id)
            assertEquals(Money(1_250, "EUR"), stored.transaction.amount)
            assertEquals(LocalDate.of(2026, 8, 1), stored.transaction.date)
            assertEquals("Fixed", stored.transaction.note)
        }

    @Test
    fun `the category a movement already carries stays offered even once archived`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val categoryId = database.categoryDao().insert(category(name = "Old habit"))
            val id =
                transactions.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = accountId,
                        categoryId = categoryId,
                        kind = TransactionKind.EXPENSE,
                        amount = Money(500, "EUR"),
                        date = LocalDate.of(2026, 8, 18),
                        note = null,
                    ),
                )
            database.categoryDao().setArchived(categoryId, archived = true)
            val editor = formFor(id)

            editor.uiState.test {
                val state = awaitStateThat { it.isEditing && it.categories.isNotEmpty() }
                // Archiving keeps a category off new movements; it must not silently
                // uncategorize the one that already uses it.
                assertEquals(listOf("Old habit"), state.categories.map { it.name })
                assertEquals(categoryId, state.selectedCategoryId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a movement is recorded in the currency of its account, not in euros by default`() =
        runTest {
            database.accountDao().insert(account(name = "Dollars", currency = "USD"))

            viewModel.uiState.test {
                awaitStateThat { it.accounts.isNotEmpty() }
                "2500".forEach(viewModel::appendDigit)
                awaitStateThat { it.amountDigits == "2500" }

                viewModel.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                Money(2_500, "USD"),
                transactions
                    .observeTransactions()
                    .first()
                    .single()
                    .transaction.amount,
            )
        }

    @Test
    fun `editing a movement keeps the currency it was recorded in`() =
        runTest {
            val accountId = database.accountDao().insert(account(name = "Dollars", currency = "USD"))
            val id =
                transactions.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = accountId,
                        categoryId = null,
                        kind = TransactionKind.EXPENSE,
                        amount = Money(2_500, "USD"),
                        date = LocalDate.of(2026, 8, 18),
                        note = null,
                    ),
                )
            val editor = formFor(id)

            editor.uiState.test {
                // The currency is known from the movement itself, before the accounts have
                // even arrived: a fallback here would re-denominate it as euros.
                val loaded = awaitStateThat { it.isEditing }
                assertEquals("USD", loaded.currency)

                editor.appendDigit('0')
                awaitStateThat { it.amountDigits == "25000" }
                editor.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                Money(25_000, "USD"),
                transactions
                    .observeTransactions()
                    .first()
                    .single()
                    .transaction.amount,
            )
        }

    @Test
    fun `deleting a movement removes it and leaves it ready to be undone`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val id =
                transactions.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = accountId,
                        categoryId = null,
                        kind = TransactionKind.EXPENSE,
                        amount = Money(700, "EUR"),
                        date = LocalDate.of(2026, 8, 18),
                        note = "Mistake",
                    ),
                )
            val editor = formFor(id)

            editor.uiState.test {
                awaitStateThat { it.isEditing }
                editor.requestDeletion()
                awaitStateThat { it.confirmingDeletion }

                editor.confirmDeletion()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            assertNull(transactions.findById(id))
            val buffered = requireNotNull(deleted.deleted.value)
            assertEquals(id, buffered.id)
            assertEquals(Money(700, "EUR"), buffered.amount)
            assertEquals("Mistake", buffered.note)
        }
}
