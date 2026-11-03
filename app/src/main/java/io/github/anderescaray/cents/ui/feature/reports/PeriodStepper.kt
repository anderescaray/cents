package io.github.anderescaray.cents.ui.feature.reports

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.ui.components.EaseOutExpo
import io.github.anderescaray.cents.ui.components.RoundIconButton
import io.github.anderescaray.cents.ui.components.tappable
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.formatLocale
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The period on screen by name, with an arrow either side: back one month (or week, quarter,
 * year) at a time, and forward again as far as the one holding today. Under the name, *This
 * month* while it is the current one, and a way back to it once it is not.
 */
@Composable
internal fun PeriodStepper(
    state: ReportsUiState,
    onStep: (Int) -> Unit,
    onStepToNow: () -> Unit,
) {
    val locale = formatLocale()
    val title = remember(state.period, locale) { state.period.title(locale) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CentsDimens.screenMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            iconRes = R.drawable.ic_chevron_left,
            contentDescription = stringResource(R.string.reports_step_back),
            onClick = { onStep(-1) },
            modifier = Modifier.testTag(STEP_BACK_TAG),
        )
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            // The name slides in from the side the owner went: back in time from the left.
            AnimatedContent(
                targetState = StepperTitle(state.offset, title),
                transitionSpec = {
                    val from = if (targetState.offset < initialState.offset) -1 else 1
                    (
                        slideInHorizontally(tween(STEP_MS, easing = EaseOutExpo)) { it / SLIDE_SHARE * from } +
                            fadeIn(tween(STEP_MS))
                    ).togetherWith(fadeOut(tween(STEP_MS / 2)))
                },
                label = "periodTitle",
            ) { shown ->
                Text(
                    text = shown.title,
                    style = CentsTheme.typography.title3,
                    color = CentsTheme.colors.label,
                    maxLines = 1,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (state.offset == 0) {
                Text(
                    text = stringResource(state.preset.currentRes()),
                    style = CentsTheme.typography.footnote,
                    color = CentsTheme.colors.secondaryLabel,
                )
            } else {
                Text(
                    text = stringResource(R.string.reports_step_now),
                    style = CentsTheme.typography.footnote,
                    color = CentsTheme.colors.accentText,
                    modifier = Modifier.tappable(onClick = onStepToNow).testTag(STEP_NOW_TAG),
                )
            }
        }
        RoundIconButton(
            iconRes = R.drawable.ic_chevron_right,
            contentDescription = stringResource(R.string.reports_step_forward),
            onClick = { onStep(1) },
            enabled = state.canStepForward,
            modifier = Modifier.testTag(STEP_FORWARD_TAG),
        )
    }
}

/** What the stepper's title animates between: where it is, and what that is called. */
private data class StepperTitle(
    val offset: Int,
    val title: String,
)

/** "This month", "This week": what the period is while it is the one holding today. */
private fun PeriodPreset.currentRes(): Int =
    when (this) {
        PeriodPreset.WEEK -> R.string.reports_period_this_week
        PeriodPreset.QUARTER -> R.string.reports_period_this_quarter
        PeriodPreset.YEAR -> R.string.reports_period_this_year
        PeriodPreset.MONTH, PeriodPreset.CUSTOM -> R.string.reports_period_this_month
    }

/**
 * A period's name: "October 2026", "Q4 2026", "2026", or both ends of a week or a range. In the
 * phone's language, with a capital first letter where the language writes months in lower case.
 */
internal fun ReportPeriod.title(locale: Locale): String {
    val text =
        when (this) {
            is ReportPeriod.Month -> {
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            }

            is ReportPeriod.Quarter -> {
                from.format(DateTimeFormatter.ofPattern("QQQ yyyy", locale))
            }

            is ReportPeriod.Year -> {
                year.toString()
            }

            is ReportPeriod.Week, is ReportPeriod.Custom -> {
                val day = DateTimeFormatter.ofPattern("d MMM", locale)
                "${from.format(day)} – ${to.format(day)}"
            }
        }
    return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

private const val STEP_MS = 260
private const val SLIDE_SHARE = 3
internal const val STEP_BACK_TAG = "reportsStepBack"
internal const val STEP_FORWARD_TAG = "reportsStepForward"
internal const val STEP_NOW_TAG = "reportsStepNow"
