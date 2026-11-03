package io.github.anderescaray.cents.ui.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.AnimatedMoney
import io.github.anderescaray.cents.ui.components.CategoryBadge
import io.github.anderescaray.cents.ui.components.CentsCard
import io.github.anderescaray.cents.ui.components.CentsChip
import io.github.anderescaray.cents.ui.components.CentsRing
import io.github.anderescaray.cents.ui.components.IconBadge
import io.github.anderescaray.cents.ui.components.RingSlice
import io.github.anderescaray.cents.ui.components.movementValueColor
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.feature.inbox.InboxItem
import io.github.anderescaray.cents.ui.feature.inbox.inboxChipsTag
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel
import io.github.anderescaray.cents.ui.util.weekdayDayMonth
import java.time.LocalDate

// The cards Home is made of: a waiting movement, the bills waiting for a figure, and the month.

/** One waiting movement: what it was, then its categories as chips. One tap files it. */
@Composable
internal fun InboxCard(
    waiting: InboxItem,
    today: LocalDate,
    onOpen: (Long) -> Unit,
    onCategorize: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    CentsCard(
        modifier = modifier.padding(horizontal = CentsDimens.screenMargin),
        contentPadding = PaddingValues(vertical = CentsDimens.spacingL),
    ) {
        InboxCardHeader(waiting = waiting, today = today, onOpen = onOpen)
        Spacer(Modifier.height(CentsDimens.spacingM))
        InboxChoices(waiting = waiting, onCategorize = onCategorize)
    }
}

/** What the movement was: its note or account, when, and how much. Tapping it opens the form. */
@Composable
internal fun InboxCardHeader(
    waiting: InboxItem,
    today: LocalDate,
    onOpen: (Long) -> Unit,
) {
    val transaction = waiting.details.transaction
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tappable(onClick = { onOpen(transaction.id) })
                .padding(horizontal = CentsDimens.spacingL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingM),
    ) {
        CategoryBadge(name = null, colorArgb = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.note?.takeIf { it.isNotBlank() } ?: waiting.details.accountName,
                style = CentsTheme.typography.headline,
                color = CentsTheme.colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = transaction.date.relativeLabel(today) + " · " + waiting.details.accountName,
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = MoneyFormatter.formatSigned(transaction.signedAmount),
            style = CentsTheme.typography.headline,
            color = waiting.details.movementValueColor(),
        )
    }
}

/** The categories it can be filed under, as chips; or why there are none. */
@Composable
internal fun InboxChoices(
    waiting: InboxItem,
    onCategorize: (Long, Long) -> Unit,
) {
    val transaction = waiting.details.transaction
    if (waiting.choices.isEmpty()) {
        Text(
            text =
                stringResource(
                    if (transaction.kind == TransactionKind.INCOME) {
                        R.string.inbox_no_income_categories
                    } else {
                        R.string.inbox_no_expense_categories
                    },
                ),
            style = CentsTheme.typography.footnote,
            color = CentsTheme.colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = CentsDimens.spacingL),
        )
        return
    }
    LazyRow(
        modifier = Modifier.testTag(inboxChipsTag(transaction.id)),
        contentPadding = PaddingValues(horizontal = CentsDimens.spacingL),
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
    ) {
        items(waiting.choices, key = { it.id }) { category ->
            CentsChip(
                label = category.name,
                colorArgb = category.colorArgb,
                emoji = category.emoji,
                onClick = { onCategorize(transaction.id, category.id) },
            )
        }
    }
}

/** Bills and salaries that fell due and are waiting for their figure. */
@Composable
internal fun PendingCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CentsCard(modifier = modifier.padding(horizontal = CentsDimens.screenMargin), onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingM),
        ) {
            IconBadge(iconRes = R.drawable.ic_calendar, color = CentsTheme.colors.warning)
            Column(Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.home_pending_title, count, count),
                    style = CentsTheme.typography.headline,
                    color = CentsTheme.colors.label,
                )
                Text(
                    text = stringResource(R.string.home_pending_message),
                    style = CentsTheme.typography.footnote,
                    color = CentsTheme.colors.secondaryLabel,
                )
            }
        }
    }
}

