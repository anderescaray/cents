package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Position
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.CentsSegmentedControl
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.AmountEntry
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.relativeLabel
import java.time.ZoneId

// What a holding is worth, and the two ways the owner can say it.
//
// A file of its own rather than more of `HoldingDetailScreen.kt`, because until prices come from
// a provider this *is* the feature: every value, profit and total on that screen is derived from
// the number typed here, so the reasoning about how it is typed belongs next to it.

internal fun LazyListScope.priceSection(
    position: Position,
    price: PriceEntryState,
    viewModel: HoldingDetailViewModel,
) {
    item(key = "price") {
        CentsListSection(
            header = stringResource(R.string.holding_price_header),
            footer = stringResource(R.string.holding_price_manual_footer),
        ) {
            if (price.editing) {
                PriceEditor(price = price, viewModel = viewModel)
            } else {
                PriceReadout(position = position, onEdit = viewModel::startEditingPrice)
            }
        }
    }
}

/**
 * Which number is being typed: what one unit is worth, or what the whole lot is worth.
 *
 * Both store the same thing — one price per unit — so this is not a second kind of price, it is
 * the same question asked the way the owner happens to be reading it. A broker's app shows both,
 * and being made to divide one by the other before typing is how correcting a number turns into
 * arithmetic homework.
 */
@Composable
private fun PriceBasisControl(
    selected: PriceBasis,
    onSelect: (PriceBasis) -> Unit,
) {
    val labels =
        mapOf(
            PriceBasis.PER_UNIT to stringResource(R.string.price_basis_per_unit),
            PriceBasis.TOTAL to stringResource(R.string.price_basis_total),
        )
    CentsSegmentedControl(
        options = PriceBasis.entries,
        selected = selected,
        onSelect = onSelect,
        label = { basis -> labels.getValue(basis) },
        modifier = Modifier.padding(horizontal = CentsDimens.rowHorizontalPadding, vertical = CentsDimens.spacingS),
    )
}

/**
 * The price being typed, in whichever of the two ways the owner is reading it.
 */
@Composable
private fun PriceEditor(
    price: PriceEntryState,
    viewModel: HoldingDetailViewModel,
) {
    PriceBasisControl(selected = price.basis, onSelect = viewModel::onPriceBasisChanged)
    CentsTextField(
        value = AmountEntry.toTypedText(price.input),
        onValueChange = viewModel::onPriceTyped,
        placeholder =
            stringResource(
                if (price.basis == PriceBasis.TOTAL) {
                    R.string.price_field_total
                } else {
                    R.string.price_dialog_placeholder
                },
            ),
        numeric = true,
        showSeparator = true,
    )
    CentsListRow(
        title = stringResource(R.string.price_dialog_date),
        value = price.date.relativeLabel(viewModel.today()),
        onClick = viewModel::openPriceDatePicker,
    )
    CentsListRow(
        title = stringResource(R.string.action_save),
        onClick = viewModel::savePrice,
        showSeparator = true,
    )
    CentsListRow(
        title = stringResource(R.string.action_cancel),
        onClick = viewModel::cancelEditingPrice,
        showSeparator = false,
    )
}

/** The price as it stands, with its age — or the absence of one, said in words. */
@Composable
private fun PriceReadout(
    position: Position,
    onEdit: () -> Unit,
) {
    // The *usable* quote: one stored in another currency values nothing, so showing it as the
    // last price would explain none of what the rows above say.
    val quote = position.usableQuote
    CentsListRow(
        title = stringResource(R.string.holding_price_row),
        subtitle =
            quote?.let {
                stringResource(
                    R.string.holding_price_asof,
                    it.asOf
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .relativeLabel(),
                )
            },
        value = quote?.let { MoneyFormatter.format(it.price) } ?: stringResource(R.string.investments_no_price),
        valueColor = if (quote == null) CentsTheme.colors.secondaryLabel else CentsTheme.colors.label,
        showSeparator = true,
    )
    CentsListRow(
        title = stringResource(R.string.holding_price_set),
        onClick = onEdit,
        showSeparator = false,
    )
}
