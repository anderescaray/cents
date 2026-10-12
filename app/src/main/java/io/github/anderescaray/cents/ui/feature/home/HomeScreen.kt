package io.github.anderescaray.cents.ui.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsCard
import io.github.anderescaray.cents.ui.components.IconBadge
import io.github.anderescaray.cents.ui.components.RoundIconButton
import io.github.anderescaray.cents.ui.components.ScreenHeader
import io.github.anderescaray.cents.ui.components.arrive
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.feature.inbox.InboxUiState
import io.github.anderescaray.cents.ui.feature.inbox.InboxViewModel
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.formatLocale
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** The day under the month: *Wednesday, 1 October*, written the phone's way. */
private const val DAY_PATTERN = "EEEE, d MMMM"

private const val STATUS_SCRIM_ALPHA = 0.94f

/** How many waiting movements Home shows before handing over to the full inbox. */
private const val INBOX_PREVIEW = 3

/**
 * Home: the screen the app opens on, built for the two things done most — **recording a movement
 * in a few taps** and **filing the ones capture wrote on its own** — with the month underneath,
 * as a ring of where the money went.
 */
@Composable
fun HomeScreen(
    onAdd: (TransactionKind) -> Unit,
    onTransfer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenPending: () -> Unit,
    onOpenMovements: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    inbox: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val waiting by inbox.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        inbox = waiting,
        actions =
            HomeActions(
                onAdd = onAdd,
                onTransfer = onTransfer,
                onOpenSettings = onOpenSettings,
                onOpenInbox = onOpenInbox,
                onOpenTransaction = onOpenTransaction,
                onCategorize = inbox::categorize,
                onOpenPending = onOpenPending,
                onOpenCategory = { slice ->
                    viewModel.openCategory(slice)
                    onOpenMovements()
                },
                onCreateAccount = onCreateAccount,
            ),
    )
}

/** Everything Home can do, gathered so the content can be drawn from a state in a test. */
internal data class HomeActions(
    val onAdd: (TransactionKind) -> Unit = {},
    val onTransfer: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenInbox: () -> Unit = {},
    val onOpenTransaction: (Long) -> Unit = {},
    val onCategorize: (transactionId: Long, categoryId: Long) -> Unit = { _, _ -> },
    val onOpenPending: () -> Unit = {},
    val onOpenCategory: (CategorySpending) -> Unit = {},
    val onCreateAccount: () -> Unit = {},
)

@Composable
internal fun HomeContent(
    state: HomeUiState,
    inbox: InboxUiState,
    actions: HomeActions,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier = Modifier.fillMaxSize().background(CentsTheme.colors.groupedBackground)) {
        HomeList(state = state, inbox = inbox, actions = actions, topInset = topInset)
        // The status bar is drawn over the app: without a backdrop the title scrolls up under
        // the clock and the two are read as one.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(topInset)
                    .background(CentsTheme.colors.groupedBackground.copy(alpha = STATUS_SCRIM_ALPHA)),
        )
    }
}

@Composable
private fun HomeList(
    state: HomeUiState,
    inbox: InboxUiState,
    actions: HomeActions,
    topInset: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topInset + CentsDimens.spacingS, bottom = CentsDimens.spacingXl),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.sectionSpacing),
    ) {
        item(key = "header") {
            val locale = formatLocale()
            ScreenHeader(
                title =
                    state.today.month
                        .getDisplayName(TextStyle.FULL_STANDALONE, locale)
                        .replaceFirstChar { it.titlecase(locale) },
                subtitle =
                    state.today
                        .format(DateTimeFormatter.ofPattern(DAY_PATTERN, locale))
                        .replaceFirstChar { it.titlecase(locale) },
            ) {
                RoundIconButton(
                    iconRes = R.drawable.ic_tab_settings,
                    contentDescription = stringResource(R.string.tab_settings),
                    onClick = actions.onOpenSettings,
                )
            }
        }

        item(key = "add") {
            QuickAdd(
                hasAccounts = state.hasAccounts,
                onAdd = actions.onAdd,
                onTransfer = actions.onTransfer,
                onCreateAccount = actions.onCreateAccount,
                modifier = Modifier.arrive(0),
            )
        }

        toCategorize(inbox = inbox, actions = actions)

        if (state.pendingCount > 0) {
            item(key = "pending") {
                PendingCard(count = state.pendingCount, onClick = actions.onOpenPending, modifier = Modifier.arrive(2))
            }
        }

        if (!state.isLoading) {
            item(key = "month") {
                MonthCard(state = state, onOpenCategory = actions.onOpenCategory, modifier = Modifier.arrive(3))
            }
        }
    }
}

