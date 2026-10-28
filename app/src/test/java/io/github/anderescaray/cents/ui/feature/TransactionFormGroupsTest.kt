package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.group
import io.github.anderescaray.cents.data.local.recurringRule
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomCategoryRepository
import io.github.anderescaray.cents.data.repository.RoomGroupRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.transactionform.TransactionFormViewModel
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.undo.DeletedTransaction
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
 * The movement form and groups (`docs/next-improvements.md`): an archived group is kept apart, behind
 * the picker's *Archived groups* row, for a new movement and a recorded one alike; a movement already
 * in one keeps it; and a group made from the form is chosen on the way back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionFormGroupsTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private lateinit var viewModel: TransactionFormViewModel
    private lateinit var transactions: RoomTransactionRepository

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

    @Test
    fun `an archived group is not offered for a new movement`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            database.groupDao().insert(group(name = "June trip", archived = true))
            database.groupDao().insert(group(name = "Wedding"))

            viewModel.uiState.test {
                val state = awaitStateThat { it.groups.size == 2 }
                assertEquals(listOf("Wedding"), state.offeredGroups.map { it.name })
                assertEquals(listOf("June trip"), state.archivedGroups.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `with every group archived, a new movement offers no group at all`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            database.groupDao().insert(group(name = "June trip", archived = true))

            viewModel.uiState.test {
                val state = awaitStateThat { it.groups.size == 1 }
                assertTrue(state.offeredGroups.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an old movement keeps its archived group, shown and saved, even after a change of date`() =
        runTest {
            val accountId = database.accountDao().insert(account(name = "Checking"))
            val june = database.groupDao().insert(group(name = "June trip", archived = true))
            database.groupDao().insert(group(name = "Wedding"))
            val id =
                transactions.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = accountId,
                        categoryId = null,
                        kind = TransactionKind.EXPENSE,
                        amount = Money(4_500, "EUR"),
                        date = LocalDate.of(2026, 6, 12),
                        note = null,
                        groupId = june,
                    ),
                )

            val editor = formFor(id)
            editor.uiState.test {
                val loaded = awaitStateThat { it.isEditing && it.groups.size == 2 && it.selectedGroupId == june }
                // The archived group it is already in stays offered, last.
                assertEquals(listOf("Wedding", "June trip"), loaded.offeredGroups.map { it.name })
                assertTrue(loaded.archivedGroups.isEmpty())
                editor.setDate(LocalDate.of(2026, 6, 13))
                awaitStateThat { it.date == LocalDate.of(2026, 6, 13) && it.offeredGroups.any { g -> g.id == june } }
                editor.setNote("Dinner")
                editor.save()
                assertTrue(awaitStateThat { it.finished }.finished)
                cancelAndIgnoreRemainingEvents()
            }

            val stored = transactions.findById(id)!!
            assertEquals("Dinner", stored.note)
            assertEquals(june, stored.groupId)
        }

    @Test
    fun `a captured movement is not offered archived groups up front, but can still be put in one`() =
        runTest {
            val accountId = database.accountDao().insert(account(name = "Checking"))
            val june =
                database.groupDao().insert(
                    group(
                        name = "June trip",
                        startDate = LocalDate.of(2026, 6, 10),
                        endDate = LocalDate.of(2026, 6, 20),
                        archived = true,
                    ),
                )
            val id =
                transactions.addTransaction(
                    Transaction(
                        id = 0,
                        accountId = accountId,
                        categoryId = null,
                        kind = TransactionKind.EXPENSE,
                        amount = Money(3_000, "EUR"),
                        date = LocalDate.of(2026, 6, 14),
                        note = "Dinner, forgotten",
                    ),
                )

            val editor = formFor(id)
            editor.uiState.test {
                val loaded = awaitStateThat { it.isEditing && it.groups.isNotEmpty() }
                assertTrue(loaded.offeredGroups.isEmpty())
                assertEquals(listOf("June trip"), loaded.archivedGroups.map { it.name })
                editor.selectGroup(june)
                val chosen = awaitStateThat { it.selectedGroupId == june }
                assertEquals(listOf("June trip"), chosen.offeredGroups.map { it.name })
                assertTrue(chosen.archivedGroups.isEmpty())
                editor.save()
                assertTrue(awaitStateThat { it.finished }.finished)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(june, transactions.findById(id)?.groupId)
        }

    @Test
    fun `a group archived while a new movement is being recorded stays chosen`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val wedding = database.groupDao().insert(group(name = "Wedding"))

            viewModel.uiState.test {
                awaitStateThat { it.accounts.isNotEmpty() && it.groups.isNotEmpty() }
                viewModel.selectGroup(wedding)
                awaitStateThat { it.selectedGroupId == wedding }

                database.groupDao().update(database.groupDao().findById(wedding)!!.copy(archived = true))

                // The owner chose it, and the archived ones can be chosen now, so it is not undone.
                val after = awaitStateThat { it.groups.single().archived }
                assertEquals(wedding, after.selectedGroupId)
                assertEquals(listOf(wedding), after.offeredGroups.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a group made from the form is chosen when the owner comes back`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            database.groupDao().insert(group(name = "Wedding"))

            viewModel.uiState.test {
                awaitStateThat { it.accounts.isNotEmpty() && it.groups.isNotEmpty() }
                viewModel.makingGroup()
                val lisbon = database.groupDao().insert(group(name = "Lisbon trip"))

                assertEquals(lisbon, awaitStateThat { it.groups.size == 2 }.selectedGroupId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a group deleted while the form is open is let go`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val wedding = database.groupDao().insert(group(name = "Wedding"))

            viewModel.uiState.test {
                awaitStateThat { it.accounts.isNotEmpty() && it.groups.isNotEmpty() }
                viewModel.selectGroup(wedding)
                awaitStateThat { it.selectedGroupId == wedding }

                database.groupDao().deleteById(wedding)

                assertNull(awaitStateThat { it.groups.isEmpty() }.selectedGroupId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun formFor(transactionId: Long = Destination.TransactionForm.NEW) =
        viewModels.keep(
            TransactionFormViewModel(
                transactionRepository = transactions,
                deletedTransactions = DeletedTransactionBuffer(CoroutineScope(Dispatchers.Unconfined)),
                entryDefaults = FakeEntryDefaults(),
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
}
