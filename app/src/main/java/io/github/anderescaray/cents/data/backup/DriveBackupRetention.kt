package io.github.anderescaray.cents.data.backup

import io.github.anderescaray.cents.domain.model.DriveBackup
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal val driveBackupOrder = compareByDescending<DriveBackup> { it.createdAt }.thenByDescending { it.id }

fun driveBackupIdsToDelete(files: List<DriveBackup>): List<String> =
    files
        .distinctBy { it.id }
        .sortedWith(driveBackupOrder)
        .drop(BACKUPS_TO_KEEP)
        .map { it.id }

internal fun driveBackupFileName(at: Instant): String = "cents-backup-${FILE_TIME.format(at)}.json"

internal fun isDriveBackupFile(name: String): Boolean = BACKUP_NAME.matches(name)

private const val BACKUPS_TO_KEEP = 7
private val FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss").withZone(ZoneOffset.UTC)
private val BACKUP_NAME = Regex("cents-backup-\\d{4}-\\d{2}-\\d{2}T\\d{6}\\.json")
