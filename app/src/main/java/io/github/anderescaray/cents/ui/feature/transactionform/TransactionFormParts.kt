package io.github.anderescaray.cents.ui.feature.transactionform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.ui.components.CategoryBadge
import io.github.anderescaray.cents.ui.components.CentsActionSheet
import io.github.anderescaray.cents.ui.components.CentsChip
import io.github.anderescaray.cents.ui.components.CentsPickerSheet
import io.github.anderescaray.cents.ui.components.CentsSheetAction
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.components.IconBadge
import io.github.anderescaray.cents.ui.components.PickerAction
import io.github.anderescaray.cents.ui.components.PickerChoice
import io.github.anderescaray.cents.ui.components.archivedName
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.relativeLabel

// The pieces of the movement form below its amount: the two choices, the chips, the note and
// the sheets they open.

/**
 * Category and account: one row each, saying what is chosen, that opens the whole list to choose
 * again (`docs/next-improvements.md` §3 and §5). Every category is in the sheet rather than on
 * the form, so a long list costs a scroll in the sheet and nothing on the form.
 */
@Composable
internal fun ChoiceRows(
    state: TransactionFormUiState,
    onCategory: () -> Unit,
    onAccount: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(horizontal = CentsDimens.screenMargin)
                .clip(RoundedCornerShape(CHOICE_RADIUS))
                .background(CentsTheme.colors.fill),
    ) {
        val category = state.categories.firstOrNull { it.id == state.selectedCategoryId }
        ChoiceRow(
            label = stringResource(R.string.field_category),
            value = category?.name ?: stringResource(R.string.form_category_none),
            valueIsNone = category == null,
            leading = {
                CategoryBadge(
                    name = category?.name,
                    colorArgb = category?.colorArgb,
                    size = CHOICE_BADGE,
                    emoji = category?.emoji,
                )
            },
            onClick = onCategory,
            modifier = Modifier.testTag(CATEGORY_ROW_TAG),
        )
        Box(
            Modifier
                .padding(start = CentsDimens.spacingM + CHOICE_BADGE + CentsDimens.spacingM)
                .fillMaxWidth()
                .height(CentsDimens.separatorThickness)
                .background(CentsTheme.colors.separator),
        )
        val account = state.selectedAccount
        ChoiceRow(
            label = stringResource(R.string.field_account),
            value =
                account?.name ?: stringResource(
                    if (state.hasAccounts) R.string.form_account_none_offered else R.string.field_account_empty,
                ),
            valueIsPrompt = account == null,
            leading = {
                IconBadge(
                    iconRes = R.drawable.ic_wallet,
                    color = account?.colorArgb?.let(::Color) ?: CentsTheme.colors.accent,
                    size = CHOICE_BADGE,
                )
            },
            onClick = onAccount,
            modifier = Modifier.testTag(ACCOUNT_ROW_TAG),
        )
    }
}