/** The month: what came in and went out, the ring of where it went, and the categories under it. */
@Composable
internal fun MonthCard(
    state: HomeUiState,
    onOpenCategory: (CategorySpending) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spending = state.spending
    CentsCard(modifier = modifier.padding(horizontal = CentsDimens.screenMargin)) {
        // One heading for TalkBack, the date read with the title it dates.
        Column(modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
            Text(
                text = stringResource(R.string.home_this_month),
                style = CentsTheme.typography.title3,
                color = CentsTheme.colors.label,
            )
            val today = state.today
            Text(
                text = remember(today) { today.weekdayDayMonth() },
                style = CentsTheme.typography.subhead,
                color = CentsTheme.colors.secondaryLabel,
            )
        }
        if (state.summary != null) {
            Spacer(Modifier.height(CentsDimens.spacingM))
            MonthTotals(state)
        }
        Spacer(Modifier.height(CentsDimens.spacingL))
        if (spending == null || spending.isEmpty) {
            Text(
                text = stringResource(R.string.home_nothing_spent),
                style = CentsTheme.typography.subhead,
                color = CentsTheme.colors.secondaryLabel,
            )
            return@CentsCard
        }
        MonthRing(spending = spending, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(CentsDimens.spacingL))
        spending.slices.take(MONTH_ROWS).forEach { slice ->
            CategoryRow(slice = slice, onClick = { onOpenCategory(slice) })
        }
    }
}

/** The ring of the month's categories, with what went out in its middle. */
@Composable
internal fun MonthRing(
    spending: SpendingBreakdown,
    modifier: Modifier = Modifier,
) {
    val fallback = CentsTheme.colors.tertiaryLabel
    CentsRing(
        slices =
            spending.slices.map { slice ->
                RingSlice(
                    key = (slice.categoryId ?: -1L).toString(),
                    share = slice.share,
                    color = slice.colorArgb?.let(::Color) ?: fallback,
                )
            },
        modifier = modifier.widthIn(max = RING_MAX.dp).fillMaxWidth(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedMoney(money = spending.total, style = CentsTheme.typography.title2)
            Text(
                text = stringResource(R.string.home_spent),
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
            )
        }
    }
}

/** One category of the month: its mark, its name and share, and what it came to. */
@Composable
internal fun CategoryRow(
    slice: CategorySpending,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tappable(onClick = onClick)
                .padding(vertical = CentsDimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingM),
    ) {
        CategoryBadge(name = slice.name ?: "?", colorArgb = slice.colorArgb, emoji = slice.emoji)
        Column(Modifier.weight(1f)) {
            Text(
                text = slice.name ?: stringResource(R.string.transactions_uncategorized),
                style = CentsTheme.typography.headline,
                color = CentsTheme.colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.reports_share, (slice.share * PERCENT).toInt()),
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
            )
        }
        Text(
            text = MoneyFormatter.format(slice.total),
            style = CentsTheme.typography.headline,
            color = CentsTheme.colors.label,
        )
    }
}

/**
 * What the month comes to, as the headline: *Left this month* in green or *Overspent this month* in
 * red, large, on a panel tinted the same way — the one number on Home that should catch the eye —
 * with what came in and what went out beside it, smaller.
 */
@Composable
internal fun MonthTotals(state: HomeUiState) {
    val summary = state.summary ?: return
    val left = state.left ?: return
    val over = left.isNegative
    val tone = if (over) CentsTheme.colors.destructive else CentsTheme.colors.positive
    val toneText = if (over) CentsTheme.colors.destructiveText else CentsTheme.colors.positiveText
    Column(verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingS)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PANEL_RADIUS.dp))
                    // Strongest where the label starts, fading across: a wash, not a block.
                    .background(
                        Brush.horizontalGradient(
                            listOf(tone.copy(alpha = PANEL_TINT), tone.copy(alpha = PANEL_TINT_END)),
                        ),
                    ).padding(horizontal = CentsDimens.spacingL, vertical = CentsDimens.spacingM),
        ) {
            Text(
                text = stringResource(if (over) R.string.home_overspent_month else R.string.home_left_month),
                style = CentsTheme.typography.subhead,
                color = toneText,
            )
            AnimatedMoney(
                money = left.copy(minorUnits = kotlin.math.abs(left.minorUnits)),
                style = CentsTheme.typography.display,
                color = toneText,
                withSign = false,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS)) {
            TotalCell(
                label = stringResource(R.string.home_in),
                value = MoneyFormatter.format(summary.income),
                color = CentsTheme.colors.positiveText,
                modifier = Modifier.weight(1f),
            )
            TotalCell(
                label = stringResource(R.string.home_out),
                value = MoneyFormatter.format(summary.expenses),
                color = CentsTheme.colors.label,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun TotalCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(PANEL_RADIUS.dp))
                .background(CentsTheme.colors.fill)
                .padding(horizontal = CentsDimens.spacingM, vertical = CentsDimens.spacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = CentsTheme.typography.footnote,
            color = CentsTheme.colors.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = CentsTheme.typography.headline,
            color = color,
            maxLines = 1,
        )
    }
}

private const val PANEL_RADIUS = 16
private const val PANEL_TINT = 0.16f
private const val PANEL_TINT_END = 0.04f

/** How many categories the month card lists. */
private const val MONTH_ROWS = 5

/** On a tablet a fraction of the width is a ring the size of the screen. */
private const val RING_MAX = 220
private const val PERCENT = 100
