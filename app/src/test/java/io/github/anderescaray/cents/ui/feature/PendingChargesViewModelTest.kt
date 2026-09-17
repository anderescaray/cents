package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.test
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.RoomRecurringRuleRepository
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.feature.recurring.PendingChargesViewModel
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
 * Typing what the bills came to.
 *
 * The behaviour worth pinning is the empty row: blank means *not this one yet*, and reading it
 * as zero would write a month that cost nothing into every chart in the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PendingChargesViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private var accountId: Long = 0

    private val clock = Clock.fixed(Instant.parse("2026-04-20T09:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            database = createTestDatabase()
            accountId = database.accountDao().insert(account(name = "Checking"))
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
    fun `every waiting bill gets a row, oldest first`() =
        runTest {
            add("Electricity", start = LocalDate.of(2026, 2, 10))
            add("Water", start = LocalDate.of(2026, 1, 5))

            val viewModel = pending()

            viewModel.uiState.test {
                val state = awaitStateThat { it.loaded }

                assertEquals(listOf("Water", "Electricity"), state.rows.map { it.name })
                assertFalse("nothing typed yet", state.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a row left empty is left alone`() =
        runTest {
            val electricity = add("Electricity", start = LocalDate.of(2026, 3, 10))
            add("Water", start = LocalDate.of(2026, 3, 5))

            val viewModel = pending()
            viewModel.uiState.test {
                awaitStateThat { it.rows.size == 2 }
                viewModel.onTyped(electricity, "74.12")
                val state = awaitStateThat { it.answeredCount == 1 }

                assertTrue(state.canSave)
                viewModel.save()
                awaitStateThat { it.finished }
                cancelAndIgnoreRemainingEvents()
            }

            // Only the one that was typed. Reading the blank as zero would have recorded a
            // water bill of nothing, which is a figure rather than the absence of one.
            val written =
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .single()
            assertEquals(7_412L, written.amountMinor)
            assertEquals(LocalDate.of(2026, 3, 10), written.date)
        }

    @Test
    fun `skipping asks first, and then records nothing`() =
        runTest {
            add("Electricity", start = LocalDate.of(2026, 3, 10))

            val viewModel = pending()
            viewModel.uiState.test {
                val state = awaitStateThat { it.rows.isNotEmpty() }
                viewModel.requestSkip(state.rows.single())
                assertEquals("the question is asked", state.rows.single(), awaitItem().skipping)

                viewModel.confirmSkip()
                awaitStateThat { it.skipping == null }
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue(
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `answering the last one closes the screen`() =
        runTest {
            val id = add("Electricity", start = LocalDate.of(2026, 4, 10))

            val viewModel = pending()
            viewModel.uiState.test {
                awaitStateThat { it.rows.size == 1 }
                viewModel.onTyped(id, "74.12")
                awaitStateThat { it.canSave }
                viewModel.save()

                assertTrue(awaitStateThat { !it.saving }.finished)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `nothing waiting is an empty screen rather than a broken one`() =
        runTest {
            val viewModel = pending()

            viewModel.uiState.test {
                assertTrue(awaitStateThat { it.loaded }.isEmpty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun pending() = viewModels.keep(PendingChargesViewModel(repository(), clock))

    private fun repository() = RoomRecurringRuleRepository(database.recurringRuleDao(), database, clock)

    private suspend fun add(
        name: String,
        start: LocalDate,
    ): Long =
        repository().createRule(
            RecurringRule(
                id = 0,
                name = name,
                accountId = accountId,
                categoryId = null,
                kind = TransactionKind.EXPENSE,
                amount = Money(6_000, "EUR"),
                frequency = Frequency.MONTHLY,
                intervalDays = null,
                startDate = start,
                endDate = null,
                mode = RuleMode.CONFIRM,
                active = true,
                lastGenerated = null,
            ),
        )
}
