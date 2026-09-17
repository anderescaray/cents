package io.github.anderescaray.cents.ui.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.data.export.ExportFormat
import io.github.anderescaray.cents.ui.components.CentsActionSheet
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSheetAction
import io.github.anderescaray.cents.ui.components.CentsSwitch
import io.github.anderescaray.cents.ui.theme.CentsDimens

/**
 * Placeholder that doubles as a shop window for the design system: a switch row, a value
 * row, a disclosure row and the prominent button, so all of it can be judged on a device.
 */
@Composable
fun SettingsScreen(
    onOpenCategories: () -> Unit,
    onResetHomeLayout: () -> Unit,
    exportViewModel: ExportViewModel = hiltViewModel(),
) {
    val export by exportViewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        onOpenCategories = onOpenCategories,
        onResetHomeLayout = onResetHomeLayout,
        export = export,
        exportActions =
            ExportActions(
                onStart = exportViewModel::start,
                onChoose = exportViewModel::choose,
                onCancel = exportViewModel::cancel,
                onWriteTo = exportViewModel::writeTo,
                onAcknowledge = exportViewModel::acknowledge,
            ),
    )
}

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
    onResetHomeLayout: () -> Unit,
    export: ExportUiState = ExportUiState(),
    exportActions: ExportActions = ExportActions(),
) {
    var biometricLock by remember { mutableStateOf(false) }

    CentsLargeTitleScaffold(title = stringResource(R.string.tab_settings)) {
        item {
            CentsListSection(footer = stringResource(R.string.settings_categories_footer)) {
                CentsListRow(
                    title = stringResource(R.string.settings_categories),
                    showChevron = true,
                    showSeparator = false,
                    onClick = onOpenCategories,
                )
            }
        }
        item { ResetHomeLayoutSection(onReset = onResetHomeLayout) }
        item {
            CentsListSection(header = stringResource(R.string.settings_section_security)) {
                CentsListRow(
                    title = stringResource(R.string.settings_biometric_lock),
                    trailing = {
                        CentsSwitch(
                            checked = biometricLock,
                            onCheckedChange = { biometricLock = it },
                        )
                    },
                    showSeparator = false,
                )
            }
        }
        item {
            CentsListSection(
                header = stringResource(R.string.settings_section_data),
                footer = stringResource(R.string.settings_data_footer),
            ) {
                CentsListRow(
                    title = stringResource(R.string.settings_export),
                    showChevron = true,
                    onClick = exportActions.onStart,
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
        item {
            CentsButton(
                text = stringResource(R.string.settings_add_transaction),
                onClick = {},
                modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
            )
        }
    }

    Export(state = export, actions = exportActions)
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

/**
 * The non-gesture way back to a sane canvas. A screen reader cannot drag a bubble, and a bubble
 * pinched down to nothing in a corner should not need one.
 *
 * It asks first (rule 7) and there is no undo behind it: an arrangement is something the owner
 * made by hand, it can only be rebuilt the same way, and this row sits one mis-tap away from
 * everything else in Settings.
 */
@Composable
private fun ResetHomeLayoutSection(onReset: () -> Unit) {
    var asking by remember { mutableStateOf(false) }

    CentsListSection(
        header = stringResource(R.string.settings_section_home),
        footer = stringResource(R.string.settings_reset_home_footer),
    ) {
        CentsListRow(
            title = stringResource(R.string.settings_reset_home),
            showSeparator = false,
            onClick = { asking = true },
        )
    }

    if (asking) {
        CentsAlert(
            title = stringResource(R.string.settings_reset_home_title),
            message = stringResource(R.string.settings_reset_home_message),
            confirmLabel = stringResource(R.string.settings_reset_home_confirm),
            onConfirm = {
                asking = false
                onReset()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { asking = false },
            destructive = true,
        )
    }
}
