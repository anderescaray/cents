package io.github.anderescaray.cents.ui.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.anderescaray.cents.ui.components.RoundIconButton
import io.github.anderescaray.cents.ui.components.ScreenHeader
import io.github.anderescaray.cents.ui.components.arrive
import io.github.anderescaray.cents.ui.components.movementValueColor
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.feature.inbox.InboxItem
import io.github.anderescaray.cents.ui.feature.inbox.InboxUiState
import io.github.anderescaray.cents.ui.feature.inbox.InboxViewModel
import io.github.anderescaray.cents.ui.feature.inbox.inboxChipsTag
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

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
    CentsCard(modifier = modifier.padding(horizontal = CentsDimens.screenMargin).animateContentSize()) {
        Text(
            text = stringResource(R.string.home_this_month),
            style = CentsTheme.typography.title3,
            color = CentsTheme.colors.label,
            modifier = Modifier.semantics { heading() },
        )
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
        CategoryBadge(name = slice.name ?: "?", colorArgb = slice.colorArgb)
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

/** In, out and what was left, in one line of three. */
@Composable
internal fun MonthTotals(state: HomeUiState) {
    val summary = state.summary ?: return
    val left = state.left ?: return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS)) {
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
        TotalCell(
            label = stringResource(if (left.isNegative) R.string.home_overspent else R.string.home_left),
            value = MoneyFormatter.format(left.copy(minorUnits = kotlin.math.abs(left.minorUnits))),
            color = if (left.isNegative) CentsTheme.colors.destructiveText else CentsTheme.colors.label,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun TotalCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(
                    CentsTheme.colors.fill,
                    shape =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(14.dp),
                ).padding(horizontal = CentsDimens.spacingM, vertical = CentsDimens.spacingS),
    ) {
        Text(text = label, style = CentsTheme.typography.caption1, color = CentsTheme.colors.secondaryLabel)
        Spacer(Modifier.width(0.dp))
        Text(
            text = value,
            style = CentsTheme.typography.subhead,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How many categories the month card lists. */
private const val MONTH_ROWS = 5

/** On a tablet a fraction of the width is a ring the size of the screen. */
private const val RING_MAX = 220
private const val PERCENT = 100
