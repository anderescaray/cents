package io.github.anderescaray.cents.data.backup

import io.github.anderescaray.cents.domain.model.DriveBackup
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

internal val driveBackupOrder = compareByDescending<DriveBackup> { it.createdAt }.thenByDescending { it.id }

/**
 * Which copies go once a new one has landed.
 *
 * Copies are kept **by age, not by count**: the newest copy of each of the last [DAYS_TO_KEEP]
 * days that have one, of each of the last [WEEKS_TO_KEEP] weeks, and of each of the last
 * [MONTHS_TO_KEEP] months. Keeping only the newest seven meant a week of bad copies — a ledger
 * damaged by mistake and then backed up every night — replaced every good one. Now the copy from
 * before that week is still there, and so is one from each month before it.
 *
 * Periods are counted among the copies that exist rather than on the calendar, so a phone that
 * stopped backing up for a season comes back to find its old copies where it left them. Days are
 * UTC days, like the file names: where a day ends only decides which of two copies a few hours
 * apart is kept, and a fixed zone keeps the answer the same wherever the phone is.
 */
fun driveBackupIdsToDelete(
    files: List<DriveBackup>,
    zone: ZoneId = ZoneOffset.UTC,
): List<String> {
    val ordered = files.distinctBy { it.id }.sortedWith(driveBackupOrder)
    val kept =
        newestPerPeriod(ordered, DAYS_TO_KEEP) { it.atZone(zone).toLocalDate() } +
            newestPerPeriod(ordered, WEEKS_TO_KEEP) {
                val date = it.atZone(zone).toLocalDate()
                date.get(IsoFields.WEEK_BASED_YEAR) to date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            } +
            newestPerPeriod(ordered, MONTHS_TO_KEEP) { YearMonth.from(it.atZone(zone)) }
    return ordered.filter { it.id !in kept }.map { it.id }
}

/** The newest copy of each of the [periods] most recent periods that have one; [ordered] is newest first. */
private fun newestPerPeriod(
    ordered: List<DriveBackup>,
    periods: Int,
    periodOf: (Instant) -> Any,
): Set<String> =
    ordered
        .distinctBy { periodOf(it.createdAt) }
        .take(periods)
        .mapTo(mutableSetOf()) { it.id }

internal fun driveBackupFileName(at: Instant): String = "cents-backup-${FILE_TIME.format(at)}.json"

internal fun isDriveBackupFile(name: String): Boolean = BACKUP_NAME.matches(name)

internal const val DAYS_TO_KEEP = 7
internal const val WEEKS_TO_KEEP = 4
internal const val MONTHS_TO_KEEP = 6
private val FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss").withZone(ZoneOffset.UTC)
private val BACKUP_NAME = Regex("cents-backup-\\d{4}-\\d{2}-\\d{2}T\\d{6}\\.json")
