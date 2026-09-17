package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsDatePicker
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsModalBar
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.ZoneId

/**
 * Every price, on one screen.
 *
 * The portfolio has priced holdings one at a time since #44, which is right when one number has
 * moved and tedious when the owner has their broker open and is reading all of them. No free
 * price provider carries the listings this portfolio actually holds (`DECISIONS.md` D20), so
 * typing is not a fallback — it is how prices get in, and it is worth being quick.
 *
 * One date governs the lot, because they are read in one sitting. A row left empty is left
 * alone: updating three out of eight without disturbing the rest is most of the point.
 */
@Composable
fun PriceUpdateScreen(
    onDone: () -> Unit,
    viewModel: PriceUpdateViewModel = hiltViewModel(),
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
                // Every row here is a text field with a keyboard under it.
                .imePadding(),
    ) {
        CentsModalBar(
            title = stringResource(R.string.prices_title),
            cancelLabel = stringResource(R.string.action_cancel),
            onCancel = onDone,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = viewModel::save,
            confirmEnabled = state.canSave,
        )

        PriceList(state = state, viewModel = viewModel)
    }

    if (state.pickingDate) {
        CentsDatePicker(
            selected = state.date,
            today = state.today,
            onSelect = viewModel::setDate,
            onDismiss = viewModel::dismissDatePicker,
        )
    }
}

/** The date the lot were true, and a field for each holding. */
@Composable
private fun PriceList(
    state: PriceUpdateUiState,
    viewModel: PriceUpdateViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
    ) {
        item(key = "date") {
            CentsListSection(footer = stringResource(R.string.prices_date_footer)) {
                CentsListRow(
                    title = stringResource(R.string.prices_as_of),
                    value = state.date.relativeLabel(state.today),
                    showChevron = true,
                    showSeparator = false,
                    onClick = viewModel::openDatePicker,
                )
            }
        }

        if (state.isEmpty) {
            item(key = "empty") {
                CentsListSection(footer = stringResource(R.string.prices_empty_footer)) {
                    CentsListRow(
                        title = stringResource(R.string.prices_empty_title),
                        showSeparator = false,
                    )
                }
            }
        }

        if (state.rows.isNotEmpty()) {
            item(key = "prices") {
                CentsListSection(
                    header = stringResource(R.string.prices_header),
                    footer =
                        state.changedCount
                            .takeIf { it > 0 }
                            ?.let { pluralStringResource(R.plurals.prices_changed, it, it) },
                ) {
                    state.rows.forEachIndexed { index, row ->
                        PriceField(
                            row = row,
                            showSeparator = index != state.rows.lastIndex,
                            onTyped = { typed -> viewModel.onTyped(row.symbol, typed) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One holding's row: what it is, what the app believes it is worth and when, and somewhere to
 * type what it is worth now.
 *
 * The **placeholder is the price already stored**, so an untouched row still shows the number it
 * is keeping rather than an empty box that reads as missing. What is old is said in the subtitle
 * rather than coloured, because a price being four days old is information and not a fault.
 */
@Composable
private fun PriceField(
    row: PriceRow,
    showSeparator: Boolean,
    onTyped: (String) -> Unit,
) {
    val current = row.current
    CentsListRow(
        title = row.name,
        subtitle =
            buildString {
                append(row.symbol)
                val asOf = row.asOf
                if (current != null && asOf != null) {
                    append(" · ")
                    append(
                        stringResource(
                            R.string.prices_current,
                            MoneyFormatter.format(current),
                            asOf.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                        ),
                    )
                } else {
                    append(" · ")
                    append(stringResource(R.string.investments_no_price))
                }
            },
        trailing = {
            CentsTextField(
                value = row.input,
                onValueChange = onTyped,
                placeholder =
                    current?.let { MoneyFormatter.format(it) }
                        ?: stringResource(R.string.prices_placeholder),
                numeric = true,
                modifier = Modifier.testTag(priceFieldTag(row.symbol)),
            )
        },
        showSeparator = showSeparator,
    )
}

internal fun priceFieldTag(symbol: String) = "price-$symbol"
