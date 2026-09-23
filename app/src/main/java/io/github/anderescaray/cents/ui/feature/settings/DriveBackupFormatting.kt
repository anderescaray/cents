package io.github.anderescaray.cents.ui.feature.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * When a backup happened, said the way the rest of the app says a date — *Today*, *Yesterday*,
 * or the date itself, from [relativeLabel] — with the time after it.
 */
@Composable
internal fun relativeBackupTime(
    at: Instant?,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    if (at == null) return stringResource(R.string.drive_backup_never)
    val day = at.atZone(zone).toLocalDate().relativeLabel(now.atZone(zone).toLocalDate())
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone).format(at)
    return stringResource(R.string.drive_backup_day_time, day, time)
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
