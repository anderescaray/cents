package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import java.time.LocalDate

/** The rows the Reports tab is made of: a range bound, a slice, a legend entry. */
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
internal fun CategoryRow(
    slice: CategorySpending,
    last: Boolean,
    onClick: () -> Unit,
) {
    CentsListRow(
        title = slice.name ?: stringResource(R.string.reports_uncategorized),
        subtitle = stringResource(R.string.reports_share, (slice.share * PERCENT).toInt()),
        value = MoneyFormatter.format(slice.total),
        leading = { Swatch(slice.colorArgb?.let { Color(it) } ?: CentsTheme.colors.tertiaryLabel) },
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

@Composable
internal fun Swatch(color: Color) {
    Box(
        Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(color),
    )
}

/** Adds amounts that already share a currency; null when there is nothing to add. */
internal fun List<Money>.total(): Money? = reduceOrNull { acc, money -> acc + money }

private val SWATCH_SIZE = 10.dp
private const val PERCENT = 100

internal val PeriodPreset.labelRes: Int
    get() =
        when (this) {
            PeriodPreset.WEEK -> R.string.reports_period_week
            PeriodPreset.MONTH -> R.string.reports_period_month
            PeriodPreset.QUARTER -> R.string.reports_period_quarter
            PeriodPreset.YEAR -> R.string.reports_period_year
            PeriodPreset.CUSTOM -> R.string.reports_period_custom
        }
