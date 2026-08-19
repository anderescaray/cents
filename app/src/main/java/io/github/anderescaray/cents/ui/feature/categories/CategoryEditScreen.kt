package io.github.anderescaray.cents.ui.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsAlert
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

    if (state.confirmingDeletion) {
        DeletionAlert(
            state = state,
            onConfirm = viewModel::confirmDeletion,
            onDismiss = viewModel::cancelDeletion,
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
            footer = stringResource(R.string.category_name_taken).takeIf { state.nameTaken },
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
                archived = state.archived,
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
        Column(
            modifier = Modifier.padding(CentsDimens.rowHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingM),
        ) {
            CategoryPalette.chunked(COLORS_PER_ROW).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    row.forEach { colorArgb ->
                        ColorSwatch(
                            colorArgb = colorArgb,
                            selected = colorArgb == selected,
                            onSelect = { onSelect(colorArgb) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    colorArgb: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(SWATCH_TAP_SIZE.dp)
                .clip(CircleShape)
                .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(SWATCH_SIZE.dp)
                    .clip(CircleShape)
                    .background(Color(colorArgb))
                    .then(
                        if (selected) {
                            Modifier.border(SWATCH_RING.dp, CentsTheme.colors.label, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
        )
    }
}

@Composable
private fun CategoryActions(
    archived: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    CentsListSection(footer = stringResource(R.string.category_delete_footer)) {
        if (archived) {
            CenteredActionRow(
                label = stringResource(R.string.category_restore),
                color = CentsTheme.colors.accent,
                onClick = onRestore,
                showSeparator = true,
            )
        }
        CenteredActionRow(
            label = stringResource(R.string.category_delete),
            color = CentsTheme.colors.destructive,
            onClick = onDelete,
            showSeparator = false,
        )
    }
}

/** A row whose whole point is its label: centred, tinted, no chevron. */
@Composable
private fun CenteredActionRow(
    label: String,
    color: Color,
    onClick: () -> Unit,
    showSeparator: Boolean,
) {
    Column {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(CentsTheme.colors.groupedCard)
                    .clickable(onClick = onClick)
                    .padding(vertical = CentsDimens.spacingM),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = CentsTheme.typography.body, color = color)
        }
        if (showSeparator) {
            Box(
                modifier =
                    Modifier
                        .padding(start = CentsDimens.rowHorizontalPadding)
                        .fillMaxWidth()
                        .height(CentsDimens.separatorThickness)
                        .background(CentsTheme.colors.separator),
            )
        }
    }
}

/**
 * Says which of the two things the button is about to do, and why, before it happens —
 * an unused category is removed, one with history behind it can only be archived.
 */
@Composable
private fun DeletionAlert(
    state: CategoryEditUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val outright = state.deletionKind == DeletionKind.OUTRIGHT
    CentsAlert(
        title =
            stringResource(
                if (outright) R.string.category_delete_title else R.string.category_archive_title,
                state.name,
            ),
        message =
            if (outright) {
                stringResource(R.string.category_delete_message)
            } else {
                pluralStringResource(
                    R.plurals.category_archive_message,
                    state.transactionCount,
                    state.transactionCount,
                )
            },
        confirmLabel =
            stringResource(if (outright) R.string.action_delete else R.string.action_archive),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        destructive = outright,
    )
}

private const val COLORS_PER_ROW = 6
private const val SWATCH_SIZE = 30
private const val SWATCH_TAP_SIZE = 44
private const val SWATCH_RING = 2
internal const val NAME_FIELD_TAG = "categoryName"
