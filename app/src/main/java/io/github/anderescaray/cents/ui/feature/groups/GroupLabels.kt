package io.github.anderescaray.cents.ui.feature.groups

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.GroupSummary
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * What a group came to, in words: *Cost you 840 €* or *Earned you 1 150 €* (D27).
 *
 * The sign is the answer, so it is turned into the verb rather than shown as a minus in front of
 * a number — "−840 €" next to a trip reads as a debt, not as what the trip cost.
 */
@Composable
internal fun GroupSummary.netSentence(): String =
    when {
        net.isNegative -> stringResource(R.string.group_cost, MoneyFormatter.format(-net))
        net.isZero && isEmpty -> stringResource(R.string.group_row_empty)
        net.isZero -> stringResource(R.string.group_even)
        else -> stringResource(R.string.group_earned, MoneyFormatter.format(net))
    }

/** Red for what cost, green for what earned, and plain for nothing either way. */
@Composable
internal fun GroupSummary.netColor(): Color =
    when {
        net.isNegative -> CentsTheme.colors.destructiveText
        net.isZero -> CentsTheme.colors.secondaryLabel
        else -> CentsTheme.colors.positiveText
    }

/** "2 Oct 2026 – 6 Oct 2026", or one date when everything happened on one day. */
@Composable
internal fun spanLabel(
    first: LocalDate,
    last: LocalDate,
): String =
    if (first == last) {
        first.medium()
    } else {
        stringResource(R.string.group_span, first.medium(), last.medium())
    }

/** The line under a group's name on the list: when, and how many movements. */
@Composable
internal fun GroupSummary.subtitle(): String {
    val first = firstDate
    val last = lastDate
    if (isEmpty || first == null || last == null) return stringResource(R.string.group_row_empty)
    return stringResource(
        R.string.group_row_span,
        spanLabel(first, last),
        pluralStringResource(R.plurals.group_row_movements, movementCount, movementCount),
    )
}

internal fun LocalDate.medium(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
