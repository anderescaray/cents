package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The iOS calendar picker: a month grid with the chosen day in a filled circle, and the
 * month stepped with chevrons — not Material's dialog with its own header and buttons.
 *
 * The week starts on whatever day the device locale says it does, so a Spanish phone shows
 * Monday first and a US one Sunday, without the app having an opinion about it.
 */
@Composable
fun CentsDatePicker(
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    locale: Locale = Locale.getDefault(),
) {
    var month by remember(selected) { mutableStateOf(YearMonth.from(selected)) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(CALENDAR_RADIUS.dp))
                    .background(CentsTheme.colors.groupedCard)
                    .padding(CentsDimens.spacingL),
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingM),
        ) {
            MonthHeader(
                month = month,
                locale = locale,
                onPrevious = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
            )
            WeekdayHeader(locale = locale)
            MonthGrid(
                month = month,
                selected = selected,
                today = today,
                locale = locale,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    locale: Locale,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text =
                month
                    .format(DateTimeFormatter.ofPattern(MONTH_PATTERN, locale))
                    .replaceFirstChar { it.titlecase(locale) },
            style = CentsTheme.typography.headline,
            color = CentsTheme.colors.label,
            modifier = Modifier.weight(1f),
        )
        StepButton(
            iconRes = R.drawable.ic_chevron_left,
            description = stringResource(R.string.date_previous_month),
            onClick = onPrevious,
        )
        StepButton(
            iconRes = R.drawable.ic_chevron_right,
            description = stringResource(R.string.date_next_month),
            onClick = onNext,
        )
    }
}

@Composable
private fun StepButton(
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(CentsDimens.minRowHeight)
                .clip(CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = CentsTheme.colors.accent,
            modifier = Modifier.size(CHEVRON_SIZE.dp),
        )
    }
}

@Composable
private fun WeekdayHeader(locale: Locale) {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdays(locale).forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                style = CentsTheme.typography.caption1,
                color = CentsTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingXs)) {
        monthCells(month, locale).chunked(DAYS_PER_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        selected = date == selected,
                        isToday = date == today,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    selected: Boolean,
    isToday: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        if (date == null) return@Box
        Box(
            modifier =
                Modifier
                    .size(DAY_SIZE.dp)
                    .clip(CircleShape)
                    .background(if (selected) CentsTheme.colors.accent else CentsTheme.colors.groupedCard)
                    .clickable { onSelect(date) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = CentsTheme.typography.subhead,
                fontWeight = if (selected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    when {
                        selected -> CentsTheme.colors.onAccent
                        isToday -> CentsTheme.colors.accent
                        else -> CentsTheme.colors.label
                    },
            )
        }
    }
}

/** The seven weekday headings, starting on the locale's first day of the week. */
internal fun weekdays(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return List(DAYS_PER_WEEK) { first.plus(it.toLong()) }
}

/**
 * The cells of a month grid: the days of [month], preceded by blanks so the first one
 * falls under its weekday, and followed by blanks to complete the last week.
 */
internal fun monthCells(
    month: YearMonth,
    locale: Locale,
): List<LocalDate?> {
    val first = WeekFields.of(locale).firstDayOfWeek
    val lead = (month.atDay(1).dayOfWeek.value - first.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
    val days = List(month.lengthOfMonth()) { month.atDay(it + 1) }
    val cells: List<LocalDate?> = List(lead) { null } + days
    val trail = (DAYS_PER_WEEK - cells.size % DAYS_PER_WEEK) % DAYS_PER_WEEK
    return cells + List(trail) { null }
}

private const val DAYS_PER_WEEK = 7
private const val CALENDAR_RADIUS = 14
private const val CHEVRON_SIZE = 18
private const val DAY_SIZE = 36
private const val MONTH_PATTERN = "LLLL yyyy"
