package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.export.BackupParse
import io.github.anderescaray.cents.data.export.BackupReader
import io.github.anderescaray.cents.data.export.BackupWriter
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.budget
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.entity.RulePostponementEntity
import io.github.anderescaray.cents.data.local.entity.RuleReminderEntity
import io.github.anderescaray.cents.data.local.group
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.quote
import io.github.anderescaray.cents.data.local.recurringRule
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.domain.model.BalanceChange
import io.github.anderescaray.cents.domain.model.Money
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
 * A backup is only a backup if it comes back.
 *
 * The round trip is the test that matters: write the file, read it, put it back, and check the
 * app holds what it held. It proves both halves at once, and it is the only test that would
 * catch the export and the import agreeing with each other while both being wrong about the
 * database — which is the failure that is invisible until the day it is needed.
 */
@RunWith(RobolectricTestRunner::class)
class RestoreTest {
    private lateinit var database: CentsDatabase
    private lateinit var backups: RoomBackupRepository

    private val clock = Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = createTestDatabase()
        backups = RoomBackupRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `everything written out comes back in`() =
        runTest {
            val original = populate()
            val text = BackupWriter.toJson(backups.snapshot())

            // The whole point, in three lines: empty the app, read the file, put it back.
            wipe()
            assertEquals(0, backups.snapshot().rowCount)
            backups.restore(readable(text))

            val restored = backups.snapshot()
            assertEquals(original.rowCount, restored.rowCount)
            assertEquals(original.accounts, restored.accounts)
            assertEquals(original.categories, restored.categories)
            assertEquals(original.transactions, restored.transactions)
            assertEquals(original.holdings, restored.holdings)
            assertEquals(original.trades, restored.trades)
            assertEquals(original.quotes, restored.quotes)
            assertEquals(original.rules, restored.rules)
            assertEquals(original.reminders, restored.reminders)
            assertEquals(original.budgets, restored.budgets)
            assertEquals(original.groups, restored.groups)
        }

    @Test
    fun `a movement comes back in its group`() =
        runTest {
            populate()
            val checking = database.accountDao().insert(account(name = "Travel card"))
            val lisbon =
                database.groupDao().insert(
                    group(
                        name = "Lisbon trip",
                        startDate = LocalDate.of(2026, 10, 2),
                        endDate = LocalDate.of(2026, 10, 6),
                    ),
                )
            database.transactionDao().insert(
                transaction(checking, amountMinor = 18_000, note = "Hotel", groupId = lisbon),
            )
            val text = BackupWriter.toJson(backups.snapshot())

            wipe()
            backups.restore(readable(text))

            val group = database.groupDao().all().single()
            assertEquals("Lisbon trip", group.name)
            assertEquals(LocalDate.of(2026, 10, 2), group.startDate)
            assertEquals(LocalDate.of(2026, 10, 6), group.endDate)
            val hotel =
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .single { it.note == "Hotel" }
            assertEquals("the hotel is still part of the trip", group.id, hotel.groupId)
        }

