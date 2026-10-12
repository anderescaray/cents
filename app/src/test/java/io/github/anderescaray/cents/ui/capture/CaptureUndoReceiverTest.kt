package io.github.anderescaray.cents.ui.capture

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.repository.CaptureMatcher
import io.github.anderescaray.cents.data.repository.FakeCaptureSettings
import io.github.anderescaray.cents.data.repository.RoomCaptureRecorder
import io.github.anderescaray.cents.data.repository.RoomRecurringRuleRepository
import io.github.anderescaray.cents.data.repository.ruleRepository
import io.github.anderescaray.cents.domain.model.CaptureOutcome
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.CaptureRecorder
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Undo on a capture that matched a subscription (`DECISIONS.md` D35), as the notification's action
 * runs it with the app closed: the payment saved as an ordinary capture, and the notification
 * turned into the one an ordinary capture shows.
 */
@RunWith(RobolectricTestRunner::class)
class CaptureUndoReceiverTest {
    private lateinit var context: Application
    private lateinit var manager: NotificationManager
    private lateinit var database: CentsDatabase
    private lateinit var dependencies: CaptureUndoReceiver.Dependencies
    private var bank = 0L

    @Before
    fun setUp() =
        runTest {
            context = ApplicationProvider.getApplicationContext()
            manager = context.getSystemService(NotificationManager::class.java)
            shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            shadowOf(manager).setNotificationsEnabled(true)
            database = createTestDatabase()
            bank = database.accountDao().insert(account(name = "Bank"))
            val clock = Clock.fixed(AT_10, ZoneOffset.UTC)
            val settings =
                FakeCaptureSettings(
                    CaptureSettings(enabled = true, sources = listOf(CaptureSource(BANK_APP, "My bank", bank))),
                )
            val recorder =
                RoomCaptureRecorder(
                    settings = settings,
                    accountDao = database.accountDao(),
                    transactionDao = database.transactionDao(),
                    matcher = CaptureMatcher(database, ruleRepository(database, clock), clock),
                    clock = clock,
                )
            val notifier = CaptureNotifier(context)
            dependencies =
                object : CaptureUndoReceiver.Dependencies {
                    override fun recorder(): CaptureRecorder = recorder

                    override fun settings(): CaptureSettingsRepository = settings

                    override fun captureNotifier(): CaptureNotifier = notifier
                }
        }

    @After
    fun tearDown() {
        manager.cancelAll()
        database.close()
    }

    @Test
    fun `undo saves the payment and the notification becomes an ordinary capture's`() =
        runTest {
            val matched = matchNetflix()

            CaptureUndoReceiver.undo(dependencies, matched.matchId)

            val saved =
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .single { it.ruleId == null }
            assertEquals(1_299L, saved.amountMinor)
            assertNull(saved.categoryId)
            val notification = manager.activeNotifications.single().notification
            assertEquals("Expense added", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals(
                saved.id,
                shadowOf(notification.contentIntent).savedIntent.getLongExtra(CaptureNotifier.EXTRA_OPEN_CAPTURED, 0L),
            )
        }

    @Test
    fun `a second tap finds nothing to undo and saves nothing more`() =
        runTest {
            val matched = matchNetflix()

            CaptureUndoReceiver.undo(dependencies, matched.matchId)
            manager.cancelAll()
            CaptureUndoReceiver.undo(dependencies, matched.matchId)

            assertEquals(
                1,
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .count { it.ruleId == null },
            )
            assertEquals(0, manager.activeNotifications.size)
        }

    /** Netflix due today, its payment captured, and the match's notification on screen. */
    private suspend fun matchNetflix(): CaptureOutcome.Matched {
        RoomRecurringRuleRepository(
            database.recurringRuleDao(),
            database.ruleReminderDao(),
            database,
            Clock.fixed(AT_10, ZoneOffset.UTC),
        ).createRule(
            RecurringRule(
                id = 0,
                name = "Netflix",
                accountId = bank,
                categoryId = null,
                kind = TransactionKind.EXPENSE,
                amount = Money(1_299, "EUR"),
                frequency = Frequency.MONTHLY,
                intervalDays = null,
                startDate = LocalDate.of(2026, 10, 15),
                endDate = null,
                mode = RuleMode.AUTOMATIC,
                active = true,
                lastGenerated = null,
            ),
        )
        val matched =
            dependencies.recorder().record(
                BANK_APP,
                "Compra en NETFLIX por 12,99 €",
                AT_10,
            ) as CaptureOutcome.Matched
        dependencies.captureNotifier().notify(matched, enabled = true)
        assertEquals(1, manager.activeNotifications.size)
        return matched
    }

    private companion object {
        const val BANK_APP = "es.example.bank"
        val AT_10: Instant = Instant.parse("2026-10-15T10:00:30Z")
    }
}
