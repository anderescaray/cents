package io.github.anderescaray.cents.ui.feature.transactionform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.ui.components.CentsActionSheet
import io.github.anderescaray.cents.ui.components.CentsChip
import io.github.anderescaray.cents.ui.components.CentsSheetAction
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.relativeLabel

// The pieces of the movement form below its amount: the chips, the note and the two pickers.

/**
 * The categories of this kind as chips, one tap each; tapping the chosen one again leaves the
 * movement uncategorized, to be filed later. The last chip makes a new category.
 */
@Composable
internal fun CategoryChips(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    onCreateCategory: (CategoryKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingS)) {
        FieldLabel(R.string.field_category)
        LazyRow(
            contentPadding = PaddingValues(horizontal = CentsDimens.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
        ) {
            items(state.categories, key = { it.id }) { category ->
                CentsChip(
                    label = category.name,
                    colorArgb = category.colorArgb,
                    selected = category.id == state.selectedCategoryId,
                    onClick = {
                        viewModel.selectCategory(category.id.takeIf { it != state.selectedCategoryId })
                    },
                )
            }
            item(key = "new") {
                CentsChip(
                    label = stringResource(R.string.form_new_category),
                    iconRes = R.drawable.ic_plus,
                    onClick = { onCreateCategory(state.kind.toCategoryKind()) },
                )
            }
        }
    }
}

/** Account, date and group: already answered, and one tap away from a different answer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailChips(
    state: TransactionFormUiState,
    onAccount: () -> Unit,
    onDate: () -> Unit,
    onGroup: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
    ) {
        val account = state.selectedAccount
        CentsChip(
            label = account?.name ?: stringResource(R.string.field_account_empty),
            iconRes = R.drawable.ic_wallet,
            selected = account == null,
            onClick = onAccount,
        )
        CentsChip(
            label = state.date.relativeLabel(state.today),
            iconRes = R.drawable.ic_calendar,
            onClick = onDate,
        )
        if (state.groups.isNotEmpty()) {
            CentsChip(
                label =
                    state.groups.firstOrNull { it.id == state.selectedGroupId }?.name
                        ?: stringResource(R.string.field_group),
                iconRes = R.drawable.ic_tab_home,
                selected = state.selectedGroupId != null,
                onClick = onGroup,
            )
        }
    }
}

@Composable
internal fun NoteField(
    note: String,
    onNoteChange: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .padding(horizontal = CentsDimens.screenMargin)
                .clip(RoundedCornerShape(NOTE_RADIUS))
                .background(CentsTheme.colors.fill),
    ) {
        CentsTextField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = stringResource(R.string.field_note_placeholder),
            modifier =
                Modifier
                    .testTag(NOTE_FIELD_TAG)
                    .onFocusChanged { onFocus(it.isFocused) },
        )
    }
}

@Composable
internal fun FieldLabel(res: Int) {
    Text(
        text = stringResource(res).uppercase(),
        style = CentsTheme.typography.overline,
        color = CentsTheme.colors.secondaryLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = CentsDimens.screenMargin + CentsDimens.spacingXs),
    )
}

/** Which account the money moved in. */
@Composable
internal fun AccountSheet(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    onCloseSheet: () -> Unit,
) {
    CentsActionSheet(
        title = stringResource(R.string.field_account),
        message = "",
        actions =
            state.accounts.map { account ->
                CentsSheetAction(
                    label = account.name,
                    onClick = {
                        viewModel.selectAccount(account.id)
                        onCloseSheet()
                    },
                )
            },
        cancelLabel = stringResource(R.string.action_cancel),
        onDismiss = onCloseSheet,
    )
}

/** The groups this movement's date fits, and *No group* when it is in one already. */
@Composable
internal fun GroupSheet(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    onCloseSheet: () -> Unit,
) {
    val selected = state.selectedGroupId
    CentsActionSheet(
        title = stringResource(R.string.field_group),
        message = stringResource(R.string.field_group_footer),
        actions =
            buildList {
                state.offeredGroups.forEach { group ->
                    add(
                        CentsSheetAction(
                            label = group.name,
                            onClick = {
                                if (group.id != selected) viewModel.selectGroup(group.id)
                                onCloseSheet()
                            },
                        ),
                    )
                }
                if (selected != null) {
                    add(
                        CentsSheetAction(
                            label = stringResource(R.string.form_no_group),
                            onClick = {
                                viewModel.selectGroup(selected)
                                onCloseSheet()
                            },
                        ),
                    )
                }
            },
        cancelLabel = stringResource(R.string.action_cancel),
        onDismiss = onCloseSheet,
    )
}

private val NOTE_RADIUS = CentsDimens.keyRadius
