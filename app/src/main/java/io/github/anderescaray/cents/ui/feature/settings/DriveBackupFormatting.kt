package io.github.anderescaray.cents.ui.feature.settings

import android.content.Context
import io.github.anderescaray.cents.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun relativeBackupTime(
    context: Context,
    at: Instant?,
    now: Instant,
): String {
    if (at == null) return context.getString(R.string.drive_backup_never)
    val zone = ZoneId.systemDefault()
    val locale = context.resources.configuration.locales[0]
    val date = at.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val time =
        DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
            .format(at)
    return when (date) {
        today -> context.getString(R.string.drive_backup_today_at, time)
        today.minusDays(1) -> context.getString(R.string.drive_backup_yesterday_at, time)
        else -> backupDateTime(context, at)
    }
}

internal fun backupDateTime(
    context: Context,
    at: Instant,
): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(context.resources.configuration.locales[0])
        .withZone(ZoneId.systemDefault())
        .format(at)
