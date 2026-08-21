package io.github.anderescaray.cents.ui.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsColorPicker
import io.github.anderescaray.cents.ui.components.CentsListScreen
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * Creating or editing a single category: a name, a colour, and — for one that already
 * exists — the way out of it, which is a deletion or an archive depending on whether any
 * transaction is counting on it.
 */
@Composable
fun CategoryEditScreen(
    onDone: () -> Unit,
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deletion by viewModel.deletion.state.collectAsStateWithLifecycle()
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }
    LaunchedEffect(state.isNew) {
        // A new category has exactly one thing to type: start with the caret in it.
        if (state.isNew) nameFocus.requestFocus()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
    ) {
        CentsModalBar(
            title =
                stringResource(
                    if (state.isNew) R.string.category_new_title else R.string.category_edit_title,
                ),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
        )

        CategoryForm(state = state, viewModel = viewModel, nameFocus = nameFocus)
    }

    if (deletion.asking) {
        CategoryDeletionAlert(
            state = deletion,
            onConfirm = viewModel.deletion::confirm,
            onDismiss = viewModel.deletion::cancel,
        )
    }
}

/** Name, colour, and the way out of an existing category. */
@Composable
private fun CategoryForm(
    state: CategoryEditUiState,
    viewModel: CategoryEditViewModel,
    nameFocus: FocusRequester,
) {
    CentsListScreen(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        CentsListSection(
            header = stringResource(R.string.category_name_header),
            footer =
                when {
                    state.nameTaken -> stringResource(R.string.category_name_taken)
                    state.writeFailed -> stringResource(R.string.delete_failed)
                    else -> null
                },
        ) {
            CentsTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                placeholder = stringResource(R.string.category_name_placeholder),
                focusRequester = nameFocus,
                modifier = Modifier.testTag(NAME_FIELD_TAG),
            )
        }

        ColorSection(selected = state.colorArgb, onSelect = viewModel::setColor)

        if (!state.isNew) {
            CategoryActions(
                state = state,
                onRestore = viewModel::restore,
                onDelete = viewModel::requestDeletion,
            )
        }
    }
}

/** The palette, laid out as rows of dots with the chosen one ringed. */
@Composable
private fun ColorSection(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    CentsListSection(header = stringResource(R.string.category_color_header)) {
        CentsColorPicker(colors = CategoryPalette, selected = selected, onSelect = onSelect)
    }
}

@Composable
private fun CategoryActions(
    state: CategoryEditUiState,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val deletable = !state.archived || state.deletionKind == DeletionKind.OUTRIGHT

    CentsListSection(
        footer =
            stringResource(
                if (deletable) R.string.category_delete_footer else R.string.categories_archived_footer,
            ),
    ) {
        if (state.archived) {
            CentsActionRow(
                label = stringResource(R.string.category_restore),
                onClick = onRestore,
                showSeparator = deletable,
            )
        }
        if (deletable) {
            CentsActionRow(
                label = stringResource(R.string.category_delete),
                onClick = onDelete,
                destructive = true,
            )
        }
    }
}

internal const val NAME_FIELD_TAG = "categoryName"
