package io.github.anderescaray.cents.ui.feature

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomAccountRepository
import io.github.anderescaray.cents.data.repository.RoomTransactionRepository
import io.github.anderescaray.cents.domain.model.CategoryFilter
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.reports.ReportsViewModel
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingSection
import io.github.anderescaray.cents.ui.filter.TransactionFilterStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Tapping a slice hands the transaction list the filter that slice is made of. What is being
 * checked here is that the list would show *exactly* the movements the chart added up —
 * every one of these assertions is a way the two could quietly disagree.
 *
 * The filter is now built by the section rather than by the view model, which is what lets a
 * view drill down on something that is not a category at all. The view model applies whatever
 * it is handed and never learns what was tapped, so these tests go through
 * [SpendingBreakdown.filterFor] on the way in, exactly as the section does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReportsDrillDownTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private lateinit var viewModel: ReportsViewModel
    private lateinit var filters: TransactionFilterStore
    private lateinit var transactions: RoomTransactionRepository

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = createTestDatabase()
        filters = TransactionFilterStore()
        transactions = RoomTransactionRepository(database.transactionDao(), clock)
        viewModel =
            viewModels.keep(
                ReportsViewModel(
                    reportSections(database),
                    RoomAccountRepository(database.accountDao(), database.transactionDao(), database, clock),
                    filters,
                    clock,
                ),
            )
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
    fun `the list a slice opens holds exactly the movements the slice added up`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            val rent = database.categoryDao().insert(category(name = "Rent"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, groceries, amountMinor = 1_500, date = LocalDate.of(2026, 8, 3)),
                    transaction(accountId, groceries, amountMinor = 2_500, date = LocalDate.of(2026, 8, 9)),
                    // Another category, another month, and an income filed under Groceries:
                    // three different ways the list could show more than the slice counted.
                    transaction(accountId, rent, amountMinor = 90_000, date = LocalDate.of(2026, 8, 1)),
                    transaction(accountId, groceries, amountMinor = 7_000, date = LocalDate.of(2026, 7, 30)),
                    transaction(
                        accountId,
                        groceries,
                        kind = TransactionKind.INCOME,
                        amountMinor = 5_000,
                        date = LocalDate.of(2026, 8, 4),
                    ),
                ),
            )
            val breakdown = awaitSpending()
            val slice = breakdown.slices.single { it.name == "Groceries" }

            viewModel.drillDown(breakdown.filterFor(slice))

            val matched = transactions.observeTransactions(filters.filter.value).first()
            assertEquals(2, matched.size)
            assertEquals(
                slice.total,
                matched.fold(Money.zero("EUR")) { acc, details -> acc + details.transaction.amount },
            )
        }

    @Test
    fun `the unnamed slice opens the inbox, not every category`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insertAll(
                listOf(
                    transaction(accountId, groceries, amountMinor = 1_000),
                    transaction(accountId, categoryId = null, amountMinor = 3_000),
                ),
            )
            val breakdown = awaitSpending()
            val unnamed = breakdown.slices.single { it.categoryId == null }

            viewModel.drillDown(breakdown.filterFor(unnamed))

            // `categoryId = null` already means "never classified", so the filter has to say
            // Uncategorized rather than leaving the category unset, which means "any".
            assertEquals(CategoryFilter.Uncategorized, filters.filter.value.category)
            val matched = transactions.observeTransactions(filters.filter.value).first()
            assertEquals(1, matched.size)
            assertEquals(Money(3_000, "EUR"), matched.single().transaction.amount)
        }

    @Test
    fun `drilling down clears a search left in the field`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insert(
                transaction(accountId, groceries, amountMinor = 1_000, note = "milk"),
            )
            filters.setQuery("something typed ages ago")
            val breakdown = awaitSpending()

            viewModel.drillDown(breakdown.filterFor(breakdown.slices.first()))

            // The row promises the movements behind the slice; a stale search word would
            // narrow that promise further with nothing on the chart saying so.
            assertEquals("", filters.filter.value.query)
        }

    @Test
    fun `the period travels with the slice`() =
        runTest {
            val accountId = database.accountDao().insert(account())
            val groceries = database.categoryDao().insert(category(name = "Groceries"))
            database.transactionDao().insert(transaction(accountId, groceries, amountMinor = 1_000))
            val breakdown = awaitSpending()

            viewModel.drillDown(breakdown.filterFor(breakdown.slices.first()))

            val filter = filters.filter.value
            assertEquals(LocalDate.of(2026, 8, 1), filter.from)
            assertEquals(LocalDate.of(2026, 8, 31), filter.to)
            assertEquals(TransactionKind.EXPENSE, filter.kind)
            assertTrue(filter.isNarrowed)
        }

    private suspend fun awaitSpending(): SpendingBreakdown =
        requireNotNull(
            viewModel.uiState
                .first { it.section<SpendingSection>() != null }
                .section<SpendingSection>(),
        ).expense
}
