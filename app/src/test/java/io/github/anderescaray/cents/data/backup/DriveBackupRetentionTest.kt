package io.github.anderescaray.cents.data.backup

import io.github.anderescaray.cents.domain.model.DriveBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DriveBackupRetentionTest {
    @Test
    fun `an empty folder has nothing to delete`() {
        assertTrue(driveBackupIdsToDelete(emptyList()).isEmpty())
    }

    @Test
    fun `the newest seven are kept`() {
        assertTrue(driveBackupIdsToDelete(files(7)).isEmpty())
    }

    @Test
    fun `only the two oldest of nine are deleted regardless of list order`() {
        assertEquals(listOf("2", "1"), driveBackupIdsToDelete(files(9).reversed()))
    }

    @Test
    fun `equal timestamps have a deterministic tie breaker and duplicate ids are counted once`() {
        val files = files(9).map { it.copy(createdAt = Instant.EPOCH) }
        assertEquals(listOf("2", "1"), driveBackupIdsToDelete(files + files))
    }

    private fun files(count: Int): List<DriveBackup> =
        (1..count).map {
            DriveBackup(it.toString(), "backup-$it.json", Instant.EPOCH.plusSeconds(it.toLong()), 100)
        }
}
