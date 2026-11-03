package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.BalanceOverTime
import io.github.anderescaray.cents.domain.model.BalancePoint
import io.github.anderescaray.cents.domain.model.BalanceSpan
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.repository.reports.BalanceOverTimeRepository
import io.github.anderescaray.cents.ui.components.AnimatedMoney
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.components.headlineMoney
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.feature.reports.EmptyChart
import io.github.anderescaray.cents.ui.feature.reports.ReportActions
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionSource
import io.github.anderescaray.cents.ui.feature.reports.ReportSectionState
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.formatLocale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Am I trending up or down: the balance, day by day, over the period.
 *
 * The third view on the tab, and the first one built after the seam existed — so it is the
 * worked example of what a view is. Its model is `domain/model/BalanceOverTime.kt`, its query
 * is `TransactionDao.observeDailyNet` plus `observeBalanceBefore`, its repository is
 * `RoomBalanceOverTimeRepository`, and everything on screen is in this file.
 *
 * **The line starts from the money carried into the period, not from zero**, and runs to today.
 * The whole argument is on [BalanceOverTime]; drawing it is [BalanceLine], which reads by touch.
 *
 * The three rows under the chart are not decoration. A line drawn against an axis that does
 * not start at zero cannot be read for its level, only for its shape, so the level is stated
 * in words: what there was, what there is, and the difference.
 */
data class BalanceOverTimeSection(
    val balance: BalanceOverTime,
    /** How far back this card reaches, which only this card changes. */
    val span: BalanceSpan = BalanceSpan.PERIOD,
    /** A stable instance from the source, so two states still compare equal. */
    val onSpan: (BalanceSpan) -> Unit = {},
) : ReportSectionState {
    override val key: String get() = KEY

    @Composable
    override fun Content(actions: ReportActions) {
        val today = remember { LocalDate.now() }
        // From what there was before the period, so a single expense on its first day is a step
        // down rather than a flat line, and no further than today: the days still to come have
        // nothing in them, and drawing them flat said the money had stopped moving.
        val line =
            remember(balance, today) {
                listOf(BalancePoint(balance.period.from.minusDays(1), balance.opening)) +
                    balance.points.filter { it.date <= today }
            }
        var scrubbed by remember(line) { mutableStateOf<Int?>(null) }
        val reading = scrubbed?.let(line::getOrNull)

        CentsListSection(
            header = stringResource(R.string.reports_balance_header),
            // Said, because over every account the brokers are left out, and a line that quietly
            // omits money is the kind of number that gets believed and should not be.
            footer = stringResource(R.string.reports_balance_scope).takeIf { !balance.oneAccount },
        ) {
            // The answer first: how much the money moved, in its colour, with how far back to look
            // beside it, small, so it costs the card no row of its own. While a finger is on the
            // line, the day under it instead: what there was then, and how far from the start.
            BalanceHeadline(balance = balance, reading = reading, span = span, onSpan = onSpan)
            if (balance.isEmpty) {
                // A flat line at the opening balance would be true and would say nothing.
                EmptyChart(stringResource(R.string.reports_balance_empty))
            } else {
                BalanceLine(
                    points = line,
                    above = CentsTheme.colors.positive,
                    below = CentsTheme.colors.destructive,
                    scrubbed = scrubbed,
                    onScrub = { scrubbed = it },
                    description =
                        stringResource(
                            R.string.reports_balance_line_description,
                            MoneyFormatter.format(balance.opening),
                            MoneyFormatter.format(line.last().balance),
                        ),
                    modifier = Modifier.padding(horizontal = CentsDimens.rowHorizontalPadding),
                )
                Spacer(Modifier.height(CentsDimens.spacingM))
                // A line with no axis cannot be read for its level, only for its shape, so the level
                // is stated in words under it.
                StatRow {
                    StatCell(label = stringResource(R.string.reports_balance_opening), amount = balance.opening)
                    StatCell(label = stringResource(R.string.reports_balance_closing), amount = balance.closing)
                }
                Spacer(Modifier.height(CentsDimens.spacingL))
            }
        }
    }

    companion object {
        const val KEY = "balance"
        const val SPAN_TAG = "balance-span"
    }
}

/**
 * The three reaches as one small track of pills: the tab's period, the year up to its end, all of it. Small on
 * purpose: it is a second look at the same line, not a control the card is about.
 */
