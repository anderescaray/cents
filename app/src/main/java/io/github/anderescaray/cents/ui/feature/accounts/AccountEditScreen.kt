package io.github.anderescaray.cents.ui.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsAlert
import io.github.anderescaray.cents.ui.components.CentsColorPicker
import io.github.anderescaray.cents.ui.components.CentsKeypad
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import java.util.Currency
import java.util.Locale

/**
 * Creating or editing one account: what it is called, what kind it is, what it is
 * denominated in, and what was in it before Cents started counting.
 */
@Composable
fun AccountEditScreen(
    onDone: () -> Unit,
    viewModel: AccountEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onDone()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .imePadding(),
    ) {
        CentsModalBar(
            title =
                stringResource(
                    if (state.isNew) R.string.account_new_title else R.string.account_edit_title,
                ),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
        )

        AccountForm(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))

        CentsKeypad(
            onDigit = viewModel::appendDigit,
            onDelete = viewModel::deleteDigit,
            modifier =
                Modifier.padding(
                    bottom =
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            CentsDimens.spacingS,
                ),
        )
    }

    if (state.confirmingRemoval) {
        RemovalAlert(
            state = state,
            onConfirm = viewModel::confirmRemoval,
            onDismiss = viewModel::cancelRemoval,
        )
    }
}

@Composable
private fun AccountForm(
    state: AccountEditUiState,
    viewModel: AccountEditViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
    ) {
        item(key = "opening") { OpeningBalanceHeader(state = state, onToggleSign = viewModel::toggleSign) }

        item(key = "name") {
            CentsListSection(
                header = stringResource(R.string.account_name_header),
                footer =
                    when {
                        state.nameTaken -> stringResource(R.string.account_name_taken)
                        state.writeFailed -> stringResource(R.string.account_write_failed)
                        else -> null
                    },
            ) {
                CentsTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    placeholder = stringResource(R.string.account_name_placeholder),
                    modifier = Modifier.testTag(NAME_FIELD_TAG),
                )
            }
        }

        typeSection(selected = state.type, onSelect = viewModel::setType)
        currencySection(state = state, onSelect = viewModel::setCurrency)
        colorSection(selected = state.colorArgb, onSelect = viewModel::setColor)

        if (!state.isNew) {
            accountActions(state = state, onRestore = viewModel::restore, onRemove = viewModel::requestRemoval)
        }
    }
}

/** The amount the account held before Cents knew about it — and its sign. */
@Composable
private fun OpeningBalanceHeader(
    state: AccountEditUiState,
    onToggleSign: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = CentsDimens.spacingL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingXs),
    ) {
        Text(
            text = MoneyFormatter.format(state.openingBalance),
            style = CentsTheme.typography.largeTitle,
            color =
                if (state.openingBalance.isNegative) {
                    CentsTheme.colors.destructive
                } else {
                    CentsTheme.colors.label
                },
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.account_opening_balance),
            style = CentsTheme.typography.footnote,
            color = CentsTheme.colors.secondaryLabel,
        )
        Text(
            text =
                stringResource(
                    if (state.openingNegative) R.string.account_owed else R.string.account_held,
                ),
            style = CentsTheme.typography.subhead,
            color = CentsTheme.colors.accent,
            modifier =
                Modifier
                    .testTag(SIGN_TOGGLE_TAG)
                    .clickable(onClick = onToggleSign)
                    .padding(CentsDimens.spacingXs),
        )
    }
}

private fun LazyListScope.typeSection(
    selected: AccountType,
    onSelect: (AccountType) -> Unit,
) {
    item(key = "type") {
        CentsListSection(header = stringResource(R.string.account_type_header)) {
            AccountType.entries.forEachIndexed { index, type ->
                CentsListRow(
                    title = stringResource(type.labelRes()),
                    value = if (type == selected) SELECTED_MARK else null,
                    valueColor = CentsTheme.colors.accent,
                    showSeparator = index != AccountType.entries.lastIndex,
                    onClick = { onSelect(type) },
                )
            }
        }
    }
}

/**
 * The currency is chosen once. Movements are stored denominated in it, so changing it
 * later would silently restate every amount the account holds (rule 2).
 */
private fun LazyListScope.currencySection(
    state: AccountEditUiState,
    onSelect: (String) -> Unit,
) {
    item(key = "currency") {
        val options = currencyOptions(state.currency)
        CentsListSection(
            header = stringResource(R.string.account_currency_header),
            footer = stringResource(R.string.account_currency_locked).takeIf { state.currencyLocked },
        ) {
            if (state.currencyLocked) {
                CentsListRow(
                    title = stringResource(R.string.account_currency_header),
                    value = state.currency,
                    showSeparator = false,
                )
            } else {
                options.forEachIndexed { index, code ->
                    CentsListRow(
                        title = code,
                        subtitle = currencyName(code),
                        value = if (code == state.currency) SELECTED_MARK else null,
                        valueColor = CentsTheme.colors.accent,
                        showSeparator = index != options.lastIndex,
                        onClick = { onSelect(code) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.colorSection(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    item(key = "color") {
        CentsListSection(header = stringResource(R.string.account_color_header)) {
            CentsColorPicker(colors = AccountPalette, selected = selected, onSelect = onSelect)
        }
    }
}

private fun LazyListScope.accountActions(
    state: AccountEditUiState,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
) {
    item(key = "actions") {
        // An archived account that holds movements has nothing left for "delete" to do:
        // archiving it again would change nothing at all.
        val removable = !state.archived || state.removal == AccountRemoval.OUTRIGHT

        CentsListSection(
            footer =
                stringResource(
                    if (removable) R.string.account_delete_footer else R.string.accounts_archived_footer,
                ),
        ) {
            if (state.archived) {
                CentsActionRow(
                    label = stringResource(R.string.account_restore),
                    onClick = onRestore,
                    showSeparator = removable,
                )
            }
            if (removable) {
                CentsActionRow(
                    label = stringResource(R.string.account_delete),
                    onClick = onRemove,
                    destructive = true,
                )
            }
        }
    }
}

/** Says whether the account is about to be deleted or archived, and why. */
@Composable
private fun RemovalAlert(
    state: AccountEditUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val outright = state.removal == AccountRemoval.OUTRIGHT
    CentsAlert(
        title =
            stringResource(
                if (outright) R.string.account_delete_title else R.string.account_archive_title,
                state.name,
            ),
        message =
            if (outright) {
                stringResource(R.string.account_delete_message)
            } else {
                pluralStringResource(
                    R.plurals.account_archive_message,
                    state.transactionCount,
                    state.transactionCount,
                )
            },
        confirmLabel = stringResource(if (outright) R.string.action_delete else R.string.action_archive),
        onConfirm = onConfirm,
        dismissLabel = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        destructive = outright,
    )
}

/** A short list rather than every currency on earth: the device's, plus the usual ones. */
internal fun currencyOptions(
    selected: String,
    locale: Locale = Locale.getDefault(),
): List<String> {
    val device = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
    return (listOfNotNull(device) + COMMON_CURRENCIES + selected).distinct()
}

private fun currencyName(code: String): String? = runCatching { Currency.getInstance(code).displayName }.getOrNull()

private val COMMON_CURRENCIES = listOf("EUR", "USD", "GBP", "CHF", "JPY")
private const val SELECTED_MARK = "✓"
internal const val NAME_FIELD_TAG = "accountName"
internal const val SIGN_TOGGLE_TAG = "accountSign"
