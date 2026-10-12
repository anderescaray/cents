package io.github.anderescaray.cents.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A date as a person would say it: "Today", "Yesterday", or the localised date.
 *
 * Shared by the list headers and the form, so a movement recorded today does not read
 * "Today" in one place and "19 Aug 2026" in the other.
 */
@Composable
fun LocalDate.relativeLabel(today: LocalDate = LocalDate.now()): String =
    when (this) {
        today -> stringResource(R.string.date_today)
        today.minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> medium()
    }

/** "12 Oct 2026", written the phone's way ([formatLocale]). */
fun LocalDate.medium(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(formatLocale()))
