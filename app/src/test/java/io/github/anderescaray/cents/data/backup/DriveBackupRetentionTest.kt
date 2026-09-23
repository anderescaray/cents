package io.github.anderescaray.cents.data.backup

import io.github.anderescaray.cents.domain.model.DriveBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class DriveBackupRetentionTest {
    private val today = LocalDate.of(2026, 9, 22)

    @Test
    fun `an empty folder has nothing to delete`() {
        assertTrue(driveBackupIdsToDelete(emptyList()).isEmpty())
    }

    @Test
    fun `a week of daily copies is kept whole`() {
        assertTrue(driveBackupIdsToDelete(daily(7)).isEmpty())
    }

    @Test
    fun `a week of bad copies cannot replace every good one`() {
        // Six months of nightly copies: the seven newest, the newest of each of the four newest
        // weeks, and the newest of each of the six newest months survive.
        val files = daily(DAYS_IN_HALF_A_YEAR)

        val kept = files.map { it.id } - driveBackupIdsToDelete(files).toSet()

        val expected =
            (0L until 7).map { today.minusDays(it) } +
                listOf(
                    // Newest of each ISO week: 2026-09-22 is a Tuesday.
                    LocalDate.of(2026, 9, 20),
                    LocalDate.of(2026, 9, 13),
                    LocalDate.of(2026, 9, 6),
                ) +
                // Newest of each month.
                listOf(
                    LocalDate.of(2026, 8, 31),
                    LocalDate.of(2026, 7, 31),
                    LocalDate.of(2026, 6, 30),
                    LocalDate.of(2026, 5, 31),
                    LocalDate.of(2026, 4, 30),
                )
        assertEquals(expected.map { it.toString() }.toSet(), kept.toSet())
        // The oldest good copy from before a bad week is still more than a week old.
        assertTrue(kept.any { LocalDate.parse(it) < today.minusDays(14) })
    }

    @Test
    fun `several copies on one day keep only the newest of that day`() {
        val morning = backup("morning", today.atTime(8, 0).toInstant(ZoneOffset.UTC))
        val evening = backup("evening", today.atTime(20, 0).toInstant(ZoneOffset.UTC))

        assertEquals(listOf("morning"), driveBackupIdsToDelete(listOf(morning, evening)))
    }

    @Test
    fun `a phone that stopped backing up keeps its old copies when it comes back`() {
        val old = (1..10).map { backup("old-$it", today.minusYears(1).plusDays(it.toLong()).atStartOfDay()) }
        val back = backup("back", today.atStartOfDay())

        // Seven days' worth survives, plus the newest of each old week and month, which here are
        // already among them: the year-long gap costs nothing.
        assertEquals(listOf("old-4", "old-3", "old-2", "old-1"), driveBackupIdsToDelete(old + back))
    }

    @Test
    fun `list order and duplicate ids do not change the answer`() {
        val files = daily(40)
        assertEquals(driveBackupIdsToDelete(files), driveBackupIdsToDelete((files + files).shuffled()))
    }

    /** One copy per day for [count] days ending today, each named by its date. */
    private fun daily(count: Int): List<DriveBackup> =
        (0 until count).map { today.minusDays(it.toLong()) }.map { backup(it.toString(), it.atTime(3, 12)) }

    private fun backup(
        id: String,
        at: LocalDateTime,
    ): DriveBackup = backup(id, at.toInstant(ZoneOffset.UTC))

    private fun backup(
        id: String,
        at: Instant,
    ): DriveBackup = DriveBackup(id, "cents-backup-$id.json", at, 100)

    private companion object {
        const val DAYS_IN_HALF_A_YEAR = 183
    }
}
