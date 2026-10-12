package io.github.anderescaray.cents.ui.feature.settings

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.data.export.ExportFormat
import io.github.anderescaray.cents.ui.components.CentsActionSheet
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSheetAction
import io.github.anderescaray.cents.ui.components.IconBadge
import io.github.anderescaray.cents.ui.language.AppLanguage
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * Settings, opened from Home's header: what organises the money (categories, subscriptions,
 * groups, capture) in one card, then the data — Drive, export and restore — and last, the
 * language the app is shown in.
 */
@Composable
fun SettingsScreen(
    onOpenCategories: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onBack: () -> Unit = {},
    onOpenGroups: () -> Unit = {},
    onOpenCapture: () -> Unit = {},
    exportViewModel: ExportViewModel = hiltViewModel(),
    restoreViewModel: RestoreViewModel = hiltViewModel(),
    driveViewModel: DriveBackupViewModel = hiltViewModel(),
    languageViewModel: AppLanguageViewModel = hiltViewModel(),
) {
    val export by exportViewModel.uiState.collectAsStateWithLifecycle()
    val restore by restoreViewModel.uiState.collectAsStateWithLifecycle()
    val drive by driveViewModel.uiState.collectAsStateWithLifecycle()
    val language by languageViewModel.language.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    // Each composition is a new screen: changing the language recreates it, and so can the
    // phone's own App languages, while the view model carries on from before.
    LaunchedEffect(Unit) { languageViewModel.refresh() }
    DriveBackupAuthorization(drive, driveViewModel)
    SettingsContent(
        onOpenCategories = onOpenCategories,
        onOpenSubscriptions = onOpenSubscriptions,
        onBack = onBack,
        onOpenGroups = onOpenGroups,
        onOpenCapture = onOpenCapture,
        export = export,
        restore = restore,
        drive = drive,
        driveActions =
            DriveBackupActions(
                onAutomaticChanged = driveViewModel::setAutomaticEnabled,
                onBackUp = driveViewModel::backUpNow,
                onReconnect = driveViewModel::reconnect,
                onRestore = driveViewModel::showBackups,
                onChooseBackup = { driveViewModel.chooseBackup(it, restoreViewModel::readFromDrive) },
                onCloseBackups = driveViewModel::closeBackups,
            ),
        restoreActions =
            RestoreActions(
                onStart = restoreViewModel::start,
                onReadFrom = restoreViewModel::readFrom,
                onConfirm = restoreViewModel::confirm,
                onCancel = restoreViewModel::cancel,
                onAcknowledge = restoreViewModel::acknowledge,
            ),
        exportActions =
            ExportActions(
                onStart = exportViewModel::start,
                onChoose = exportViewModel::choose,
                onCancel = exportViewModel::cancel,
                onWriteTo = exportViewModel::writeTo,
                onAcknowledge = exportViewModel::acknowledge,
            ),
        language = language,
        onChooseLanguage = { chosen -> if (languageViewModel.choose(chosen)) activity?.recreate() },
    )
}

/** What the restore flow can be asked to do, so the screen below holds no view model. */
@Immutable
data class RestoreActions(
    val onStart: () -> Unit = {},
    val onReadFrom: (Uri?) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onAcknowledge: () -> Unit = {},
)

/** What the export flow can be asked to do, so the screen below holds no view model. */
@Immutable
data class ExportActions(
    val onStart: () -> Unit = {},
    val onChoose: (ExportFormat) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onWriteTo: (Uri?) -> Unit = {},
    val onAcknowledge: () -> Unit = {},
)

/**
 * The screen itself, given everything it shows.
 *
 * Stateless, in the shape the movements list already uses: a test can put Settings on screen and
 * tap its alerts without a Hilt-enabled activity behind it, which is what the export's view
 * model would otherwise require of every test that only wanted to check a confirmation.
 */
