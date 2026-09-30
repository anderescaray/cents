package io.github.anderescaray.cents.ui.feature.capture

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsActionSheet
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSheetAction
import io.github.anderescaray.cents.ui.components.CentsSwitch
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * Settings › Capture (PRD §4.6, `DECISIONS.md` D29).
 *
 * *Notification access* is asked of the phone here rather than kept anywhere: it is granted and
 * taken back on a system screen, and the only moment the app hears about it is coming back into
 * view, which is when this asks again.
 */
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onAddApp: () -> Unit,
    onOpenWords: (TransactionKind) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var resumed by remember { mutableIntStateOf(0) }
    val accessGranted = remember(resumed) { listenerAllowed(context) }

    LifecycleResumeEffect(Unit) {
        resumed += 1
        onPauseOrDispose {}
    }

    CaptureContent(
        state = state,
        accessGranted = accessGranted,
        actions =
            CaptureActions(
                onBack = onBack,
                onEnabledChanged = viewModel::setEnabled,
                onOpenAccess = { openAccessSettings(context) },
                onEditApp = viewModel::edit,
                onAddApp = onAddApp,
                onOpenWords = onOpenWords,
                onMoveTo = viewModel::moveTo,
                onRemove = viewModel::remove,
                onCloseEditor = viewModel::closeEditor,
            ),
    )
}

/** What the screen can be asked to do, so [CaptureContent] holds no view model. */
internal data class CaptureActions(
    val onBack: () -> Unit = {},
    val onEnabledChanged: (Boolean) -> Unit = {},
    val onOpenAccess: () -> Unit = {},
    val onEditApp: (String) -> Unit = {},
    val onAddApp: () -> Unit = {},
    val onOpenWords: (TransactionKind) -> Unit = {},
    val onMoveTo: (Long) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onCloseEditor: () -> Unit = {},
)

/** The screen without its view model or the phone, so a test can draw it from a state. */
@Composable
internal fun CaptureContent(
    state: CaptureUiState,
    accessGranted: Boolean,
    actions: CaptureActions,
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.capture_title),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.tab_settings), onClick = actions.onBack) },
    ) {
        if (state.isLoading) return@CentsLargeTitleScaffold
        switchSection(state, actions)
        accessSection(accessGranted, actions)
        appsSection(state, actions)
        wordsSection(state, actions)
    }

    state.editing?.let { editing -> EditAppSheet(editing, state, actions) }
}

private fun LazyListScope.switchSection(
    state: CaptureUiState,
    actions: CaptureActions,
) {
    item(key = "switch") {
        CentsListSection(footer = stringResource(R.string.capture_switch_footer)) {
            CentsListRow(
                title = stringResource(R.string.capture_switch),
                trailing = { CentsSwitch(checked = state.enabled, onCheckedChange = actions.onEnabledChanged) },
                showSeparator = false,
            )
        }
    }
}

/**
 * Whether Android lets Cents hear notifications at all, and the way to the screen that decides.
 *
 * Shown whether or not capture is on: a switch that is on while the system says no is exactly
 * the setting that records a preference nothing acts on, and the footer says which it is.
 */
private fun LazyListScope.accessSection(
    accessGranted: Boolean,
    actions: CaptureActions,
) {
    item(key = "access") {
        CentsListSection(
            footer =
                stringResource(
                    if (accessGranted) R.string.capture_access_on_footer else R.string.capture_access_off_footer,
                ),
        ) {
            CentsListRow(
                title = stringResource(R.string.capture_access),
                value = stringResource(if (accessGranted) R.string.capture_access_on else R.string.capture_access_off),
                valueColor = if (accessGranted) null else CentsTheme.colors.destructive,
                showChevron = true,
                showSeparator = false,
                onClick = actions.onOpenAccess,
                modifier = Modifier.testTag(ACCESS_ROW_TAG),
            )
        }
    }
}

private fun LazyListScope.appsSection(
    state: CaptureUiState,
    actions: CaptureActions,
) {
    item(key = "apps") {
        CentsListSection(
            header = stringResource(R.string.capture_section_apps),
            footer = stringResource(R.string.capture_apps_footer),
        ) {
            state.sources.forEach { row ->
                CentsListRow(
                    title = row.source.label,
                    subtitle =
                        row.accountName?.let { stringResource(R.string.capture_app_into, it) }
                            ?: stringResource(R.string.capture_app_no_account),
                    showChevron = true,
                    onClick = { actions.onEditApp(row.source.packageName) },
                    modifier = Modifier.testTag(APP_ROW_TAG),
                )
            }
            CentsListRow(
                title = stringResource(R.string.capture_add_app),
                subtitle = if (state.canAddApp) null else stringResource(R.string.capture_add_app_needs_account),
                showChevron = state.canAddApp,
                showSeparator = false,
                onClick = actions.onAddApp.takeIf { state.canAddApp },
            )
        }
    }
}

private fun LazyListScope.wordsSection(
    state: CaptureUiState,
    actions: CaptureActions,
) {
    item(key = "words") {
        CentsListSection(
            header = stringResource(R.string.capture_section_words),
            footer = stringResource(R.string.capture_words_footer),
        ) {
            CentsListRow(
                title = stringResource(R.string.capture_expense_words),
                value = state.expenseWordCount.toString(),
                showChevron = true,
                onClick = { actions.onOpenWords(TransactionKind.EXPENSE) },
            )
            CentsListRow(
                title = stringResource(R.string.capture_income_words),
                value = state.incomeWordCount.toString(),
                showChevron = true,
                showSeparator = false,
                onClick = { actions.onOpenWords(TransactionKind.INCOME) },
            )
        }
    }
}

/**
 * One chosen app: another account for it, or stop reading it.
 *
 * Stopping needs no confirmation of its own: it deletes nothing, since what the app already
 * recorded stays in the list, and choosing the app again is two taps away.
 */
@Composable
private fun EditAppSheet(
    editing: CaptureSourceRow,
    state: CaptureUiState,
    actions: CaptureActions,
) {
    val current = editing.source.accountId
    val accounts =
        state.accounts.map { account ->
            CentsSheetAction(
                label =
                    if (account.id == current && editing.accountName != null) {
                        stringResource(R.string.capture_account_current, account.name)
                    } else {
                        account.name
                    },
                onClick = { if (account.id == current) actions.onCloseEditor() else actions.onMoveTo(account.id) },
            )
        }
    CentsActionSheet(
        title = editing.source.label,
        message = stringResource(R.string.capture_edit_message),
        actions =
            accounts +
                CentsSheetAction(
                    label = stringResource(R.string.capture_stop_reading),
                    onClick = actions.onRemove,
                    destructive = true,
                ),
        cancelLabel = stringResource(R.string.action_cancel),
        onDismiss = actions.onCloseEditor,
    )
}

/** Whether Android lets this app's notification listener hear anything. */
private fun listenerAllowed(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

/**
 * The system screen that grants *Notification access*. Some makers move or rename it, so a phone
 * without it gets the settings app's front page rather than a crash.
 */
private fun openAccessSettings(context: Context) {
    val listeners = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    try {
        context.startActivity(listeners)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

internal const val ACCESS_ROW_TAG = "captureAccess"
internal const val APP_ROW_TAG = "captureApp"
