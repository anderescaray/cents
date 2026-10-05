package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.feature.filters.SelectableRow
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The lower half of the rule form: where the money comes from and goes to, how it is recorded,
 * and how to be rid of it.
 *
 * Split off `RuleFormScreen.kt` to keep either file readable — one screen, two files, divided
 * where the form stops being about *what repeats* and starts being about *where it lands*.
 */
internal fun LazyListScope.accountSection(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
    onCreateAccount: () -> Unit,
) {
    item(key = "account") {
        if (state.accounts.isEmpty()) {
            CentsListSection(
                header = stringResource(R.string.field_account),
                footer = stringResource(R.string.field_account_empty_footer),
            ) {
                CentsListRow(
                    title = stringResource(R.string.field_account_empty),
                    showChevron = true,
                    showSeparator = false,
                    onClick = onCreateAccount,
                )
            }
        } else {
            CentsListSection(header = stringResource(R.string.field_account)) {
                state.accounts.forEachIndexed { index, account ->
                    key(account.id) {
                        SelectableRow(
                            title = account.name,
                            selected = account.id == state.accountId,
                            onClick = { viewModel.selectAccount(account.id) },
                            last = index == state.accounts.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

internal fun LazyListScope.categorySection(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
) {
    if (state.categories.isEmpty()) return
    item(key = "category") {
        CentsListSection(
            header = stringResource(R.string.field_category),
            footer = stringResource(R.string.field_category_footer),
        ) {
            state.categories.forEachIndexed { index, category ->
                key(category.id) {
                    SelectableRow(
                        title = category.name,
                        selected = category.id == state.categoryId,
                        onClick = {
                            viewModel.selectCategory(category.id.takeIf { it != state.categoryId })
                        },
                        last = index == state.categories.lastIndex,
                    )
                }
            }
        }
    }
}

/**
 * Whether the app records the charge or waits to be told.
 *
 * *Confirm first* is for an amount that varies — the electricity bill — and today it means the
 * rule generates nothing at all rather than asserting a number nobody has seen (PRD §4.4). The
 * footer says so plainly, because a setting that silently does less than it sounds like is worse
 * than not offering it.
 */
@Composable
internal fun ModeSection(
    state: RuleFormUiState,
    viewModel: RuleFormViewModel,
) {
    CentsListSection(
        header = stringResource(R.string.rule_field_mode),
        footer = stringResource(R.string.rule_field_mode_footer),
    ) {
        SelectableRow(
            title = stringResource(R.string.rule_mode_automatic),
            selected = state.mode == RuleMode.AUTOMATIC,
            onClick = { viewModel.setMode(RuleMode.AUTOMATIC) },
        )
        SelectableRow(
            title = stringResource(R.string.rule_mode_confirm),
            selected = state.mode == RuleMode.CONFIRM,
            onClick = { viewModel.setMode(RuleMode.CONFIRM) },
            last = true,
        )
    }
}

/** Stopping the rule — or removing it outright, when it has never produced anything. */
internal fun LazyListScope.removalAction(
    state: RuleFormUiState,
    onRemove: () -> Unit,
) {
    item(key = "remove") {
        CentsListSection(
            footer =
                when {
                    state.stopsRatherThanDeletes -> stringResource(R.string.rule_stop_footer)
                    state.hasHistory -> stringResource(R.string.rule_delete_kept_footer)
                    else -> null
                },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(CentsTheme.colors.groupedCard)
                        .tappable(onClick = onRemove)
                        .padding(vertical = CentsDimens.spacingM),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        stringResource(
                            if (state.stopsRatherThanDeletes) R.string.rule_stop else R.string.rule_delete,
                        ),
                    style = CentsTheme.typography.body,
                    color = CentsTheme.colors.destructiveText,
                )
            }
        }
    }
}