@Composable
internal fun SettingsContent(
    onOpenCategories: () -> Unit,
    onOpenSubscriptions: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenGroups: () -> Unit = {},
    onOpenCapture: () -> Unit = {},
    export: ExportUiState = ExportUiState(),
    exportActions: ExportActions = ExportActions(),
    restore: RestoreUiState = RestoreUiState(),
    restoreActions: RestoreActions = RestoreActions(),
    drive: DriveBackupUiState = DriveBackupUiState(),
    driveActions: DriveBackupActions = DriveBackupActions(),
    language: AppLanguage = AppLanguage.SYSTEM,
    onChooseLanguage: (AppLanguage) -> Unit = {},
) {
    val dataIdle = export.step == ExportStep.IDLE && restore.step == RestoreStep.IDLE
    val canStartData = dataIdle && !drive.isBusy && !drive.showBackups
    CentsLargeTitleScaffold(
        title = stringResource(R.string.tab_settings),
        leading = { CentsBackButton(label = stringResource(R.string.tab_home), onClick = onBack) },
    ) {
        item(key = "organise") {
            CentsListSection {
                DisclosureRow(
                    R.string.settings_categories,
                    R.string.settings_categories_footer,
                    R.drawable.ic_tab_transactions,
                    onOpenCategories,
                )
                DisclosureRow(
                    R.string.settings_subscriptions,
                    R.string.settings_subscriptions_footer,
                    R.drawable.ic_calendar,
                    onOpenSubscriptions,
                )
                DisclosureRow(
                    R.string.settings_groups,
                    R.string.settings_groups_footer,
                    R.drawable.ic_tab_home,
                    onOpenGroups,
                )
                DisclosureRow(
                    R.string.settings_capture,
                    R.string.settings_capture_footer,
                    R.drawable.ic_arrow_down,
                    onOpenCapture,
                    last = true,
                    modifier = Modifier.testTag("capture"),
                )
            }
        }
        item(key = "data") {
            DataSection(
                onExport = exportActions.onStart.takeIf { canStartData },
                onRestore = restoreActions.onStart.takeIf { canStartData },
                restore = restore,
            )
        }
        item(key = "driveBackup") { DriveBackupSection(drive, driveActions, enabled = dataIdle) }
        item(key = "language") { LanguageSection(language, onChooseLanguage) }
    }

    Export(state = export, actions = exportActions)
    DriveBackupPicker(state = drive, actions = driveActions)
    Restore(state = restore, actions = restoreActions)
}

