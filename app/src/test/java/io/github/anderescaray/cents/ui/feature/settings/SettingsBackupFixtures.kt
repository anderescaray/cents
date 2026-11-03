package io.github.anderescaray.cents.ui.feature.settings

import android.content.Context
import android.net.Uri
import io.github.anderescaray.cents.data.export.DocumentReader
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.domain.model.Backup
import io.github.anderescaray.cents.domain.model.BackupAccount
import io.github.anderescaray.cents.domain.repository.BackupRepository
import kotlinx.coroutines.CompletableDeferred
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal val backupTestClock: Clock = Clock.fixed(Instant.parse("2026-09-22T03:12:00Z"), ZoneOffset.UTC)

internal fun backupWithAccounts(vararg names: String): Backup =
    Backup(
        schemaVersion = CentsDatabase.VERSION,
        exportedAt = backupTestClock.instant(),
        accounts =
            names.mapIndexed { index, name ->
                BackupAccount(index + 1L, name, "BANK", "EUR", 12_345, 0, false, index)
            },
        categories = emptyList(),
        transactions = emptyList(),
        holdings = emptyList(),
        trades = emptyList(),
    )

internal class SettingsBackupRepository(
    var current: Backup = backupWithAccounts("Checking"),
) : BackupRepository {
    var restores = 0
    var failSnapshot = false

    override suspend fun snapshot(): Backup {
        check(!failSnapshot)
        return current
    }

    override suspend fun restore(backup: Backup) {
        restores++
        current = backup
    }
}

internal class SettingsDocumentReader(
    context: Context,
) : DocumentReader(context) {
    var text = ""
    var waiting: CompletableDeferred<String>? = null

    override suspend fun read(uri: Uri): String = waiting?.await() ?: text
}
