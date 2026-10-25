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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CategoryBadge
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsColorPicker
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListScreen
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.feature.accounts.DeletionFailedAlert
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
    if (deletion.failed) {
        DeletionFailedAlert(onDismiss = viewModel.deletion::cancel)
    }
}

/** Name, emoji, colour, and the way out of an existing category. */
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

        EmojiSection(state = state, onChoose = viewModel::setEmoji)

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

/**
 * The emoji drawn on the category's colour: one of the common ones in a tap, or any other from
 * the full picker, every emoji the phone has, by group and with a search. The colour section stays
 * underneath, because the ring and the bars are drawn in the colour whatever the emoji is.
 */
@Composable
private fun EmojiSection(
    state: CategoryEditUiState,
    onChoose: (String?) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    CentsListSection(
        header = stringResource(R.string.category_emoji_header),
        footer = stringResource(R.string.category_emoji_footer),
    ) {
        CentsListRow(
            title = stringResource(R.string.category_emoji_all),
            leading = {
                CategoryBadge(name = state.name.ifBlank { null }, colorArgb = state.colorArgb, emoji = state.emoji)
            },
            showChevron = true,
            onClick = { picking = true },
            modifier = Modifier.testTag(ALL_EMOJIS_TAG),
        )
        Column(
            modifier = Modifier.padding(CentsDimens.rowHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
        ) {
            SuggestedEmojis.chunked(EMOJIS_PER_ROW).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { emoji -> EmojiCell(emoji, selected = emoji == state.emoji, onChoose = onChoose) }
                }
            }
        }
        if (state.emoji != null) {
            CentsActionRow(
                label = stringResource(R.string.category_emoji_remove),
                onClick = { onChoose(null) },
            )
        }
    }
    if (picking) {
        EmojiPickerSheet(
            onPick = { emoji ->
                onChoose(emoji)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun EmojiCell(
    emoji: String,
    selected: Boolean,
    onChoose: (String?) -> Unit,
) {
    val label = stringResource(R.string.category_emoji_choose, emoji)
    Box(
        modifier =
            Modifier
                .size(EMOJI_CELL.dp)
                .clip(CircleShape)
                .background(
                    if (selected) CentsTheme.colors.accent.copy(alpha = SELECTED_EMOJI_ALPHA) else Color.Transparent,
                ).border(
                    width = SELECTED_EMOJI_BORDER.dp,
                    color = if (selected) CentsTheme.colors.accent else Color.Transparent,
                    shape = CircleShape,
                ).selectable(selected = selected, role = Role.RadioButton, onClick = { onChoose(emoji) })
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, style = CentsTheme.typography.title3)
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

/**
 * The emojis a money app's categories are most often about, four rows of six; anything else comes
 * from the keyboard.
 */
private val SuggestedEmojis =
    listOf(
        "🛒",
        "🍽️",
        "☕",
        "🍺",
        "🚗",
        "⛽",
        "🚌",
        "✈️",
        "🏠",
        "💡",
        "📱",
        "🎬",
        "🎮",
        "📚",
        "💊",
        "🏋️",
        "👕",
        "🎁",
        "🐶",
        "👶",
        "🧾",
        "💰",
        "💼",
        "📈",
    )

private const val EMOJIS_PER_ROW = 6
private const val EMOJI_CELL = 44
private const val SELECTED_EMOJI_BORDER = 2
private const val SELECTED_EMOJI_ALPHA = 0.1f

internal const val ALL_EMOJIS_TAG = "allEmojis"