/** One row that opens a screen of its own, with a line under it saying what that screen is for. */
@Composable
private fun DisclosureRow(
    @StringRes title: Int,
    @StringRes subtitle: Int,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    last: Boolean = false,
) {
    CentsListRow(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        leading = { IconBadge(iconRes = icon, color = CentsTheme.colors.accent) },
        showChevron = true,
        showSeparator = !last,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * The language the app is shown in, last because it is set once (backlog item 1). One row with the
 * current answer, which asks from an action sheet, like a subscription's frequency.
 */
@Composable
private fun LanguageSection(
    language: AppLanguage,
    onChoose: (AppLanguage) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    CentsListSection(footer = stringResource(R.string.settings_language_footer)) {
        CentsListRow(
            title = stringResource(R.string.settings_language),
            value = stringResource(language.label),
            showChevron = true,
            showSeparator = false,
            onClick = { choosing = true },
            modifier = Modifier.testTag("language"),
        )
    }
    if (choosing) {
        CentsActionSheet(
            title = stringResource(R.string.settings_language),
            message = stringResource(R.string.settings_language_message),
            actions =
                AppLanguage.entries.map { option ->
                    CentsSheetAction(
                        label = stringResource(option.label),
                        onClick = {
                            choosing = false
                            onChoose(option)
                        },
                    )
                },
            cancelLabel = stringResource(R.string.action_cancel),
            onDismiss = { choosing = false },
        )
    }
}

/** What a language is called on the row and in the sheet: each one in itself. */
@get:StringRes
private val AppLanguage.label: Int
    get() =
        when (this) {
            AppLanguage.SYSTEM -> R.string.settings_language_system
            AppLanguage.ENGLISH -> R.string.settings_language_english
            AppLanguage.SPANISH -> R.string.settings_language_spanish
        }

/**
 * Getting the data out, and putting it back.
 *
 * The two sit together because they are one subject, and because seeing them next to each other
 * is what makes the second one legible: a restore is only ever as good as the file the export
 * beside it wrote.
 */
@Composable
private fun DataSection(
    onExport: (() -> Unit)?,
    onRestore: (() -> Unit)?,
    restore: RestoreUiState,
) {
    CentsListSection(
        header = stringResource(R.string.settings_section_data),
        footer = stringResource(R.string.settings_data_footer),
    ) {
        CentsListRow(
            title = stringResource(R.string.settings_export),
            showChevron = true,
            onClick = onExport,
        )
        CentsListRow(
            title = stringResource(R.string.settings_restore),
            subtitle =
                when (restore.step) {
                    RestoreStep.READING -> stringResource(R.string.drive_backup_reading)
                    RestoreStep.WRITING -> stringResource(R.string.drive_backup_restoring)
                    else -> null
                },
            showChevron = true,
            onClick = onRestore,
        )
        CentsListRow(
            title = stringResource(R.string.settings_currency),
            value = "EUR",
            showChevron = true,
            showSeparator = false,
            onClick = {},
        )
    }
}

/**
 * Putting a backup back — the most destructive thing this app can do.
 *
 * The file is read **before** the question is asked, and that ordering is the whole design. A
 * confirmation shown first would be asking the owner to agree to lose everything on the strength
 * of a file nobody has looked at; asking afterwards lets the alert say what is in the file and
 * what is about to be replaced, so the answer can be checked rather than guessed.
 */
@Composable
private fun Restore(
    state: RestoreUiState,
    actions: RestoreActions,
) {
    val picker =
        rememberLauncherForActivityResult(
            // Any file, because a backup renamed or handed over by another app is still a
            // backup, and a picker that hides it would be a dead end with no way out.
            contract = ActivityResultContracts.OpenDocument(),
            onResult = actions.onReadFrom,
        )

    LaunchedEffect(state.step) {
        if (state.step == RestoreStep.PICKING) picker.launch(arrayOf("*/*"))
    }

    when (state.step) {
        RestoreStep.CONFIRMING -> {
            ConfirmRestoreAlert(state, actions)
        }

        RestoreStep.REFUSED -> {
            RefusedAlert(state, actions)
        }

        RestoreStep.DONE -> {
            CentsAlert(
                title = stringResource(R.string.restore_done_title),
                message = stringResource(R.string.restore_done_message),
                confirmLabel = stringResource(R.string.action_ok),
                onConfirm = actions.onAcknowledge,
                onDismiss = actions.onAcknowledge,
            )
        }

        RestoreStep.FAILED -> {
            CentsAlert(
                title = stringResource(R.string.restore_failed_title),
                message = stringResource(R.string.restore_failed_message),
                confirmLabel = stringResource(R.string.action_ok),
                onConfirm = actions.onAcknowledge,
                onDismiss = actions.onAcknowledge,
            )
        }

        RestoreStep.CURRENT_UNREADABLE -> {
            CentsAlert(
                title = stringResource(R.string.restore_current_unreadable_title),
                message = stringResource(R.string.restore_current_unreadable_message),
                confirmLabel = stringResource(R.string.action_ok),
                onConfirm = actions.onAcknowledge,
                onDismiss = actions.onAcknowledge,
            )
        }

        RestoreStep.IDLE, RestoreStep.PICKING, RestoreStep.READING, RestoreStep.WRITING -> {
            Unit
        }
    }
}

/**
 * The last moment before everything goes, saying both halves of the trade.
 *
 * What is in the file is what the owner gets; what is recorded now is what they lose. Naming
 * only one of the two would be asking half a question.
 */
@Composable
private fun ConfirmRestoreAlert(
    state: RestoreUiState,
    actions: RestoreActions,
) {
    val incoming = state.incoming ?: return
    val current = state.current
    CentsAlert(
        title = stringResource(R.string.restore_confirm_title),
        message =
            buildString {
                append(
                    stringResource(
                        R.string.restore_confirm_incoming,
                        incoming.accounts,
                        incoming.movements,
                        incoming.holdings,
                        incoming.subscriptions,
                    ),
                )
                if (incoming.hasBudgetsOrGroups) {
                    append(" ")
                    append(stringResource(R.string.restore_confirm_incoming_extra, incoming.budgets, incoming.groups))
                }
                // An empty app has nothing to lose, and saying "this replaces your 0 accounts"
                // would be alarming about nothing.
                if (!current.isEmpty) {
                    append("\n\n")
                    append(
                        stringResource(
                            R.string.restore_confirm_replaces,
                            current.accounts,
                            current.movements,
                            current.holdings,
                            current.subscriptions,
                        ),
                    )
                    if (current.hasBudgetsOrGroups) {
                        append(" ")
                        append(stringResource(R.string.restore_confirm_replaces_extra, current.budgets, current.groups))
                    }
                    // The one way back from a restore is a copy taken before it, and this alert
                    // is the last moment it can be taken. Named by the row it lives on.
                    append("\n\n")
                    append(
                        stringResource(
                            R.string.restore_confirm_export_first,
                            stringResource(R.string.settings_export),
                        ),
                    )
                }
            },
        confirmLabel = stringResource(R.string.restore_confirm_action),
        onConfirm = actions.onConfirm,
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = actions.onCancel,
        destructive = true,
    )
}

/** Why a file was turned away, said in the owner's terms rather than the parser's. */
@Composable
private fun RefusedAlert(
    state: RestoreUiState,
    actions: RestoreActions,
) {
    val message =
        when (state.problem) {
            RestoreProblem.TOO_NEW -> {
                stringResource(R.string.restore_too_new_message, state.fileVersion)
            }

            RestoreProblem.DAMAGED -> {
                stringResource(R.string.restore_damaged_message)
            }

            RestoreProblem.NOT_A_BACKUP, null -> {
                stringResource(R.string.restore_not_a_backup_message)
            }
        }
    CentsAlert(
        title = stringResource(R.string.restore_refused_title),
        message = message,
        confirmLabel = stringResource(R.string.action_ok),
        onConfirm = actions.onAcknowledge,
        onDismiss = actions.onAcknowledge,
    )
}

/**
 * The two questions an export asks, and what it says afterwards.
 *
 * **Which file** is this app's question — the whole thing, or the movements in a spreadsheet —
 * and **where it goes** is the system's, asked by its own picker. Going through the picker is
 * what lets the app write a file while asking for no permission to write files: the owner names
 * a place, and the app is handed a door to that one file.
 */
@Composable
private fun Export(
    state: ExportUiState,
    actions: ExportActions,
) {
    val picker =
        rememberLauncherForActivityResult(
            contract = CreateDocument(state.format?.mimeType ?: ExportFormat.JSON.mimeType),
            onResult = actions.onWriteTo,
        )

    // Launched from the state rather than from the tap, so the format is already decided and
    // the picker is opened for the right kind of file — and so a rotation mid-flow does not
    // open a second one.
    LaunchedEffect(state.step, state.suggestedName) {
        if (state.step == ExportStep.PICKING) picker.launch(state.suggestedName)
    }

    when (state.step) {
        ExportStep.CHOOSING -> {
            FormatSheet(actions)
        }

        ExportStep.DONE -> {
            CentsAlert(
                title = stringResource(R.string.export_done_title),
                message = pluralStringResource(R.plurals.export_done_message, state.rowsWritten, state.rowsWritten),
                confirmLabel = stringResource(R.string.action_ok),
                onConfirm = actions.onAcknowledge,
                onDismiss = actions.onAcknowledge,
            )
        }

        ExportStep.FAILED -> {
            CentsAlert(
                title = stringResource(R.string.export_failed_title),
                message = stringResource(R.string.export_failed_message),
                confirmLabel = stringResource(R.string.action_ok),
                onConfirm = actions.onAcknowledge,
                onDismiss = actions.onAcknowledge,
            )
        }

        ExportStep.IDLE, ExportStep.PICKING, ExportStep.WRITING -> {
            Unit
        }
    }
}

@Composable
private fun FormatSheet(actions: ExportActions) {
    CentsActionSheet(
        title = stringResource(R.string.export_title),
        message = stringResource(R.string.export_message),
        actions =
            listOf(
                CentsSheetAction(
                    label = stringResource(R.string.export_json),
                    onClick = { actions.onChoose(ExportFormat.JSON) },
                    reason = stringResource(R.string.export_json_reason),
                ),
                CentsSheetAction(
                    label = stringResource(R.string.export_csv),
                    onClick = { actions.onChoose(ExportFormat.CSV) },
                    reason = stringResource(R.string.export_csv_reason),
                ),
            ),
        cancelLabel = stringResource(R.string.action_cancel),
        onDismiss = actions.onCancel,
    )
}
