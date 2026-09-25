package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsActionSheet
import io.github.anderescaray.cents.ui.components.CentsSheetAction
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How far to move one occurrence: *3 days · 4 Oct*.
 *
 * Each choice says the day as well as the count, because the day is what the movement will
 * carry and what the reminder will fire on. The choices come from `postponeChoices`, so none of
 * them reaches the next occurrence.
 */
@Composable
internal fun PostponeSheet(
    name: String,
    from: LocalDate,
    today: LocalDate,
    choices: List<LocalDate>,
    onChoose: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    CentsActionSheet(
        title = stringResource(R.string.postpone_sheet_title, name),
        message = stringResource(R.string.postpone_sheet_message),
        actions =
            choices.map { day ->
                val days = ChronoUnit.DAYS.between(from, day).toInt()
                CentsSheetAction(
                    label = pluralStringResource(R.plurals.postpone_by_days, days, days, day.relativeLabel(today)),
                    onClick = { onChoose(day) },
                )
            },
        cancelLabel = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
    )
}
