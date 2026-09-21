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
import io.github.anderescaray.cents.ui.feature.recurring.SubscriptionsUiState
import io.github.anderescaray.cents.ui.feature.recurring.SubscriptionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The total is the screen, so the total is the test.
 *
 * A list of subscriptions says nothing the movement list does not already say; "these come to
 * 84 € a month" is the thing nobody works out by hand (PRD §4.4). Everything below is about
 * that figure being right, or about it honestly refusing to exist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SubscriptionsViewModelTest {
    private lateinit var database: CentsDatabase
    private val viewModels = ViewModelKeeper()
    private var accountId: Long = 0

    private val clock = Clock.fixed(Instant.parse("2026-09-17T09:00:00Z"), ZoneOffset.UTC)

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
    fun `a yearly subscription is worth a twelfth of itself each month`() =
        runTest {
            // The case the screen exists for: 120 € once a year is invisible in a month's
            // spending and is 10 € a month of commitment.
            add(name = "Domain", minor = 12_000, frequency = Frequency.YEARLY)

            val state = firstLoaded()

            assertEquals(Money(1_000, "EUR"), state.monthlyOutgoing)
            assertEquals(Money(12_000, "EUR"), state.yearlyOutgoing)
        }

    @Test
    fun `a weekly charge counts 52 and a seventh times a year, not 48`() =
        runTest {
            // 10 € a week is 521,43 € a year, not 40 € a month. Treating a week as a quarter of
            // a month loses a month and a half of it.
            add(name = "Coffee", minor = 1_000, frequency = Frequency.WEEKLY)

            val state = firstLoaded()

            assertEquals(Money(52_143, "EUR"), state.yearlyOutgoing)
            assertEquals(Money(4_345, "EUR"), state.monthlyOutgoing)
        }

    @Test
    fun `several subscriptions add up`() =
        runTest {
            add(name = "Netflix", minor = 1_299, frequency = Frequency.MONTHLY)
            add(name = "Rent", minor = 75_000, frequency = Frequency.MONTHLY)

            assertEquals(Money(76_299, "EUR"), firstLoaded().monthlyOutgoing)
        }

    @Test
    fun `a salary is listed but left out of the total`() =
        runTest {
            add(name = "Netflix", minor = 1_299, frequency = Frequency.MONTHLY)
            add(
                name = "Salary",
                minor = 250_000,
                frequency = Frequency.MONTHLY,
                kind = TransactionKind.INCOME,
            )

            val state = firstLoaded()

            // Income repeats the same way (PRD §4.4), so it belongs on the screen. Netting it
            // off the total would produce a cheerful number answering no question: what is
            // wanted here is what is going out.
            assertEquals(2, state.active.size)
            assertEquals(Money(1_299, "EUR"), state.monthlyOutgoing)
        }

    @Test
    fun `a stopped subscription stops counting, and stays on the screen`() =
        runTest {
            val id = add(name = "Netflix", minor = 1_299, frequency = Frequency.MONTHLY)
            add(name = "Rent", minor = 75_000, frequency = Frequency.MONTHLY)

            val viewModel = subscriptions()
            viewModel.setActive(id, active = false)

            viewModel.uiState.test {
                val state = awaitStateThat { it.stopped.isNotEmpty() }

                assertEquals(Money(75_000, "EUR"), state.monthlyOutgoing)
                assertEquals(
                    "Netflix",
                    state.stopped
                        .single()
                        .rule.name,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `amounts in different currencies refuse to be added rather than being added wrongly`() =
        runTest {
            add(name = "Netflix", minor = 1_299, frequency = Frequency.MONTHLY)
            add(name = "Hosting", minor = 900, frequency = Frequency.MONTHLY, currency = "USD")

            // Rule 2: a currency is part of an amount. 12,99 € plus 9,00 $ is not 21,99 of
            // anything, and no conversion exists (D2) — so the screen says so instead.
            assertNull(firstLoaded().monthlyOutgoing)
        }

    @Test
    fun `each row says when it next comes round`() =
        runTest {
            add(
                name = "Netflix",
                minor = 1_299,
                frequency = Frequency.MONTHLY,
                start = LocalDate.of(2026, 9, 5),
            )

            val row = firstLoaded().active.single()

            assertEquals(LocalDate.of(2026, 10, 5), row.nextDue)
        }

    @Test
    fun `a rule past its end date has nothing left to come`() =
        runTest {
            add(
                name = "Gym",
                minor = 3_000,
                frequency = Frequency.MONTHLY,
                start = LocalDate.of(2026, 1, 10),
                end = LocalDate.of(2026, 6, 10),
            )

            assertNull(firstLoaded().active.single().nextDue)
        }

    @Test
    fun `no rules at all is an empty screen rather than a zero`() =
        runTest {
            assertTrue(firstLoaded().isEmpty)
        }

    @Test
    fun `the state includes commitments and the monthly income share`() =
        runTest {
            add(name = "Netflix", minor = 1_299, frequency = Frequency.MONTHLY, start = LocalDate.of(2026, 9, 30))
            add(name = "Salary", minor = 250_000, frequency = Frequency.MONTHLY, kind = TransactionKind.INCOME)

            val state = firstLoaded()
            val share = requireNotNull(state.totals.incomeShare)

            assertEquals(Money(1_299, "EUR"), state.totals.committedThisMonth)
            assertEquals(state.monthlyOutgoing, share.monthlyExpenses)
            assertEquals(Money(250_000, "EUR"), share.monthlyIncome)
        }

    @Test
    fun `generating today's charge updates commitments without a reload`() =
        runTest {
            add(name = "Weekly", minor = 1_000, frequency = Frequency.WEEKLY, start = LocalDate.of(2026, 9, 17))

            subscriptions().uiState.test {
                val before = awaitStateThat { !it.isLoading }
                assertEquals(Money(2_000, "EUR"), before.totals.committedThisMonth)

                repository().generateDue()

                val after = awaitStateThat { it.totals.committedThisMonth == Money(1_000, "EUR") }
                assertEquals(before.monthlyOutgoing, after.monthlyOutgoing)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recording and skipping variable bills remove only the answered occurrences`() =
        runTest {
            val id =
                add(
                    name = "Electricity",
                    minor = 6_000,
                    frequency = Frequency.MONTHLY,
                    start = LocalDate.of(2026, 7, 17),
                    mode = RuleMode.CONFIRM,
                )

            subscriptions().uiState.test {
                val before = awaitStateThat { !it.isLoading }
                assertEquals(Money(18_000, "EUR"), before.totals.committedThisMonth)
                assertEquals(2, before.pending.single().laterCount)
                assertTrue(before.totals.committedIncludesEstimates)

                assertTrue(repository().recordPending(id, LocalDate.of(2026, 7, 17), Money(7_412, "EUR")))
                awaitStateThat { it.totals.committedThisMonth == Money(12_000, "EUR") }
                assertTrue(repository().skipPending(id, LocalDate.of(2026, 8, 17)))
                awaitStateThat { it.totals.committedThisMonth == Money(6_000, "EUR") }
                assertTrue(repository().recordPending(id, LocalDate.of(2026, 9, 17), Money(5_980, "EUR")))

                val after = awaitStateThat { it.totals.committedThisMonth == Money.zero("EUR") && it.pending.isEmpty() }
                assertFalse(after.totals.committedIncludesEstimates)
                assertTrue(after.includesEstimates)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `resuming in a new month recalculates even when Room has not changed`() =
        runTest {
            var now = Instant.parse("2026-09-30T23:00:00Z")
            val movingClock =
                object : Clock() {
                    override fun instant(): Instant = now

                    override fun getZone(): ZoneId = ZoneOffset.UTC

                    override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
                }
            add(name = "Rent", minor = 75_000, frequency = Frequency.MONTHLY, start = LocalDate.of(2026, 10, 1))
            val viewModel = subscriptions(movingClock)

            viewModel.uiState.test {
                assertEquals(Money.zero("EUR"), awaitStateThat { !it.isLoading }.totals.committedThisMonth)

                now = Instant.parse("2026-10-01T08:00:00Z")
                viewModel.refreshDate()

                val after = awaitStateThat { it.today == LocalDate.of(2026, 10, 1) }
                assertEquals(Money(75_000, "EUR"), after.totals.committedThisMonth)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun firstLoaded(): SubscriptionsUiState {
        lateinit var loaded: SubscriptionsUiState
        subscriptions().uiState.test {
            loaded = awaitStateThat { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        return loaded
    }

    private fun subscriptions(clock: Clock = this.clock) = viewModels.keep(SubscriptionsViewModel(repository(), clock))

    private fun repository() =
        RoomRecurringRuleRepository(database.recurringRuleDao(), database.ruleReminderDao(), database, clock)

    private suspend fun add(
        name: String,
        minor: Long,
        frequency: Frequency,
        kind: TransactionKind = TransactionKind.EXPENSE,
        currency: String = "EUR",
        start: LocalDate = LocalDate.of(2026, 9, 1),
        end: LocalDate? = null,
        mode: RuleMode = RuleMode.AUTOMATIC,
    ): Long =
        repository().createRule(
            RecurringRule(
                id = 0,
                name = name,
                accountId = accountId,
                categoryId = null,
                kind = kind,
                amount = Money(minor, currency),
                frequency = frequency,
                intervalDays = null,
                startDate = start,
                endDate = end,
                mode = mode,
                active = true,
                lastGenerated = null,
            ),
        )
}
