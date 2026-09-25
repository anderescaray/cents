package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import java.time.LocalDate

/**
 * The rows every section on the Reports tab is built from: a range bound, a legend entry, a
 * swatch, and what a chart says when it has nothing to draw.
 *
 * Shared on purpose, and shared *only* here. Anything a single view needs — its own kind of
 * row, its own chart — belongs in that view's file in `section/`, so the next view is a new
 * file rather than an edit to this one.
 */
@Composable
internal fun RangeRow(
    labelRes: Int,
    date: LocalDate,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = stringResource(labelRes),
        value = date.toString(),
        showChevron = true,
        showSeparator = !last,
        onClick = onClick,
    )
}

@Composable
internal fun LegendRow(
    color: Color,
    label: String,
    amount: Money?,
    last: Boolean = false,
) {
    CentsListRow(
        title = label,
        value = amount?.let { MoneyFormatter.format(it) },
        leading = { Swatch(color) },
        showSeparator = !last,
    )
}

/** A row that names one number, tinted when the sign of that number is the point of it. */
@Composable
internal fun AmountRow(
    label: String,
    amount: Money,
    valueColor: Color? = null,
    last: Boolean = false,
) {
    CentsListRow(
        title = label,
        value = MoneyFormatter.format(amount),
        valueColor = valueColor,
        showSeparator = !last,
    )
}

@Composable
internal fun Swatch(color: Color) {
    Box(
        Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(color),
    )
}

/** What a chart says when the period it was given holds nothing. */
@Composable
internal fun EmptyChart(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = EMPTY_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = CentsTheme.typography.subhead,
            color = CentsTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = CentsDimens.spacingL),
        )
    }
}

/** Adds amounts that already share a currency; null when there is nothing to add. */
internal fun List<Money>.total(): Money? = reduceOrNull { acc, money -> acc + money }

private val SWATCH_SIZE = 10.dp
private val EMPTY_HEIGHT = 120.dp

internal val PeriodPreset.labelRes: Int
    get() =
        when (this) {
            PeriodPreset.WEEK -> R.string.reports_period_week
            PeriodPreset.MONTH -> R.string.reports_period_month
            PeriodPreset.QUARTER -> R.string.reports_period_quarter
            PeriodPreset.YEAR -> R.string.reports_period_year
            PeriodPreset.CUSTOM -> R.string.reports_period_custom
        }
