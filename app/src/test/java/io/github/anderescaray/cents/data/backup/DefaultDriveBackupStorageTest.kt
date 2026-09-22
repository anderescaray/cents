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

@RunWith(RobolectricTestRunner::class)
class DefaultDriveBackupStorageTest {
    private lateinit var database: CentsDatabase
    private lateinit var backups: RoomBackupRepository
    private val clock = Clock.fixed(Instant.parse("2026-09-22T03:12:45Z"), ZoneOffset.UTC)
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
            assertEquals(listOf("upload", "list", "list", "download"), drive.operations)
        }

    @Test
    fun `the first upload leaves its only copy alone`() =
        runTest {
            DefaultDriveBackupStorage(backups, drive).create("token")

            assertTrue(drive.deleted.isEmpty())
            assertEquals(1, drive.files.size)
        }

    @Test
    fun `seven existing copies lose only the oldest after an eighth arrives`() =
        runTest {
            drive.seed(7)

            DefaultDriveBackupStorage(backups, drive).create("token")

            assertEquals(listOf("old-1"), drive.deleted)
            assertEquals(7, drive.files.size)
            assertEquals("upload", drive.operations.first())
        }

    @Test
    fun `nine existing copies lose three old copies after the new one succeeds`() =
        runTest {
            drive.seed(9)

            DefaultDriveBackupStorage(backups, drive).create("token")

            assertEquals(listOf("old-3", "old-2", "old-1"), drive.deleted)
            assertEquals(7, drive.files.size)
            assertEquals(listOf("upload", "list", "delete", "delete", "delete"), drive.operations)
        }

    @Test
    fun `a failed upload never lists or deletes existing copies`() =
        runTest {
            drive.seed(9)
            drive.failUpload = true

            val failure = runCatching { DefaultDriveBackupStorage(backups, drive).create("token") }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertTrue(drive.deleted.isEmpty())
            assertEquals(9, drive.files.size)
            assertEquals(listOf("upload"), drive.operations)
        }

    @Test
    fun `an incomplete listing never starts retention`() =
        runTest {
            drive.seed(9)
            drive.failList = true

            val failure = runCatching { DefaultDriveBackupStorage(backups, drive).create("token") }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertTrue(drive.deleted.isEmpty())
            assertEquals(10, drive.files.size)
        }
}

private class MemoryDriveFiles : DriveFiles {
    val files = mutableListOf<DriveBackup>()
    val deleted = mutableListOf<String>()
    val operations = mutableListOf<String>()
    private val contents = mutableMapOf<String, String>()
    var failUpload = false
    var failList = false

    fun seed(count: Int) {
        files +=
            (1..count).map {
                DriveBackup("old-$it", "old.json", Instant.EPOCH.plusSeconds(it.toLong()), 10)
            }
    }

    override suspend fun upload(
        accessToken: String,
        name: String,
        contents: String,
    ): String {
        operations += "upload"
        if (failUpload) throw IOException("offline")
        val id = "new-${files.size}"
        files += DriveBackup(id, name, Instant.parse("2026-09-22T03:12:45Z"), contents.toByteArray().size.toLong())
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
        deleted += id
        files.removeAll { it.id == id }
        contents.remove(id)
    }
}
