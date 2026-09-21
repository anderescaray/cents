package io.github.anderescaray.cents.ui.feature.recurring

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Reminder
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.components.CentsSwipeableRow
import io.github.anderescaray.cents.ui.components.CentsTextField
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

            if (state.addingReminder) {
                NewReminderRows(state, viewModel)
            } else {
                CentsListRow(
                    title = stringResource(R.string.rule_reminders_add),
                    value = if (state.reminders.isEmpty()) stringResource(R.string.rule_reminders_none) else null,
                    showChevron = true,
                    showSeparator = false,
                    onClick = viewModel::startAddingReminder,
                )
            }
        }
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
 * Typing a new one: how many days, and which side of the date.
 *
 * Inline rather than in a dialog, so the reminders already set stay visible while another is
 * added — *three days before* reads differently when you can see that *on the day* is already
 * there.
 */
@Composable
private fun NewReminderRows(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
) {
    CentsListRow(
        title = stringResource(R.string.reminder_new_days),
        trailing = {
            CentsTextField(
                value = state.reminderDaysInput,
                onValueChange = viewModel::setReminderDays,
                placeholder = stringResource(R.string.reminder_new_days_placeholder),
                numeric = true,
            )
        },
    )

    // Zero days is "on the day", where the side of the date means nothing; and a fixed amount is
    // only ever announced beforehand.
    if (state.asksForAmount && state.reminderDays != 0) {
        val before = stringResource(R.string.reminder_before)
        val after = stringResource(R.string.reminder_after)
        Box(modifier = Modifier.padding(CentsDimens.rowHorizontalPadding)) {
            CentsSegmentedControl(
                options = listOf(true, false),
                selected = state.reminderBefore,
                onSelect = viewModel::setReminderBefore,
                label = { option -> if (option) before else after },
            )
        }
    }

    CentsListRow(
        title = stringResource(R.string.action_add),
        subtitle = if (state.reminderIsDuplicate) stringResource(R.string.reminder_duplicate) else null,
        value = state.reminderOffset?.let { reminderLabel(Reminder(offsetDays = it)) },
        valueColor = CentsTheme.colors.secondaryLabel,
        showSeparator = false,
        onClick = if (state.canAddReminder) viewModel::confirmReminder else viewModel::cancelAddingReminder,
    )
}

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