/** Three big ways in: money out, money in, money between your own accounts. */
@Composable
private fun QuickAdd(
    hasAccounts: Boolean,
    onAdd: (TransactionKind) -> Unit,
    onTransfer: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = CentsDimens.screenMargin)) {
        if (!hasAccounts) {
            CentsCard(onClick = onCreateAccount) {
                Text(
                    text = stringResource(R.string.home_no_accounts_title),
                    style = CentsTheme.typography.headline,
                    color = CentsTheme.colors.label,
                )
                Text(
                    text = stringResource(R.string.home_no_accounts_message),
                    style = CentsTheme.typography.footnote,
                    color = CentsTheme.colors.secondaryLabel,
                )
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingM)) {
            QuickAddTile(
                label = stringResource(R.string.kind_expense),
                iconRes = R.drawable.ic_arrow_up,
                color = CentsTheme.colors.destructive,
                onClick = { onAdd(TransactionKind.EXPENSE) },
                modifier = Modifier.weight(1f),
            )
            QuickAddTile(
                label = stringResource(R.string.kind_income),
                iconRes = R.drawable.ic_arrow_down,
                color = CentsTheme.colors.positive,
                onClick = { onAdd(TransactionKind.INCOME) },
                modifier = Modifier.weight(1f),
            )
            QuickAddTile(
                label = stringResource(R.string.kind_transfer),
                iconRes = R.drawable.ic_transfer,
                color = CentsTheme.colors.accent,
                onClick = onTransfer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickAddTile(
    label: String,
    iconRes: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CentsCard(
        modifier = modifier,
        onClick = onClick,
        onClickLabel = stringResource(R.string.home_add_label, label),
        contentPadding = PaddingValues(vertical = CentsDimens.spacingL, horizontal = CentsDimens.spacingS),
    ) {
        IconBadge(
            iconRes = iconRes,
            color = color,
            size = 44.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(CentsDimens.spacingS))
        Text(
            text = label,
            style = CentsTheme.typography.subhead,
            color = CentsTheme.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/** The movements waiting for a category, each one tap from filed — the inbox, brought forward. */
private fun LazyListScope.toCategorize(
    inbox: InboxUiState,
    actions: HomeActions,
) {
    if (inbox.isLoading || inbox.items.isEmpty()) return
    item(key = "inbox-title") {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CentsDimens.screenMargin)
                    .arrive(1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.inbox_title),
                style = CentsTheme.typography.title3,
                color = CentsTheme.colors.label,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (inbox.items.size > INBOX_PREVIEW) {
                Text(
                    text = stringResource(R.string.home_see_all, inbox.items.size),
                    style = CentsTheme.typography.subhead,
                    color = CentsTheme.colors.accentText,
                    modifier = Modifier.tappable(onClick = actions.onOpenInbox),
                )
            }
        }
    }
    if (inbox.writeFailed) {
        item(key = "inbox-failed") {
            Text(
                text = stringResource(R.string.inbox_write_failed),
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.destructiveText,
                modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
            )
        }
    }
    items(inbox.items.take(INBOX_PREVIEW), key = { "inbox-${it.details.transaction.id}" }) { item ->
        InboxCard(
            waiting = item,
            today = inbox.today,
            onOpen = actions.onOpenTransaction,
            onCategorize = actions.onCategorize,
            modifier = Modifier.animateItem().arrive(1),
        )
    }
}
