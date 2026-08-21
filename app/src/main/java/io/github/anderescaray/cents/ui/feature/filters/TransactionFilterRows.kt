package io.github.anderescaray.cents.ui.feature.filters

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsTextField
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import java.time.LocalDate

// The rows the filter form is built from, and the labels they say things with.

/** A row that is one of a set: iOS marks the chosen one with a tick, not a radio button. */
@Composable
internal fun SelectableRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    CentsListRow(
        title = title,
        value = if (selected) SELECTED_MARK else null,
        valueColor = CentsTheme.colors.accent,
        showSeparator = !last,
        onClick = onClick,
    )
}

@Composable
internal fun BoundRow(
    labelRes: Int,
    date: LocalDate?,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = stringResource(labelRes),
        value = date?.toString() ?: stringResource(R.string.filters_date_pick),
        showChevron = true,
        showSeparator = !last,
        onClick = onClick,
    )
}

@Composable
internal fun AmountField(
    digits: String,
    placeholder: String,
    onChange: (String) -> Unit,
    last: Boolean = false,
) {
    CentsTextField(
        value = digits,
        onValueChange = onChange,
        placeholder = placeholder,
        numeric = true,
        showSeparator = !last,
    )
}

/** An archived row still classifies history, so it is offered — and said to be archived. */
@Composable
internal fun Account.displayName(): String = if (archived) stringResource(R.string.filters_archived, name) else name

@Composable
internal fun Category.displayName(): String = if (archived) stringResource(R.string.filters_archived, name) else name

/**
 * "From 10,00 € to 50,00 €", or whichever half of it was actually given.
 *
 * The currency is passed in rather than read from the locale. Reading it here would name the
 * device's currency for movements that may be stored in another, and
 * `Currency.getInstance(Locale)` throws outright on a language-only locale — which Android
 * allows, and which would take the whole screen down on recomposition.
 */
@Composable
internal fun TransactionFilter.amountRangeLabel(currency: String): String? {
    val min = minAmountMinor?.let { MoneyFormatter.format(Money(it, currency)) }
    val max = maxAmountMinor?.let { MoneyFormatter.format(Money(it, currency)) }
    return when {
        min != null && max != null -> stringResource(R.string.filters_amount_between, min, max)
        min != null -> stringResource(R.string.filters_amount_from, min)
        max != null -> stringResource(R.string.filters_amount_up_to, max)
        else -> null
    }
}

internal const val SELECTED_MARK = "✓"
