package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.export.BackupParse
import io.github.anderescaray.cents.data.export.BackupReader
import io.github.anderescaray.cents.data.export.BackupWriter
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.backupRepository
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.CaptureOutcome
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Postponement
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
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
 * Capture recognising a subscription's charge (`DECISIONS.md` D35), against a real database. The
 * failure designed against is the one the owner reported: **one payment counted twice**, once by
 * the rule and once by the notification. The other failure, as bad and quieter, is a real payment
 * recorded nowhere.
 */
@RunWith(RobolectricTestRunner::class)
class CaptureMatchTest {
    private lateinit var database: CentsDatabase
    private var bank = 0L
    private var card = 0L

    @Before
    fun setUp() =
        runTest {
            database = createTestDatabase()
            bank = database.accountDao().insert(account(name = "Bank"))
            card = database.accountDao().insert(account(name = "Card"))
        }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a fixed charge already written is the payment, and nothing new is saved`() =
        runTest {
            val netflix = rules().createRule(netflix(start = OCT_15))
            rules().generateDue()

            val outcome = capture("Compra en NETFLIX por 12,99 €")

            val matched = outcome as CaptureOutcome.Matched
            assertEquals("Netflix", matched.subscription)
            assertEquals(false, matched.filledBill)
            assertEquals(Money(1_299, "EUR"), matched.amount)
            assertEquals("Bank", matched.accountName)
            val only = movements().single()
            assertEquals(netflix, only.ruleId)
            assertEquals(only.id, matched.chargeTransactionId)
        }

    @Test
    fun `a fixed charge not yet written is written once, by the match, and the rule writes nothing more`() =
        runTest {
            val netflix = rules().createRule(netflix(start = OCT_15))

            assertTrue(capture("Compra en NETFLIX por 12,99 €") is CaptureOutcome.Matched)
            assertEquals(0, rules().generateDue())

            val only = movements().single()
            assertEquals(netflix, only.ruleId)
            assertEquals(OCT_15, only.date)
            assertEquals(1_299L, only.amountMinor)
        }

    @Test
    fun `a charge notified before its day is written on its own day, and not again when the day comes`() =
        runTest {
            val netflix = rules().createRule(netflix(start = OCT_15.plusDays(2)))

            assertTrue(capture("Compra en NETFLIX por 12,99 €") is CaptureOutcome.Matched)
            assertEquals(0, rules(OCT_15.plusDays(2)).generateDue())

            val only = movements().single()
            assertEquals(netflix, only.ruleId)
            assertEquals(OCT_15.plusDays(2), only.date)
        }

    @Test
    fun `three days either side matches, and four does not`() =
        runTest {
            rules().createRule(netflix(start = OCT_15.minusDays(3)))
            rules().createRule(netflix(start = OCT_15.plusDays(4)).copy(name = "Too late"))

            val first = capture("Compra en NETFLIX por 12,99 €") as CaptureOutcome.Matched
            val second = capture("Compra en NETFLIX por 12,99 €", at = AT_10.plusSeconds(120))

            assertEquals("Netflix", first.subscription)
            assertTrue(second is CaptureOutcome.Recorded)
        }

    @Test
    fun `a fixed subscription matches only at its own amount`() =
        runTest {
            rules().createRule(netflix(start = OCT_15))

            assertTrue(capture("Compra en NETFLIX por 13,99 €") is CaptureOutcome.Recorded)
        }

    @Test
    fun `another account, another kind or a stopped subscription is not the charge`() =
        runTest {
            rules().createRule(netflix(start = OCT_15))
            rules().createRule(netflix(start = OCT_15, accountId = card).copy(name = "Card Netflix", active = false))

            val elsewhere = capture("Compra en NETFLIX por 12,99 €", app = WALLET_APP)
            val income = capture("Has recibido 12,99 € de NETFLIX", at = AT_10.plusSeconds(120))

            assertTrue(elsewhere is CaptureOutcome.Recorded)
            assertTrue(income is CaptureOutcome.Recorded)
        }

    @Test
    fun `one capture per charge, so a second payment inside the window is saved`() =
        runTest {
            rules().createRule(netflix(start = OCT_15))
            rules().generateDue()

            val first = capture("Compra en NETFLIX por 12,99 €")
            val second = capture("Compra en NETFLIX por 12,99 €", at = AT_10.plusSeconds(120))

            assertTrue(first is CaptureOutcome.Matched)
            assertTrue(second is CaptureOutcome.Recorded)
            assertEquals(2, movements().size)
        }

    @Test
    fun `the same payment announced twice in the same minute is still one, after a match`() =
        runTest {
            rules().createRule(netflix(start = OCT_15))
            val recorder = recorder()

            val first = recorder.record(BANK_APP, "Compra en NETFLIX por 12,99 €", AT_10)
            val again = recorder.record(BANK_APP, "Pago de 12,99 € en NETFLIX", AT_10.plusSeconds(20))

            assertTrue(first is CaptureOutcome.Matched)
            assertEquals(CaptureOutcome.Duplicate, again)
            assertEquals(1, movements().size)
        }

    @Test
    fun `of several charges the nearest in date wins`() =
        runTest {
            rules().createRule(netflix(start = OCT_15.minusDays(2)).copy(name = "Spotify"))
            rules().createRule(netflix(start = OCT_15.plusDays(1)).copy(name = "Disney"))

            val matched = capture("Compra por 12,99 €") as CaptureOutcome.Matched

            assertEquals("Disney", matched.subscription)
        }

    @Test
    fun `a postponed charge is found on the day it was moved to`() =
        runTest {
            val rent = rules().createRule(netflix(start = OCT_15.minusDays(7)).copy(name = "Rent"))
            // Moved a week, from the 8th to the 15th, while it was still to come.
            assertTrue(rules(OCT_15.minusDays(8)).postpone(rent, OCT_15.minusDays(7), OCT_15))

            val matched = capture("Compra por 12,99 €") as CaptureOutcome.Matched

            assertEquals("Rent", matched.subscription)
            assertEquals(OCT_15, movements().single().date)
            assertTrue(rules().observePostponements().first().isEmpty())
        }

    @Test
    fun `a variable bill takes the amount paid, on its own date`() =
        runTest {
            rules().createRule(electricity(start = OCT_15.minusDays(1)))

            val matched = capture("Recibo IBERDROLA, cargo de 63,20 €") as CaptureOutcome.Matched

            assertEquals(true, matched.filledBill)
            val bill = movements().single()
            assertEquals(6_320L, bill.amountMinor)
            assertEquals(OCT_15.minusDays(1), bill.date)
            assertTrue("the bill is answered, so it waits no more", rules().observePending().first().isEmpty())
        }

    @Test
    fun `a variable bill already answered is a movement like any other`() =
        runTest {
            val electricity = rules().createRule(electricity(start = OCT_15.minusDays(1)))
            rules().recordPending(electricity, OCT_15.minusDays(1), Money(6_000, "EUR"))

            assertTrue(capture("Recibo IBERDROLA, cargo de 63,20 €") is CaptureOutcome.Recorded)
            assertEquals(2, movements().size)
        }

    @Test
    fun `only the oldest waiting bill can be filled, because the rule counts with one date`() =
        runTest {
            // September's bill still waits, outside the window, so October's cannot be answered.
            rules().createRule(electricity(start = OCT_15.minusMonths(1).minusDays(1)))

            assertTrue(capture("Recibo IBERDROLA, cargo de 63,20 €") is CaptureOutcome.Recorded)
            assertEquals(
                OCT_15.minusMonths(1).minusDays(1),
                rules()
                    .observePending()
                    .first()
                    .single()
                    .dueDate,
            )
        }

    @Test
    fun `a salary is a subscription too`() =
        runTest {
            rules().createRule(
                electricity(
                    start = OCT_15,
                ).copy(name = "Salary", kind = TransactionKind.INCOME, amount = Money(150_000, "EUR")),
            )

            val matched = capture("Has recibido 1.520,00 € de ACME") as CaptureOutcome.Matched

            assertEquals("Salary", matched.subscription)
            assertEquals(152_000L, movements().single().amountMinor)
            assertEquals(TransactionKind.INCOME, movements().single().kind)
        }

    @Test
    fun `undo saves the payment as an ordinary capture and leaves a charge the rule wrote`() =
        runTest {
            val netflix = rules().createRule(netflix(start = OCT_15))
            rules().generateDue()
            val recorder = recorder()
            val matched = recorder.record(BANK_APP, "Compra en NETFLIX por 12,99 €", AT_10) as CaptureOutcome.Matched

            val saved = requireNotNull(recorder.undoMatch(matched.matchId))

            val capture = requireNotNull(database.transactionDao().findById(saved.transactionId))
            assertNull(capture.ruleId)
            assertNull(capture.categoryId)
            assertEquals("Compra en NETFLIX por 12,99 €", capture.note)
            assertEquals(1_299L, capture.amountMinor)
            assertEquals("Bank", saved.accountName)
            assertEquals(1, movements().count { it.ruleId == netflix })
        }

    @Test
    fun `undo takes back a charge the match wrote, and the rule writes it on its day as before`() =
        runTest {
            val netflix = rules().createRule(netflix(start = OCT_15.plusDays(2)))
            val recorder = recorder()
            val matched = recorder.record(BANK_APP, "Compra en NETFLIX por 12,99 €", AT_10) as CaptureOutcome.Matched

            recorder.undoMatch(matched.matchId)

            assertNull(rules().findById(netflix)?.lastGenerated)
            assertEquals(listOf<Long?>(null), movements().map { it.ruleId })
            assertEquals(1, rules(OCT_15.plusDays(2)).generateDue())
        }

    @Test
    fun `undo puts a variable bill back to waiting, on the day it had been moved to`() =
        runTest {
            val electricity = rules().createRule(electricity(start = OCT_15.minusDays(7)))
            assertTrue(rules(OCT_15.minusDays(8)).postpone(electricity, OCT_15.minusDays(7), OCT_15))
            val recorder = recorder()
            val matched =
                recorder.record(
                    BANK_APP,
                    "Recibo IBERDROLA, cargo de 63,20 €",
                    AT_10,
                ) as CaptureOutcome.Matched

            recorder.undoMatch(matched.matchId)

            val waiting = rules().observePending().first().single()
            assertEquals(OCT_15.minusDays(7), waiting.dueDate)
            assertEquals(OCT_15, waiting.postponedTo)
            assertEquals(
                listOf(Postponement(electricity, OCT_15.minusDays(7), OCT_15)),
                rules().observePostponements().first(),
            )
            assertEquals(listOf(6_320L), movements().map { it.amountMinor })
            assertNull(movements().single().ruleId)
        }

    @Test
    fun `a rule that has not caught up is caught up first, so its charge is still recognised`() =
        runTest {
            // September and October have both fallen due, and the app has not been opened.
            val netflix = rules().createRule(netflix(start = OCT_15.minusMonths(1)))

            assertTrue(capture("Compra en NETFLIX por 12,99 €") is CaptureOutcome.Matched)

            assertEquals(listOf(OCT_15.minusMonths(1), OCT_15), movements().map { it.date }.sorted())
            assertTrue(movements().all { it.ruleId == netflix })
        }

    @Test
    fun `nothing is matched when the match could not be announced with Undo`() =
        runTest {
            rules().createRule(netflix(start = OCT_15.plusDays(2)))

            val outcome = recorder().record(BANK_APP, "Compra en NETFLIX por 12,99 €", AT_10, announced = false)

            assertTrue(outcome is CaptureOutcome.Recorded)
            assertEquals(listOf<Long?>(null), movements().map { it.ruleId })
            assertNull(
                rules()
                    .observeRules()
                    .first()
                    .single()
                    .lastGenerated,
            )
        }

    @Test
    fun `undo leaves a charge the rule has moved past, so that occurrence is not lost`() =
        runTest {
            val netflix = rules().createRule(netflix(start = OCT_15.plusDays(2)))
            val recorder = recorder()
            val matched = recorder.record(BANK_APP, "Compra en NETFLIX por 12,99 €", AT_10) as CaptureOutcome.Matched
            // A month on, November has been written.
            assertEquals(1, rules(OCT_15.plusMonths(1).plusDays(2)).generateDue())

            recorder.undoMatch(matched.matchId)

            assertEquals(
                listOf(OCT_15.plusDays(2), OCT_15.plusMonths(1).plusDays(2)),
                movements().filter { it.ruleId == netflix }.map { it.date }.sorted(),
            )
            assertEquals(1, movements().count { it.ruleId == null })
        }

    @Test
    fun `a charge deleted and put back is still matched, so a second payment is saved`() =
        runTest {
            rules().createRule(netflix(start = OCT_15))
            rules().generateDue()
            assertTrue(capture("Compra en NETFLIX por 12,99 €") is CaptureOutcome.Matched)
            val charge = movements().single()

            // Deleted from the list, then its Undo: the same row comes back with the same id.
            database.transactionDao().deleteById(charge.id)
            database.transactionDao().insert(charge)

            assertTrue(capture("Compra en NETFLIX por 12,99 €", at = AT_10.plusSeconds(120)) is CaptureOutcome.Recorded)
        }

    @Test
    fun `editing a subscription keeps the charge a match wrote while the form was open`() =
        runTest {
            val netflix = rules().createRule(netflix(start = OCT_15.plusDays(2)))
            // The form read the rule before the payment arrived.
            val read = requireNotNull(rules().findById(netflix))
            assertTrue(capture("Compra en NETFLIX por 12,99 €") is CaptureOutcome.Matched)

            rules().updateRule(read.copy(name = "Netflix Premium"))

            assertEquals(OCT_15.plusDays(2), rules().findById(netflix)?.lastGenerated)
            assertEquals(0, rules(OCT_15.plusDays(2)).generateDue())
            assertEquals(1, movements().size)
        }

    @Test
    fun `undoing twice saves the payment once`() =
        runTest {
            rules().createRule(netflix(start = OCT_15))
            val recorder = recorder()
            val matched = recorder.record(BANK_APP, "Compra en NETFLIX por 12,99 €", AT_10) as CaptureOutcome.Matched

            recorder.undoMatch(matched.matchId)
            val again = recorder.undoMatch(matched.matchId)

            assertNull(again)
            assertEquals(1, movements().count { it.ruleId == null })
        }

    @Test
    fun `a match is kept through a backup and restore, so undo still works`() =
        runTest {
            rules().createRule(netflix(start = OCT_15))
            val recorder = recorder()
            val matched = recorder.record(BANK_APP, "Compra en NETFLIX por 12,99 €", AT_10) as CaptureOutcome.Matched
            val backups = backupRepository(database, Clock.fixed(AT_10, ZoneOffset.UTC))

            // Through the file itself, as a restore reads it.
            val text = BackupWriter.toJson(backups.snapshot())
            val backup = (BackupReader.parse(text, CentsDatabase.VERSION) as BackupParse.Restorable).backup
            backups.restore(backup)

            assertEquals(1, backup.captureMatches.size)
            assertTrue(recorder.undoMatch(matched.matchId) != null)
            assertEquals(1, movements().count { it.ruleId == null })
        }

    private suspend fun capture(
        text: String,
        at: Instant = AT_10,
        app: String = BANK_APP,
    ): CaptureOutcome = recorder().record(app, text, at)

    private fun recorder(): RoomCaptureRecorder {
        val clock = Clock.fixed(AT_10, ZoneOffset.UTC)
        return RoomCaptureRecorder(
            settings =
                FakeCaptureSettings(
                    CaptureSettings(
                        enabled = true,
                        sources =
                            listOf(
                                CaptureSource(BANK_APP, "My bank", bank),
                                CaptureSource(WALLET_APP, "Wallet", card),
                            ),
                    ),
                ),
            accountDao = database.accountDao(),
            transactionDao = database.transactionDao(),
            matcher = CaptureMatcher(database, ruleRepository(database, clock), clock),
            clock = clock,
        )
    }

    private fun rules(today: LocalDate = OCT_15) =
        RoomRecurringRuleRepository(
            database.recurringRuleDao(),
            database.ruleReminderDao(),
            database,
            Clock.fixed(today.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
        )

    private suspend fun movements(): List<TransactionEntity> = database.transactionDao().observeAll().first()

    private fun netflix(
        start: LocalDate,
        accountId: Long = bank,
    ) = RecurringRule(
        id = 0,
        name = "Netflix",
        accountId = accountId,
        categoryId = null,
        kind = TransactionKind.EXPENSE,
        amount = Money(1_299, "EUR"),
        frequency = Frequency.MONTHLY,
        intervalDays = null,
        startDate = start,
        endDate = null,
        mode = RuleMode.AUTOMATIC,
        active = true,
        lastGenerated = null,
    )

    private fun electricity(start: LocalDate) =
        netflix(start).copy(name = "Electricity", amount = Money(6_000, "EUR"), mode = RuleMode.CONFIRM)

    private companion object {
        const val BANK_APP = "es.example.bank"
        const val WALLET_APP = "com.google.android.apps.walletnfcrel"
        val OCT_15: LocalDate = LocalDate.of(2026, 10, 15)
        val AT_10: Instant = Instant.parse("2026-10-15T10:00:30Z")
    }
}
