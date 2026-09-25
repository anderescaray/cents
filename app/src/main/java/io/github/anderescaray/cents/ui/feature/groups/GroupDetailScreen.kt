package io.github.anderescaray.cents.ui.feature.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.GroupSummary
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.ui.components.CentsBackButton
import io.github.anderescaray.cents.ui.components.CentsLargeTitleScaffold
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.feature.reports.Swatch
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingDonut
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter

@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenMovements: () -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Deleted from its own editor: there is nothing left here to look at.
    LaunchedEffect(state.isGone) {
        if (state.isGone) onBack()
    }

    GroupDetailContent(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onShowAll = {
            // The filter first and the tab second, so the list is already narrowed when it
            // slides in — the order the Reports tab drills down in, for the same reason.
            viewModel.showMovements()
            onOpenMovements()
        },
        onShowSlice = { slice, kind ->
            viewModel.showMovements(slice, kind)
            onOpenMovements()
        },
    )
}

/** The screen without its view model, so a test can draw it from a state. */
@Composable
internal fun GroupDetailContent(
    state: GroupDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onShowAll: () -> Unit,
    onShowSlice: (CategorySpending, TransactionKind) -> Unit,
) {
    val summary = state.summary
    CentsLargeTitleScaffold(
        title = summary?.group?.name ?: stringResource(R.string.groups_title),
        bottomPadding = CentsDimens.sectionSpacing,
        leading = { CentsBackButton(label = stringResource(R.string.groups_title), onClick = onBack) },
        trailing = {
            if (summary != null) {
                Text(
                    text = stringResource(R.string.action_edit),
                    style = CentsTheme.typography.body,
                    color = CentsTheme.colors.accent,
                    modifier =
                        Modifier
                            .testTag(EDIT_GROUP_TAG)
                            .clickable { onEdit(summary.group.id) }
                            .padding(CentsDimens.spacingXs),
                )
            }
        },
    ) {
        if (summary == null) return@CentsLargeTitleScaffold

        item(key = "net") { NetHeader(summary) }
        totals(summary = summary, onShowAll = onShowAll)
        if (state.spending.isNotEmpty()) {
            breakdown(
                key = "spending",
                header = R.string.group_spending_header,
                slices = state.spending,
                withRing = true,
                onShow = { onShowSlice(it, TransactionKind.EXPENSE) },
            )
        }
        if (state.income.isNotEmpty()) {
            breakdown(
                key = "income",
                header = R.string.group_income_header,
                slices = state.income,
                withRing = false,
                onShow = { onShowSlice(it, TransactionKind.INCOME) },
            )
        }
    }
}

/** The answer, first and largest: *Cost you 150 €*. */
@Composable
private fun NetHeader(summary: GroupSummary) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = CentsDimens.spacingM),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingXs),
    ) {
        Text(
            text = summary.netSentence(),
            style = CentsTheme.typography.title2,
            color = if (summary.isEmpty) CentsTheme.colors.secondaryLabel else summary.netColor(),
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(GROUP_NET_TAG).padding(horizontal = CentsDimens.screenMargin),
        )
    }
}

/** What made the answer up: in, out, how many, and when — and the way to the movements. */
private fun LazyListScope.totals(
    summary: GroupSummary,
    onShowAll: () -> Unit,
) {
    item(key = "totals") {
        CentsListSection {
            CentsListRow(title = stringResource(R.string.group_came_in), value = MoneyFormatter.format(summary.income))
            CentsListRow(
                title = stringResource(R.string.group_went_out),
                value = MoneyFormatter.format(summary.expenses),
            )
            val first = summary.firstDate
            val last = summary.lastDate
            if (first != null && last != null) {
                CentsListRow(title = stringResource(R.string.group_dates), value = spanLabel(first, last))
            }
            CentsListRow(
                title = stringResource(R.string.group_see_movements),
                value = summary.movementCount.toString(),
                showChevron = true,
                showSeparator = false,
                onClick = if (summary.isEmpty) null else onShowAll,
                modifier = Modifier.testTag(GROUP_MOVEMENTS_TAG),
            )
        }
    }
}

/**
 * One direction, by category: the ring for what went out, since that is the question a trip is
 * asked; a plain list for what came in, which is usually one or two rows and no ring's worth.
 */
private fun LazyListScope.breakdown(
    key: String,
    header: Int,
    slices: List<CategorySpending>,
    withRing: Boolean,
    onShow: (CategorySpending) -> Unit,
) {
    item(key = key) {
        CentsListSection(header = stringResource(header)) {
            if (withRing) {
                Box(Modifier.padding(vertical = CentsDimens.spacingM)) {
                    SpendingDonut(slices = slices, onSliceClick = onShow)
                }
            }
            slices.forEachIndexed { index, slice ->
                CentsListRow(
                    title = slice.name ?: stringResource(R.string.reports_uncategorized),
                    subtitle = stringResource(R.string.reports_share, (slice.share * PERCENT).toInt()),
                    value = MoneyFormatter.format(slice.total),
                    leading = { Swatch(slice.colorArgb?.let { Color(it) } ?: CentsTheme.colors.tertiaryLabel) },
                    showChevron = true,
                    showSeparator = index != slices.lastIndex,
                    onClick = { onShow(slice) },
                )
            }
        }
    }
}

private const val PERCENT = 100
internal const val EDIT_GROUP_TAG = "editGroup"
internal const val GROUP_NET_TAG = "groupNet"
internal const val GROUP_MOVEMENTS_TAG = "groupMovements"