/** *Category* on the left, what is chosen on the right, and a chevron saying it opens a list. */
@Composable
private fun ChoiceRow(
    label: String,
    value: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueIsPrompt: Boolean = false,
    valueIsNone: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                // Read as one thing — "Category, Groceries, button" — rather than three.
                .semantics(mergeDescendants = true) {}
                .tappable(onClick = onClick)
                .defaultMinSize(minHeight = CentsDimens.minRowHeight)
                .padding(horizontal = CentsDimens.spacingM, vertical = CentsDimens.spacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingM),
    ) {
        leading()
        Text(
            text = label,
            style = CentsTheme.typography.callout,
            color = CentsTheme.colors.secondaryLabel,
            maxLines = 1,
        )
        Text(
            text = value,
            style = CentsTheme.typography.headline,
            color =
                when {
                    valueIsPrompt -> CentsTheme.colors.accentText
                    valueIsNone -> CentsTheme.colors.secondaryLabel
                    else -> CentsTheme.colors.label
                },
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CentsTheme.colors.tertiaryLabel,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Date and group: already answered, and one tap away from a different answer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailChips(
    state: TransactionFormUiState,
    onDate: () -> Unit,
    onGroup: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
    ) {
        CentsChip(
            label = state.date.relativeLabel(state.today),
            iconRes = R.drawable.ic_calendar,
            onClick = onDate,
            modifier = Modifier.testTag(DATE_CHIP_TAG),
        )
        if (state.offeredGroups.isNotEmpty()) {
            CentsChip(
                label =
                    state.groups.firstOrNull { it.id == state.selectedGroupId }?.let {
                        archivedName(
                            it.name,
                            it.archived,
                        )
                    }
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

/**
 * Every category of this kind, three to a row. Choosing one files the movement there and
 * closes the sheet; choosing the one already chosen keeps it. Leaving the movement uncategorized
 * is its own row, so it is never done by accident, and a new category is one more.
 */
@Composable
internal fun CategorySheet(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    onCreateCategory: (CategoryKind) -> Unit,
    onCloseSheet: () -> Unit,
) {
    val selected = state.selectedCategoryId
    val choices =
        remember(state.categories) {
            state.categories.map { PickerChoice(it.id, it.name, colorArgb = it.colorArgb, emoji = it.emoji) }
        }
    val clearLabel = stringResource(R.string.form_category_clear)
    val newLabel = stringResource(R.string.form_category_new)
    CentsPickerSheet(
        title = stringResource(R.string.field_category),
        choices = choices,
        selectedId = selected,
        onChoose = { id ->
            viewModel.selectCategory(id)
            onCloseSheet()
        },
        onDismiss = onCloseSheet,
        grid = true,
        footer =
            if (state.categories.isEmpty()) {
                stringResource(R.string.field_category_empty_footer)
            } else {
                stringResource(R.string.field_category_footer)
            },
        actions =
            buildList {
                if (selected != null) {
                    add(
                        PickerAction(label = clearLabel, iconRes = null) {
                            viewModel.clearCategory()
                            onCloseSheet()
                        },
                    )
                }
                add(
                    PickerAction(label = newLabel, iconRes = R.drawable.ic_plus) {
                        onCloseSheet()
                        viewModel.makingCategory()
                        onCreateCategory(state.kind.toCategoryKind())
                    },
                )
            },
    )
}

/**
 * The accounts a movement can go in. Brokers are left out of an expense (D32) and the foot of the
 * sheet says so, since an account that has quietly gone missing from a list reads as a bug.
 */
@Composable
internal fun AccountSheet(
    state: TransactionFormUiState,
    viewModel: TransactionFormViewModel,
    onCreateAccount: () -> Unit,
    onCloseSheet: () -> Unit,
) {
    val choices =
        remember(state.accounts) {
            state.accounts.map {
                PickerChoice(
                    it.id,
                    it.name,
                    colorArgb = it.colorArgb,
                    iconRes = R.drawable.ic_wallet,
                )
            }
        }
    val newLabel = stringResource(R.string.form_account_new)
    CentsPickerSheet(
        title = stringResource(R.string.field_account),
        choices = choices,
        selectedId = state.selectedAccountId,
        onChoose = { id ->
            viewModel.selectAccount(id)
            onCloseSheet()
        },
        onDismiss = onCloseSheet,
        footer = if (state.brokersLeftOut) stringResource(R.string.form_account_brokers_footer) else null,
        actions =
            listOf(
                PickerAction(label = newLabel, iconRes = R.drawable.ic_plus) {
                    onCloseSheet()
                    // Chosen on the way back, as a category made from its sheet is.
                    viewModel.makingAccount()
                    onCreateAccount()
                },
            ),
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
                            label = archivedName(group.name, group.archived),
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
private val CHOICE_RADIUS = CentsDimens.keyRadius
private val CHOICE_BADGE = 30.dp

internal const val CATEGORY_ROW_TAG = "transactionCategory"
internal const val ACCOUNT_ROW_TAG = "transactionAccount"
internal const val DATE_CHIP_TAG = "transactionDate"