@Composable
private fun SpanPicker(
    selected: BalanceSpan,
    onSelect: (BalanceSpan) -> Unit,
) {
    val labels =
        mapOf(
            BalanceSpan.PERIOD to stringResource(R.string.reports_balance_span_period),
            BalanceSpan.YEAR to stringResource(R.string.reports_balance_span_year),
            BalanceSpan.ALL to stringResource(R.string.reports_balance_span_all),
        )
    Row(
        modifier =
            Modifier
                .testTag(BalanceOverTimeSection.SPAN_TAG)
                .clip(CircleShape)
                .background(CentsTheme.colors.fill)
                .padding(SPAN_TRACK.dp),
    ) {
        BalanceSpan.entries.forEach { span ->
            val isSelected = span == selected
            Text(
                text = labels.getValue(span),
                style = CentsTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
                color = if (isSelected) CentsTheme.colors.label else CentsTheme.colors.secondaryLabel,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) CentsTheme.colors.groupedCard else Color.Transparent)
                        .semantics { this.selected = isSelected }
                        .tappable(onClick = { onSelect(span) })
                        .padding(horizontal = SPAN_PAD_H.dp, vertical = SPAN_PAD_V.dp),
            )
        }
    }
}

private const val SPAN_TRACK = 2
private const val SPAN_PAD_H = 10
private const val SPAN_PAD_V = 4

@Singleton
class BalanceOverTimeSectionSource
    @Inject
    constructor(
        private val repository: BalanceOverTimeRepository,
    ) : ReportSectionSource {
        /** In memory, like the flow chart's shape: a way of looking, reset by a restart. */
        private val span = MutableStateFlow(BalanceSpan.PERIOD)

        private val onSpan: (BalanceSpan) -> Unit = { span.value = it }

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observe(scope: ReportScope): Flow<ReportSectionState> =
            span.flatMapLatest { chosen ->
                reach(scope, chosen)
                    .flatMapLatest { period -> repository.observeBalanceOverTime(scope.copy(period = period)) }
                    .map { BalanceOverTimeSection(it, chosen, onSpan) }
            }

        /**
         * Where the line runs for a span, always ending where the tab's period ends, so stepping back
         * a month moves the year and all of time with it.
         */
        private fun reach(
            scope: ReportScope,
            span: BalanceSpan,
        ): Flow<ReportPeriod> {
            val end = scope.period.to
            return when (span) {
                BalanceSpan.PERIOD -> {
                    flowOf(scope.period)
                }

                BalanceSpan.YEAR -> {
                    flowOf(ReportPeriod.Custom(end.minusYears(1).plusDays(1), end))
                }

                BalanceSpan.ALL -> {
                    repository.observeFirstDate(scope.accountId).map { first ->
                        ReportPeriod.Custom(minOf(first ?: scope.period.from, scope.period.from), end)
                    }
                }
            }
        }
    }

/**
 * The change over the line, or the day a finger is on, with how far back to look beside it: small,
 * so it costs the card no row of its own.
 */
@Composable
private fun BalanceHeadline(
    balance: BalanceOverTime,
    reading: BalancePoint?,
    span: BalanceSpan,
    onSpan: (BalanceSpan) -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = CentsDimens.rowHorizontalPadding, vertical = CentsDimens.spacingM),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            if (reading != null) {
                ScrubHeadline(point = reading, opening = balance.opening)
            } else {
                Text(
                    text = stringResource(R.string.reports_balance_change),
                    style = CentsTheme.typography.footnote,
                    color = CentsTheme.colors.secondaryLabel,
                )
                if (!balance.isEmpty) {
                    AnimatedMoney(
                        money = balance.change,
                        style = CentsTheme.typography.title1,
                        color = toneOf(balance.change),
                        withSign = true,
                    )
                }
            }
        }
        SpanPicker(selected = span, onSelect = onSpan)
    }
}

/** A day read off the line: its date, what there was, and how far that is from the start. */
@Composable
private fun ScrubHeadline(
    point: BalancePoint,
    opening: Money,
) {
    val formatter = remember { DateTimeFormatter.ofPattern(SCRUB_DATE_PATTERN, formatLocale()) }
    val fromStart = point.balance - opening
    Text(
        text = point.date.format(formatter),
        style = CentsTheme.typography.footnote,
        color = CentsTheme.colors.secondaryLabel,
    )
    val text = MoneyFormatter.format(point.balance)
    Text(
        text = headlineMoney(text, CentsTheme.typography.title1.fontSize, CentsTheme.colors.label),
        style = CentsTheme.typography.title1,
        color = CentsTheme.colors.label,
        maxLines = 1,
    )
    Text(
        text = MoneyFormatter.formatSigned(fromStart),
        style = CentsTheme.typography.footnote,
        color = toneOf(fromStart),
    )
}

/** Green for more, red for less, ink for no change: the colour of a change in money. */
@Composable
private fun toneOf(change: Money): Color =
    when {
        change.isZero -> CentsTheme.colors.label
        change.isNegative -> CentsTheme.colors.destructiveText
        else -> CentsTheme.colors.positiveText
    }

/** "3 Nov 2026": the day a finger is on. */
private const val SCRUB_DATE_PATTERN = "d MMM yyyy"
