package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.SavedStateHandle
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.group
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomGroupRepository
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.groups.GroupDate
import io.github.anderescaray.cents.ui.feature.groups.GroupDetailViewModel
import io.github.anderescaray.cents.ui.feature.groups.GroupEditViewModel
import io.github.anderescaray.cents.ui.feature.groups.GroupsViewModel
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import io.github.anderescaray.cents.ui.navigation.Destination
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
class GroupViewModelsTest {
    private lateinit var database: CentsDatabase
    private lateinit var groups: RoomGroupRepository
    private val filters = TransactionFilterStore()
    private val viewModels = ViewModelKeeper()
    private val clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        groups = RoomGroupRepository(database.groupDao(), database.transactionDao(), clock)
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
    fun `a new group needs a name, and saves with its dates`() =
        runTest {
            val editor = editorFor()
            assertFalse(editor.uiState.value.canSave)

            editor.setName("Lisbon trip")
            editor.openPicker(GroupDate.START)
            editor.setDate(LocalDate.of(2026, 10, 2))
            editor.openPicker(GroupDate.END)
            editor.setDate(LocalDate.of(2026, 10, 6))
            assertTrue(editor.uiState.value.canSave)
            editor.save()
            editor.uiState.first { it.finished }

            val saved = groups.observeGroups().first().single()
            assertEquals("Lisbon trip", saved.name)
            assertEquals(LocalDate.of(2026, 10, 2), saved.startDate)
            assertEquals(LocalDate.of(2026, 10, 6), saved.endDate)
        }

    @Test
    fun `a group that ends before it starts is said so and not saved`() =
        runTest {
            val editor = editorFor()
            editor.setName("Backwards")
            editor.openPicker(GroupDate.START)
            editor.setDate(LocalDate.of(2026, 10, 6))
            editor.openPicker(GroupDate.END)
            editor.setDate(LocalDate.of(2026, 10, 2))

            assertTrue(editor.uiState.value.datesInverted)
            assertFalse(editor.uiState.value.canSave)

            editor.clearDates()
            assertTrue(editor.uiState.value.canSave)
        }

    @Test
    fun `an existing group opens as it was, and deleting it keeps its movements`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val lisbon = database.groupDao().insert(group(name = "Lisbon trip", startDate = LocalDate.of(2026, 10, 2)))
            val hotel = database.transactionDao().insert(transaction(checking, amountMinor = 18_000, groupId = lisbon))
            val editor = editorFor(lisbon)

            val loaded = editor.uiState.first { it.loaded }
            assertEquals("Lisbon trip", loaded.name)
            assertEquals(LocalDate.of(2026, 10, 2), loaded.startDate)

            editor.requestDelete()
            assertTrue(editor.uiState.value.confirmingDelete)
            editor.confirmDelete()
            val done = editor.uiState.first { it.finished }

            assertTrue("the screen knows to leave the detail behind too", done.deleted)
            assertTrue(groups.observeGroups().first().isEmpty())
            assertEquals(18_000L, database.transactionDao().findById(hotel)?.amountMinor)
            assertNull(database.transactionDao().findById(hotel)?.groupId)
        }

    @Test
    fun `the list shows each group with what it came to`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val lisbon = database.groupDao().insert(group(name = "Lisbon trip"))
            database.transactionDao().insert(transaction(checking, amountMinor = 18_000, groupId = lisbon))

            val state =
                viewModels
                    .keep(GroupsViewModel(groups, accounts()))
                    .uiState
                    .first { !it.isLoading && it.groups.isNotEmpty() }

            assertEquals(Money(-18_000, "EUR"), state.groups.single().net)
        }

    @Test
    fun `a group's screen splits it by category and opens exactly the movements behind a row`() =
        runTest {
            val checking = database.accountDao().insert(account())
            val hotels = database.categoryDao().insert(category(name = "Hotels"))
            val lisbon = database.groupDao().insert(group(name = "Lisbon trip"))
            database.transactionDao().insert(transaction(checking, hotels, amountMinor = 18_000, groupId = lisbon))
            database.transactionDao().insert(
                transaction(checking, amountMinor = 9_000, kind = TransactionKind.INCOME, groupId = lisbon),
            )
            filters.setQuery("left over from before")
            val detail = detailFor(lisbon)

            val state = detail.uiState.first { !it.isLoading && it.spending.isNotEmpty() }
            assertEquals(listOf("Hotels"), state.spending.map { it.name })
            assertEquals(1, state.income.size)
            assertEquals(Money(-9_000, "EUR"), state.summary?.net)

            detail.showMovements(state.spending.single(), TransactionKind.EXPENSE)
            val narrowed = filters.filter.value
            assertEquals(lisbon, narrowed.groupId)
            assertEquals(CategoryFilter.One(hotels), narrowed.category)
            assertEquals(TransactionKind.EXPENSE, narrowed.kind)
            assertEquals("a drill-down promises exactly these movements", "", narrowed.query)

            detail.showMovements()
            assertEquals(lisbon, filters.filter.value.groupId)
            assertEquals(CategoryFilter.All, filters.filter.value.category)
        }

    @Test
    fun `a group's screen knows when its group has been deleted`() =
        runTest {
            val lisbon = database.groupDao().insert(group())
            val detail = detailFor(lisbon)
            detail.uiState.first { it.summary != null }

            groups.deleteGroup(lisbon)

            assertTrue(detail.uiState.first { it.isGone }.isGone)
        }

    private fun accounts() = RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock)

    private fun editorFor(groupId: Long = Destination.GroupEdit.NEW) =
        viewModels.keep(
            GroupEditViewModel(
                groups = groups,
                savedStateHandle = SavedStateHandle(mapOf("groupId" to groupId)),
                clock = clock,
            ),
        )

    private fun detailFor(groupId: Long) =
        viewModels.keep(
            GroupDetailViewModel(
                groups = groups,
                accounts = accounts(),
                filters = filters,
                savedStateHandle = SavedStateHandle(mapOf("groupId" to groupId)),
            ),
        )
}
