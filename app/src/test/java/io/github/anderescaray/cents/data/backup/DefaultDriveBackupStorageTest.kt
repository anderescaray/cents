package io.github.anderescaray.cents.data.backup

import io.github.anderescaray.cents.data.export.BackupParse
import io.github.anderescaray.cents.data.export.BackupReader
import io.github.anderescaray.cents.data.export.BackupWriter
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.category
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.holding
import io.github.anderescaray.cents.data.local.quote
import io.github.anderescaray.cents.data.local.trade
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomBackupRepository
import io.github.anderescaray.cents.domain.model.DriveBackup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
class DefaultDriveBackupStorageTest {
    private lateinit var database: CentsDatabase
    private lateinit var backups: RoomBackupRepository
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val later = Clock.fixed(NOW.plus(1, ChronoUnit.DAYS), ZoneOffset.UTC)
    private val drive = MemoryDriveFiles()

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
    fun `a snapshot travels through the existing writer and reader unchanged`() =
        runTest {
            val accountId = database.accountDao().insert(account(name = "Checking \"daily\""))
            val categoryId = database.categoryDao().insert(category(name = "Food, drink"))
            val holdingId = database.holdingDao().insert(holding(accountId, symbol = "NVD.DE"))
            val tradeId = database.tradeDao().insert(trade(holdingId, quantityScaled = 123_456_789))
            database.transactionDao().insert(
                transaction(
                    accountId,
                    categoryId,
                    amountMinor = 9_007_199_254_740_991,
                    note = "A\nB",
                    tradeId = tradeId,
                ),
            )
            database.priceQuoteDao().upsert(quote("NVD.DE"))
            val snapshot = backups.snapshot()
            val storage = DefaultDriveBackupStorage(backups, drive)

            storage.create("token")
            val uploaded = storage.list("another-token").single()
            val downloaded = storage.download("fresh-token", uploaded.id)

            assertEquals("cents-backup-2026-09-22T031245.json", uploaded.name)
            assertEquals(BackupWriter.toJson(snapshot), downloaded)
            assertEquals(BackupParse.Restorable(snapshot), BackupReader.parse(downloaded, CentsDatabase.VERSION))
            assertEquals(listOf("list", "upload", "list", "download"), drive.operations)
            assertEquals(fingerprintOf(snapshot), uploaded.fingerprint)
        }

    @Test
    fun `the first upload leaves its only copy alone`() =
        runTest {
            DefaultDriveBackupStorage(backups, drive).create("token")

            assertTrue(drive.deleted.isEmpty())
            assertEquals(1, drive.files.size)
        }

    @Test
    fun `older daily copies lose only the ones retention no longer needs`() =
        runTest {
            drive.seed(9)

            DefaultDriveBackupStorage(backups, drive).create("token")

            // Kept: the new copy and the six days before it, and old-9 as the newest of its
            // week (2026-09-07 to 09-13).
            assertEquals(listOf("old-7", "old-8"), drive.deleted)
            assertEquals(listOf("list", "upload", "delete", "delete"), drive.operations)
        }

    @Test
    fun `a failed upload deletes nothing`() =
        runTest {
            drive.seed(9)
            drive.failUpload = true

            val failure = runCatching { DefaultDriveBackupStorage(backups, drive).create("token") }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertTrue(drive.deleted.isEmpty())
            assertEquals(9, drive.files.size)
            assertEquals(listOf("list", "upload"), drive.operations)
        }

    @Test
    fun `a listing that fails uploads nothing and deletes nothing`() =
        runTest {
            drive.seed(9)
            drive.failList = true

            val failure = runCatching { DefaultDriveBackupStorage(backups, drive).create("token") }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertTrue(drive.deleted.isEmpty())
            assertEquals(9, drive.files.size)
        }

    @Test
    fun `a failed clean-up does not fail a backup that is already safe`() =
        runTest {
            drive.seed(9)
            drive.failDelete = true
            val storage = DefaultDriveBackupStorage(backups, drive)

            storage.create("token")

            assertEquals(10, drive.files.size)
            assertEquals(listOf("list", "upload", "delete"), drive.operations)
        }

    @Test
    fun `nothing is uploaded when the newest copy already holds the same ledger`() =
        runTest {
            database.accountDao().insert(account(name = "Checking"))
            val storage = DefaultDriveBackupStorage(backups, drive)
            storage.create("token")
            drive.operations.clear()

            // A later snapshot differs only in when it was taken.
            DefaultDriveBackupStorage(RoomBackupRepository(database, later), drive).create("token")

            assertEquals(listOf("list"), drive.operations)
            assertEquals(1, drive.files.size)
        }

    @Test
    fun `a changed ledger is uploaded even when a copy exists`() =
        runTest {
            val storage = DefaultDriveBackupStorage(backups, drive)
            storage.create("token")
            database.accountDao().insert(account(name = "Savings"))

            DefaultDriveBackupStorage(RoomBackupRepository(database, later), drive).create("token")

            assertEquals(2, drive.files.size)
        }
}

private class MemoryDriveFiles : DriveFiles {
    val files = mutableListOf<DriveBackup>()
    val deleted = mutableListOf<String>()
    val operations = mutableListOf<String>()
    private val contents = mutableMapOf<String, String>()
    var failUpload = false
    var failList = false
    var failDelete = false

    fun seed(count: Int) {
        files +=
            (1..count).map {
                DriveBackup("old-$it", "old.json", NOW.minus(it.toLong(), ChronoUnit.DAYS), 10)
            }
    }

    override suspend fun upload(
        accessToken: String,
        name: String,
        contents: String,
        fingerprint: String,
    ): String {
        operations += "upload"
        if (failUpload) throw IOException("offline")
        val id = "new-${files.size}"
        files += DriveBackup(id, name, NOW, contents.toByteArray().size.toLong(), fingerprint)
        this.contents[id] = contents
        return id
    }

    override suspend fun list(accessToken: String): List<DriveBackup> {
        operations += "list"
        if (failList) throw IOException("offline")
        return files.toList()
    }

    override suspend fun download(
        accessToken: String,
        id: String,
    ): String {
        operations += "download"
        return contents.getValue(id)
    }

    override suspend fun delete(
        accessToken: String,
        id: String,
    ) {
        operations += "delete"
        if (failDelete) throw IOException("offline")
        deleted += id
        files.removeAll { it.id == id }
        contents.remove(id)
    }
}

private val NOW: Instant = Instant.parse("2026-09-22T03:12:45Z")