    @Test
    fun `subscriptions come back with their reminders, their watermark and the movements they wrote`() =
        runTest {
            populateRows()
            val checking =
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .first { it.name == "Checking" }
                    .id
            val ruleId =
                database.recurringRuleDao().insert(
                    recurringRule(checking, name = "Rent", lastGenerated = LocalDate.of(2026, 9, 1)),
                )
            database.ruleReminderDao().insert(RuleReminderEntity(ruleId = ruleId, offsetDays = -3))
            database.transactionDao().insert(transaction(checking, amountMinor = 80_000, ruleId = ruleId))
            val original = backups.snapshot()
            val text = BackupWriter.toJson(original)

            wipe()
            backups.restore(readable(text))

            val restored = backups.snapshot()
            assertEquals(original.rules, restored.rules)
            // The watermark is what stops the next launch writing every rent since January again.
            assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), restored.rules.single().lastGenerated)
            assertEquals(listOf(-3), restored.reminders.map { it.offsetDays })
            assertEquals(ruleId, restored.transactions.single { it.amountMinor == 80_000L }.ruleId)
        }

    @Test
    fun `a postponed charge comes back postponed`() =
        runTest {
            populateRows()
            val checking =
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .first { it.name == "Checking" }
                    .id
            val ruleId = database.recurringRuleDao().insert(recurringRule(checking, name = "Rent"))
            val moved =
                RulePostponementEntity(
                    ruleId = ruleId,
                    occurrence = LocalDate.of(2026, 10, 1),
                    postponedTo = LocalDate.of(2026, 10, 4),
                )
            database.rulePostponementDao().upsert(moved)
            val text = BackupWriter.toJson(backups.snapshot())

            wipe()
            backups.restore(readable(text))

            // Lost, it would quietly become a rent written on the 1st after all.
            assertEquals(listOf(moved), database.rulePostponementDao().all())
        }

    @Test
    fun `a corrected balance comes back corrected, on the day it was corrected`() =
        runTest {
            populateRows()
            val checking =
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .first { it.name == "Checking" }
                    .id
            RoomBalanceRepository(database, clock)
                .setBalance(
                    checking,
                    Money(150_000, "EUR"),
                    BalanceChange.CORRECT_FROM_TODAY,
                    LocalDate.of(2026, 10, 5),
                )
            val balanceBefore = database.accountDao().balanceOf(checking)
            val text = BackupWriter.toJson(backups.snapshot())

            wipe()
            backups.restore(readable(text))

            // Lost, the balance would quietly fall back to what the movements add up to.
            assertEquals(balanceBefore, database.accountDao().balanceOf(checking))
            val restored = database.balanceAdjustmentDao().all().single()
            assertEquals(checking, restored.accountId)
            assertEquals(LocalDate.of(2026, 10, 5), restored.date)
        }

    @Test
    fun `a phone with corrections can be restored from a file written before them`() =
        runTest {
            val text = BackupWriter.toJson(populate().copy(adjustments = emptyList()))
            val checking =
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .first { it.name == "Checking" }
                    .id
            RoomBalanceRepository(database, clock)
                .setBalance(checking, Money(1, "EUR"), BalanceChange.CORRECT_FROM_TODAY, LocalDate.of(2026, 10, 5))

            backups.restore(readable(text))

            // Replaced like everything else (D21), and not refused by the account they pointed at.
            assertTrue(database.balanceAdjustmentDao().all().isEmpty())
        }

    @Test
    fun `a phone that has subscriptions can still be restored`() =
        runTest {
            populate()
            val text = BackupWriter.toJson(backups.snapshot())
            val checking =
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .first { it.name == "Checking" }
                    .id
            // A subscription added after the backup: its account key is RESTRICT, and emptying
            // the accounts used to fail on it, refusing every restore on a phone that had one.
            val ruleId = database.recurringRuleDao().insert(recurringRule(checking, name = "Gym"))
            database.ruleReminderDao().insert(RuleReminderEntity(ruleId = ruleId, offsetDays = -1))

            backups.restore(readable(text))

            val restored = backups.snapshot()
            assertTrue(restored.rules.isEmpty())
            assertTrue(restored.reminders.isEmpty())
            assertEquals(2, restored.accounts.size)
        }

    @Test
    fun `a restored movement still points at its own account`() =
        runTest {
            populate()
            val text = BackupWriter.toJson(backups.snapshot())
            wipe()
            backups.restore(readable(text))

            val accounts =
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .associateBy { it.id }
            val movements = database.transactionDao().observeAll().first()

            // Ids are the edges of the graph. Renumbering on the way in would file a movement
            // under somebody else's account, and every balance in the app would be wrong in a
            // way nothing on screen would show.
            assertTrue(movements.all { accounts.containsKey(it.accountId) })
            assertEquals("Checking", accounts.getValue(movements.first().accountId).name)
        }

    @Test
    fun `balances after a restore are the balances before it`() =
        runTest {
            populate()
            val before =
                database
                    .accountDao()
                    .observeBalances()
                    .first()
                    .map { it.balanceMinor }
            val text = BackupWriter.toJson(backups.snapshot())

            wipe()
            backups.restore(readable(text))

            // The number the owner would actually check.
            assertEquals(
                before,
                database
                    .accountDao()
                    .observeBalances()
                    .first()
                    .map { it.balanceMinor },
            )
        }

    @Test
    fun `a transfer comes back as a transfer rather than as income and an expense`() =
        runTest {
            val checking = database.accountDao().insert(account(name = "Checking"))
            val savings = database.accountDao().insert(account(name = "Savings"))
            val dao = database.transactionDao()
            val outId = dao.insert(transaction(checking, amountMinor = 50_000))
            dao.setTransferId(id = outId, transferId = outId, updatedAt = clock.instant())
            dao.insert(
                transaction(savings, kind = TransactionKind.INCOME, amountMinor = 50_000, transferId = outId),
            )
            val text = BackupWriter.toJson(backups.snapshot())

            wipe()
            backups.restore(readable(text))

            // Without the pair id surviving, a restored ledger would show 500 € earned and
            // 500 € spent that never happened, and every report would be inflated.
            assertEquals(2, dao.findTransferLegs(outId).size)
            val from = LocalDate.of(2026, 1, 1)
            val to = LocalDate.of(2026, 12, 31)
            assertEquals(0L, dao.observeTotalsBetween(from, to).first().expenseMinor)
        }

    @Test
    fun `restoring replaces what was there rather than adding to it`() =
        runTest {
            populate()
            val text = BackupWriter.toJson(backups.snapshot())
            database.accountDao().insert(account(name = "Typed after the backup"))

            backups.restore(readable(text))

            // D21: a restore is not a merge. The account created after the file was written is
            // gone, which is what "replace everything" has to mean if the result is to be the
            // app as it was rather than a mixture of two ledgers.
            val names =
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .map { it.name }
                    .toSet()
            assertEquals(setOf("Broker", "Checking"), names)
        }

    @Test
    fun `a file from a newer app is refused rather than partly read`() {
        val parsed = BackupReader.parse("""{"schemaVersion":99,"accounts":[]}""", appSchemaVersion = 4)

        // A newer file may hold tables this version has never heard of. Restoring the parts it
        // recognises would produce something that looks complete and has lost the rest.
        assertEquals(BackupParse.TooNew(fileVersion = 99, appVersion = 4), parsed)
    }

    @Test
    fun `a file from an older app reads, with what did not exist yet left empty`() {
        // Written before transfers existed, so the movement carries no `transferId` at all.
        val v2 =
            """
            {
              "schemaVersion": 2,
              "exportedAt": "2026-09-01T10:00:00Z",
              "accounts": [{"id":1,"name":"Checking","type":"BANK","currency":"EUR",
                            "openingBalanceMinor":10000,"colorArgb":0,"archived":false,"sortOrder":0}],
              "categories": [],
              "transactions": [{"id":1,"accountId":1,"categoryId":null,"kind":"EXPENSE",
                                "amountMinor":2550,"currency":"EUR","date":20684,"note":null}],
              "holdings": [], "trades": [], "quotes": []
            }
            """.trimIndent()

        val parsed = BackupReader.parse(v2, appSchemaVersion = 4)

        val backup = (parsed as BackupParse.Restorable).backup
        assertEquals(2, backup.schemaVersion)
        // Absent is not damaged: a movement from before transfers is half of nothing, which is
        // exactly what null means here.
        assertNull(backup.transactions.single().transferId)
        assertNull(backup.transactions.single().tradeId)
        assertEquals(2_550L, backup.transactions.single().amountMinor)
        // Written before a backup carried subscriptions: none, rather than a damaged file.
        assertTrue(backup.rules.isEmpty())
        assertTrue(backup.reminders.isEmpty())
        assertNull(backup.transactions.single().ruleId)
        // And before groups: none, and a movement in none.
        assertTrue(backup.groups.isEmpty())
        assertNull(backup.transactions.single().groupId)
        // And before a balance could be corrected: none, so every balance is what it was.
        assertTrue(backup.adjustments.isEmpty())
    }

    @Test
    fun `the wrong file is named as the wrong file`() {
        assertEquals(BackupParse.NotABackup, BackupReader.parse("not json at all", 4))
        assertEquals(BackupParse.NotABackup, BackupReader.parse("""{"hello":"world"}""", 4))
        // JSON with a version but none of the shape: still not this app's backup.
        assertEquals(BackupParse.NotABackup, BackupReader.parse("""{"schemaVersion":4}""", 4))
    }

    @Test
    fun `a file missing an amount is damaged rather than restored as zero`() {
        val broken =
            """
            {"schemaVersion":4,"accounts":[],"categories":[],
             "transactions":[{"id":1,"accountId":1,"kind":"EXPENSE","currency":"EUR","date":20684}],
             "holdings":[],"trades":[],"quotes":[]}
            """.trimIndent()

        val parsed = BackupReader.parse(broken, 4)

        // Substituting a zero for a missing amount is the failure this refuses: money that is
        // quietly wrong is worse than a file that is honestly turned away.
        assertTrue(parsed.toString(), parsed is BackupParse.Damaged)
        assertTrue((parsed as BackupParse.Damaged).detail.contains("amountMinor"))
    }

    @Test
    fun `a restore that cannot finish leaves the app exactly as it was`() =
        runTest {
            populate()
            val before = backups.snapshot()

            // A movement pointing at an account the file does not contain: the foreign key
            // refuses it, and the whole transaction has to roll back rather than leaving the
            // app holding half of a stranger's ledger.
            val broken =
                before.copy(
                    transactions = before.transactions.map { it.copy(accountId = 9_999) },
                )
            runCatching { backups.restore(broken) }

            val after = backups.snapshot()
            assertEquals(before.accounts, after.accounts)
            assertEquals(before.transactions, after.transactions)
            assertEquals(before.rowCount, after.rowCount)
        }

    private fun readable(text: String) =
        (BackupReader.parse(text, CentsDatabase.VERSION) as BackupParse.Restorable).backup

    private suspend fun populate() = populateRows().let { backups.snapshot() }

    private suspend fun populateRows() {
        val checking = database.accountDao().insert(account(name = "Checking", openingBalanceMinor = 100_000))
        val broker = database.accountDao().insert(account(name = "Broker"))
        val groceries = database.categoryDao().insert(category(name = "Groceries"))
        database.budgetDao().insert(budget(groceries, amountMinor = 40_000))
        database.transactionDao().insert(
            transaction(checking, categoryId = groceries, amountMinor = 2_550, note = "Weekly shop"),
        )
        val holdingId = database.holdingDao().insert(holding(broker, symbol = "NVD.DE"))
        database.tradeDao().insert(trade(holdingId))
        database.priceQuoteDao().upsert(quote(symbol = "NVD.DE"))
    }

    private suspend fun wipe() {
        database.balanceAdjustmentDao().deleteAll()
        database.transactionDao().deleteAll()
        database.groupDao().deleteAll()
        database.budgetDao().deleteAll()
        database.rulePostponementDao().deleteAll()
        database.ruleReminderDao().deleteAll()
        database.recurringRuleDao().deleteAll()
        database.tradeDao().deleteAll()
        database.holdingDao().deleteAll()
        database.priceQuoteDao().deleteAll()
        database.categoryDao().deleteAll()
        database.accountDao().deleteAll()
    }
}
