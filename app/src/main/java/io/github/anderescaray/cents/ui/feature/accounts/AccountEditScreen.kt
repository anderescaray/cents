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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.ui.components.AmountKeypadState
import io.github.anderescaray.cents.ui.components.CentsActionRow
import io.github.anderescaray.cents.ui.components.CentsColorPicker
import io.github.anderescaray.cents.ui.components.CentsKeypad
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.components.rememberAmountKeypadState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter

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
    val removal by viewModel.removal.state.collectAsStateWithLifecycle()
    val keypad = rememberAmountKeypadState(shownInitially = state.isNew)
    val focusManager = LocalFocusManager.current

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

        AccountForm(
            state = state,
            viewModel = viewModel,
            keypad = keypad,
            onAmountTapped = {
                // Clearing focus is what lowers the system keyboard; without it the two
                // would be stacked on each other.
                focusManager.clearFocus()
                keypad.showForAmount()
            },
            modifier = Modifier.weight(1f),
        )

        if (keypad.isShown) {
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
    }

    RemovalDialogs(state = removal, removal = viewModel.removal)
}

@Composable
private fun AccountForm(
    state: AccountEditUiState,
    viewModel: AccountEditViewModel,
    keypad: AmountKeypadState,
    onAmountTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
    ) {
        item(key = "opening") {
            OpeningBalanceHeader(
                state = state,
                onToggleSign = viewModel::toggleSign,
                onAmountTapped = onAmountTapped,
            )
        }

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
                    modifier =
                        Modifier
                            .testTag(NAME_FIELD_TAG)
                            .onFocusChanged { keypad.onTextFieldFocus(it.isFocused) },
                )
            }
        }

        typeSection(selected = state.type, onSelect = viewModel::setType)
        currencySection(currency = state.currency)
        colorSection(selected = state.colorArgb, onSelect = viewModel::setColor)

        if (!state.isNew) {
            accountActions(
                state = state,
                onArchive = viewModel::archive,
                onRestore = viewModel::restore,
                onRemove = viewModel::requestRemoval,
            )
        }
    }
}

/** The amount the account held before Cents knew about it — and its sign. */
@Composable
private fun OpeningBalanceHeader(
    state: AccountEditUiState,
    onToggleSign: () -> Unit,
    onAmountTapped: () -> Unit,
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
            // The balance is how you ask for the keypad back once the name has taken it away.
            modifier =
                Modifier
                    .testTag(OPENING_BALANCE_TAG)
                    .clickable(onClick = onAmountTapped)
                    .padding(CentsDimens.spacingXs),
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
 * Shown, not chosen. Every account is in the one currency the app works in, taken from the
 * device (`DECISIONS.md` D2).
 *
 * There used to be a picker here. It was the only way to reach a state the app has decided
 * it will never resolve: with two currencies in play, the accounts list can show no total
 * at all, movements cannot move between accounts, and no conversion is coming, because
 * conversion needs a rate and an "approximately" that does not belong in a ledger. Offering
 * the choice was offering a trapdoor.
 */
private fun LazyListScope.currencySection(currency: String) {
    item(key = "currency") {
        CentsListSection(footer = stringResource(R.string.account_currency_footer)) {
            CentsListRow(
                title = stringResource(R.string.account_currency_header),
                value = currency,
                showSeparator = false,
            )
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

/**
 * The two different intentions, side by side rather than one hidden inside the other.
 *
 * Archive means keep everything and stop offering the account. Delete means the account
 * goes — and if it holds movements, [RemovalDialogs] asks what should become of them.
 * Delete is offered even for an archived account, because it is now genuinely able to
 * finish the job rather than archive something twice.
 */
private fun LazyListScope.accountActions(
    state: AccountEditUiState,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
) {
    item(key = "actions") {
        CentsListSection(footer = stringResource(R.string.account_delete_footer)) {
            CentsActionRow(
                label =
                    stringResource(
                        if (state.archived) R.string.account_restore else R.string.account_archive,
                    ),
                onClick = if (state.archived) onRestore else onArchive,
            )
            CentsActionRow(
                label = stringResource(R.string.account_delete),
                onClick = onRemove,
                destructive = true,
            )
        }
    }
}

private const val SELECTED_MARK = "✓"
internal const val NAME_FIELD_TAG = "accountName"
internal const val SIGN_TOGGLE_TAG = "accountSign"
internal const val OPENING_BALANCE_TAG = "accountOpeningBalance"
