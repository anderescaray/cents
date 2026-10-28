package io.github.anderescaray.cents.ui.feature.recurring

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Reminder
import io.github.anderescaray.cents.ui.components.CentsButton
import io.github.anderescaray.cents.ui.components.CentsChip
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.components.CentsSheet
import io.github.anderescaray.cents.ui.components.CentsSwipeableRow
import io.github.anderescaray.cents.ui.components.IconBadge
import io.github.anderescaray.cents.ui.components.RoundIconButton
import io.github.anderescaray.cents.ui.notifications.notificationsAllowed
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * When Cents should say something about this subscription.
 *
 * **Any number of them, and the owner picks the number of days.** *Three days before* and *again
 * on the day* is a real way to be reminded, and the app has no business deciding one is enough.
 *
 * The two modes get different offers, because they are different requests. A **fixed** amount is
 * announced — *Netflix, 12,99 €, in 3 days* — and only beforehand is any use: afterwards the
 * movement is already in the list, so the notification would be telling the owner something they
 * can read one screen away. A **variable** one is being asked about, and *after* is the offer
 * that matters there, because the bill nobody has opened yet is the one that gets forgotten.
 */
internal fun LazyListScope.remindersSection(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
) {
    item(key = "reminders") {
        CentsListSection(
            header = stringResource(R.string.rule_field_reminders),
            footer =
                stringResource(
                    if (state.asksForAmount) {
                        R.string.rule_reminders_footer_variable
                    } else {
                        R.string.rule_reminders_footer_fixed
                    },
                ),
        ) {
            if (state.reminders.isNotEmpty() || state.addingReminder) NotificationsOffRow()

            state.reminders.forEach { reminder ->
                key(reminder.offsetDays) {
                    CentsSwipeableRow(
                        onDelete = { viewModel.removeReminder(reminder.offsetDays) },
                        deleteLabel = stringResource(R.string.action_delete),
                    ) { rowModifier ->
                        CentsListRow(
                            modifier = rowModifier,
                            title = reminderLabel(reminder),
                        )
                    }
                }
            }

            CentsListRow(
                title = stringResource(R.string.rule_reminders_add),
                leading = { IconBadge(iconRes = R.drawable.ic_plus, color = CentsTheme.colors.accent) },
                showSeparator = false,
                onClick = viewModel::startAddingReminder,
                modifier = Modifier.testTag(ADD_REMINDER_TAG),
            )
        }
        if (state.addingReminder) NewReminderSheet(state, viewModel)
    }
}

/** *3 days before*, *The day after*, *On the day* — how a reminder reads once it exists. */
@Composable
internal fun reminderLabel(reminder: Reminder): String =
    when {
        reminder.isOnTheDay -> {
            stringResource(R.string.reminder_on_the_day)
        }

        reminder.isBefore -> {
            pluralStringResource(R.plurals.reminder_days_before, reminder.days, reminder.days)
        }

        else -> {
            pluralStringResource(R.plurals.reminder_days_after, reminder.days, reminder.days)
        }
    }

