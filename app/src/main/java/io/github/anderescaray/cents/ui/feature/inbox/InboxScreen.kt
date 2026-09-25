package io.github.anderescaray.cents.ui.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.movementValueColor
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel

@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InboxContent(
        state = state,
        onBack = onBack,
        onOpenTransaction = onOpenTransaction,
        onCategorize = viewModel::categorize,
    )
}

/** The screen without its view model, so a test can draw it from a state. */
@Composable
internal fun InboxContent(
    state: InboxUiState,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onCategorize: (transactionId: Long, categoryId: Long) -> Unit,
) {
    CentsLargeTitleScaffold(
        title = stringResource(R.string.inbox_title),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.tab_transactions), onClick = onBack) },
    ) {
        if (state.isLoading) return@CentsLargeTitleScaffold

        if (state.isEmpty) {
            item(key = "empty") {
                CentsListSection(footer = stringResource(R.string.inbox_empty_message)) {
                    CentsListRow(title = stringResource(R.string.inbox_empty_title), showSeparator = false)
                }
            }
            return@CentsLargeTitleScaffold
        }

        if (state.writeFailed) {
            item(key = "failed") {
                Text(
                    text = stringResource(R.string.inbox_write_failed),
                    style = CentsTheme.typography.footnote,
                    color = CentsTheme.colors.destructive,
                    modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
                )
            }
        }
        inboxItems(state = state, onOpenTransaction = onOpenTransaction, onCategorize = onCategorize)
    }
}

/**
 * One card per movement: what it was, and underneath, its categories as a row of chips — one tap
 * files it and it leaves the screen. Tapping the movement itself opens the full form, for the
 * one that needs more than a category.
 */
private fun LazyListScope.inboxItems(
    state: InboxUiState,
    onOpenTransaction: (Long) -> Unit,
    onCategorize: (Long, Long) -> Unit,
) {
    state.items.forEach { item ->
        val transaction = item.details.transaction
        item(key = "movement-${transaction.id}") {
            CentsListSection(
                header = transaction.date.relativeLabel(state.today),
                footer = if (item.choices.isEmpty()) stringResource(item.noChoicesMessage()) else null,
            ) {
                CentsListRow(
                    title = transaction.note?.takeIf { it.isNotBlank() } ?: item.details.accountName,
                    subtitle = if (transaction.note.isNullOrBlank()) null else item.details.accountName,
                    value = MoneyFormatter.formatSigned(transaction.signedAmount),
                    valueColor = item.details.movementValueColor(),
                    showChevron = true,
                    showSeparator = item.choices.isNotEmpty(),
                    onClick = { onOpenTransaction(transaction.id) },
                )
                if (item.choices.isNotEmpty()) {
                    CategoryChips(
                        choices = item.choices,
                        onChoose = { onCategorize(transaction.id, it.id) },
                        modifier = Modifier.testTag(inboxChipsTag(transaction.id)),
                    )
                }
            }
        }
    }
}

private fun InboxItem.noChoicesMessage(): Int =
    if (details.transaction.kind == TransactionKind.INCOME) {
        R.string.inbox_no_income_categories
    } else {
        R.string.inbox_no_expense_categories
    }

@Composable
private fun CategoryChips(
    choices: List<Category>,
    onChoose: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = CentsDimens.rowHorizontalPadding, vertical = CentsDimens.spacingM),
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
    ) {
        items(choices, key = { it.id }) { category ->
            CategoryChip(category = category, onClick = { onChoose(category) })
        }
    }
}

/** A category as a pill, in its own colour: the same swatch the Reports ring paints it with. */
@Composable
private fun CategoryChip(
    category: Category,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(CHIP_RADIUS.dp))
                .background(CentsTheme.colors.groupedBackground)
                .clickable(onClick = onClick)
                .padding(horizontal = CentsDimens.spacingM, vertical = CentsDimens.spacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
    ) {
        Box(
            Modifier
                .size(DOT_SIZE.dp)
                .clip(CircleShape)
                .background(Color(category.colorArgb)),
        )
        Text(text = category.name, style = CentsTheme.typography.subhead, color = CentsTheme.colors.label)
    }
}

internal fun inboxChipsTag(transactionId: Long) = "inboxChips-$transactionId"

private const val CHIP_RADIUS = 16
private const val DOT_SIZE = 10
