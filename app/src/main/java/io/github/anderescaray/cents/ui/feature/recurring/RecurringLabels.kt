package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Frequency

/**
 * How often a rule comes round, in words.
 *
 * Shared by the list and the editor, so a fortnightly rule does not read one way on the row
 * and another on the screen that made it.
 */
@Composable
fun frequencyLabel(
    frequency: Frequency,
    intervalDays: Int?,
): String =
    when (frequency) {
        Frequency.WEEKLY -> {
            stringResource(R.string.frequency_weekly)
        }

        Frequency.FORTNIGHTLY -> {
            stringResource(R.string.frequency_fortnightly)
        }

        Frequency.MONTHLY -> {
            stringResource(R.string.frequency_monthly)
        }

        Frequency.QUARTERLY -> {
            stringResource(R.string.frequency_quarterly)
        }

        Frequency.YEARLY -> {
            stringResource(R.string.frequency_yearly)
        }

        Frequency.EVERY_N_DAYS -> {
            intervalDays?.let { days -> pluralStringResource(R.plurals.frequency_every_n_days, days, days) }
                ?: stringResource(R.string.frequency_every_n_days_unset)
        }
    }