/**
 * A new reminder, in a sheet: the usual ones as chips, a stepper for any other number of days,
 * *Before* or *After* for a bill that asks for its amount, and one button. The reminders already
 * set are left out of the suggestions, since the same notification twice is no use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewReminderSheet(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
) {
    val suggestions =
        remember(state.asksForAmount, state.reminders) {
            val taken = state.reminders.map { it.offsetDays }.toSet()
            (if (state.asksForAmount) VARIABLE_SUGGESTIONS else FIXED_SUGGESTIONS).filterNot { it in taken }
        }
    CentsSheet(title = stringResource(R.string.reminder_sheet_title), onDismiss = viewModel::cancelAddingReminder) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(CentsDimens.screenMargin),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
        ) {
            if (suggestions.isNotEmpty()) {
                SheetLabel(stringResource(R.string.reminder_suggestions))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
                    verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
                ) {
                    suggestions.forEach { offset ->
                        CentsChip(
                            label = reminderLabel(Reminder(offsetDays = offset)),
                            selected = offset == state.reminderOffset,
                            onClick = { viewModel.chooseReminder(offset) },
                        )
                    }
                }
            }

            SheetLabel(stringResource(R.string.reminder_custom))
            DaysStepper(days = state.reminderDays ?: 0, onStep = viewModel::stepReminderDays)
            // Zero days is "on the day", where the side of the date means nothing; and a fixed
            // amount is only ever announced beforehand.
            if (state.asksForAmount && state.reminderDays != 0) {
                val before = stringResource(R.string.reminder_before)
                val after = stringResource(R.string.reminder_after)
                CentsSegmentedControl(
                    options = listOf(true, false),
                    selected = state.reminderBefore,
                    onSelect = viewModel::setReminderBefore,
                    label = { option -> if (option) before else after },
                )
            }

            ReminderSummary(state)
            CentsButton(
                text = stringResource(R.string.reminder_confirm),
                onClick = viewModel::confirmReminder,
                enabled = state.canAddReminder,
                modifier = Modifier.fillMaxWidth().testTag(CONFIRM_REMINDER_TAG),
            )
        }
    }
}

/** What the button will add, in words, or why it cannot. */
@Composable
private fun ReminderSummary(state: RuleFormUiState) {
    val offset = state.reminderOffset
    Text(
        text =
            when {
                state.reminderIsDuplicate -> stringResource(R.string.reminder_duplicate)
                offset != null -> reminderLabel(Reminder(offsetDays = offset))
                else -> ""
            },
        style = CentsTheme.typography.subhead,
        color = CentsTheme.colors.secondaryLabel,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SheetLabel(text: String) {
    Text(text = text, style = CentsTheme.typography.footnote, color = CentsTheme.colors.secondaryLabel)
}

/** The number read on one line, with a round button either side of it. */
@Composable
private fun DaysStepper(
    days: Int,
    onStep: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RoundIconButton(
            iconRes = R.drawable.ic_minus,
            contentDescription = stringResource(R.string.reminder_fewer_days),
            onClick = { onStep(-1) },
            tint = CentsTheme.colors.accent,
        )
        Text(
            text = pluralStringResource(R.plurals.reminder_stepper_days, days, days),
            style = CentsTheme.typography.title2,
            color = CentsTheme.colors.label,
            maxLines = 1,
            modifier = Modifier.testTag(REMINDER_DAYS_TAG),
        )
        RoundIconButton(
            iconRes = R.drawable.ic_plus,
            contentDescription = stringResource(R.string.reminder_more_days),
            onClick = { onStep(1) },
            tint = CentsTheme.colors.accent,
        )
    }
}

/** On the day, the day before, three days and a week before: what people set. */
private val FIXED_SUGGESTIONS = listOf(0, -1, -3, -7)

/** A bill that asks also wants the nudge after the date, when it has still not been opened. */
private val VARIABLE_SUGGESTIONS = listOf(-1, 0, 1, 3)

internal const val ADD_REMINDER_TAG = "addReminder"
internal const val CONFIRM_REMINDER_TAG = "confirmReminder"
internal const val REMINDER_DAYS_TAG = "reminderDays"

/**
 * Says so when the reminders are being written down and thrown away.
 *
 * A switch that records a preference the system will never act on is worse than no switch: the
 * owner sets three reminders, hears nothing, and concludes the feature is broken. On Android 13
 * and later the row asks for the permission; on anything older — or once it has been refused for
 * good — it opens the app's notification settings, because that is the only place left to say yes.
 */
@Composable
private fun NotificationsOffRow() {
    val context = LocalContext.current
    var asked by remember { mutableIntStateOf(0) }
    val canNotify = remember(asked) { notificationsAllowed(context) }

    // Coming back from system settings is how this usually gets fixed, and nothing tells the
    // screen about it.
    LifecycleResumeEffect(Unit) {
        asked += 1
        onPauseOrDispose {}
    }

    if (canNotify) return

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { asked += 1 }

    CentsListRow(
        title = stringResource(R.string.rule_reminders_blocked),
        showChevron = true,
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData("package:${context.packageName}".toUri()),
                )
            }
        },
    )
}
