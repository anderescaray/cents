package io.github.anderescaray.cents.ui.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.medium

/**
 * Making or changing one group. [onDone] closes the editor; [onDeleted] is called instead once the
 * group is gone, so the navigation can leave its detail screen behind too.
 */
@Composable
fun GroupEditScreen(
    onDone: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: GroupEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) {
            if (state.deleted) onDeleted() else onDone()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .imePadding(),
    ) {
        CentsModalBar(
            title = stringResource(if (state.isNew) R.string.group_new_title else R.string.group_edit_title),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = CentsDimens.spacingL),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
        ) {
            nameSection(state = state, onName = viewModel::setName)
            datesSection(state = state, viewModel = viewModel)
            if (!state.isNew && state.loaded) groupActions(state = state, viewModel = viewModel)
        }
    }

    GroupEditDialogs(state = state, viewModel = viewModel)
}

/**
 * Archive keeps everything and stops offering the group; delete takes the group and leaves its
 * movements in none. Two intentions, side by side, as for an account.
 */
private fun LazyListScope.groupActions(
    state: GroupEditUiState,
    viewModel: GroupEditViewModel,
) {
    item(key = "archive") {
        CentsListSection(
            footer =
                stringResource(
                    if (state.archived) R.string.group_archived_footer else R.string.group_archive_footer,
                ),
        ) {
            CentsActionRow(
                label =
                    stringResource(
                        if (state.archived) R.string.group_reactivate else R.string.group_archive,
                    ),
                onClick = { viewModel.setArchived(!state.archived) },
                modifier = Modifier.testTag(ARCHIVE_GROUP_TAG),
            )
        }
    }
    item(key = "delete") {
        CentsListSection {
            CentsActionRow(
                label = stringResource(R.string.group_delete),
                onClick = viewModel::requestDelete,
                destructive = true,
            )
        }
    }
}

@Composable
private fun GroupEditDialogs(
    state: GroupEditUiState,
    viewModel: GroupEditViewModel,
) {
    val picking = state.picking
    if (picking != null) {
        val current = if (picking == GroupDate.START) state.startDate else state.endDate
        CentsDatePicker(
            // A calendar opened on an unset end starts from the start, which is where the end
            // almost always is: a few days later.
            selected = current ?: state.startDate ?: state.today,
            today = state.today,
            onSelect = viewModel::setDate,
            onDismiss = viewModel::dismissPicker,
        )
    }

    if (state.confirmingDelete) {
        CentsAlert(
            title = stringResource(R.string.group_delete_title),
            message = stringResource(R.string.group_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::confirmDelete,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = viewModel::cancelDelete,
            destructive = true,
        )
    }
}

private fun LazyListScope.nameSection(
    state: GroupEditUiState,
    onName: (String) -> Unit,
) {
    item(key = "name") {
        CentsListSection(
            header = stringResource(R.string.group_name_header),
            footer = if (state.writeFailed) stringResource(R.string.group_write_failed) else null,
        ) {
            CentsTextField(
                value = state.name,
                onValueChange = onName,
                placeholder = stringResource(R.string.group_name_placeholder),
                modifier = Modifier.testTag(GROUP_NAME_TAG),
            )
        }
    }
}

private fun LazyListScope.datesSection(
    state: GroupEditUiState,
    viewModel: GroupEditViewModel,
) {
    item(key = "dates") {
        CentsListSection(
            header = stringResource(R.string.group_dates_header),
            footer =
                stringResource(
                    if (state.datesInverted) R.string.group_dates_inverted else R.string.group_dates_footer,
                ),
        ) {
            val none = stringResource(R.string.group_date_none)
            CentsListRow(
                title = stringResource(R.string.group_start),
                value = state.startDate?.medium() ?: none,
                showChevron = true,
                onClick = { viewModel.openPicker(GroupDate.START) },
                modifier = Modifier.testTag(GROUP_START_TAG),
            )
            val hasDates = state.startDate != null || state.endDate != null
            CentsListRow(
                title = stringResource(R.string.group_end),
                value = state.endDate?.medium() ?: none,
                valueColor = if (state.datesInverted) CentsTheme.colors.destructiveText else null,
                showChevron = true,
                showSeparator = hasDates,
                onClick = { viewModel.openPicker(GroupDate.END) },
                modifier = Modifier.testTag(GROUP_END_TAG),
            )
            if (hasDates) {
                CentsActionRow(label = stringResource(R.string.group_clear_dates), onClick = viewModel::clearDates)
            }
        }
    }
}

internal const val GROUP_NAME_TAG = "groupName"
internal const val GROUP_START_TAG = "groupStart"
internal const val GROUP_END_TAG = "groupEnd"

internal const val ARCHIVE_GROUP_TAG = "archiveGroup"
