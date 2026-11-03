package io.github.anderescaray.cents.ui.feature.reports.section

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/*
 * What the Reports charts share: they fit the card's width instead of scrolling sideways, label
 * their axis in short whole amounts, and say their figures in small stat cells above or below.
 */

/** A chart that is never scrolled sideways: every point is in the card, all at once. */
@Composable
internal fun rememberFixedScroll(): VicoScrollState = rememberVicoScrollState(scrollEnabled = false)

/** Zoomed to fit its content into the width it is given, and not zoomable by a pinch. */
@Composable
internal fun rememberFitZoom(): VicoZoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content)

/**
 * An axis value, in minor units, as a short whole amount: 850, 1.2k, 12k, 1.5M. Minor units on an
 * axis would read as a hundred times the money, and a full figure would crowd the chart.
 */
internal fun axisAmount(
    minor: Double,
    locale: Locale,
): String {
    val units = minor / MINOR_UNITS_PER_UNIT
    val size = abs(units)
    val oneDecimal = DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale))
    return when {
        size >= MILLION -> oneDecimal.format(units / MILLION) + "M"
        size >= THOUSAND -> oneDecimal.format(units / THOUSAND) + "k"
        else -> units.toLong().toString()
    }
}

/** A row of [StatCell]s with equal room each. */
@Composable
internal fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CentsDimens.rowHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingS),
        content = content,
    )
}

/** One figure with its name over it, on a grey panel: the way a card states its answer. */
@Composable
internal fun RowScope.StatCell(
    label: String,
    amount: Money,
    modifier: Modifier = Modifier,
    color: Color = CentsTheme.colors.label,
    dot: Color? = null,
) {
    Column(
        modifier =
            modifier
                .weight(1f)
                .clip(RoundedCornerShape(STAT_RADIUS.dp))
                .background(CentsTheme.colors.fill)
                .padding(horizontal = CentsDimens.spacingM, vertical = CentsDimens.spacingS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(DOT.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(CentsDimens.spacingXs + 2.dp))
            }
            Text(
                text = label,
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
                maxLines = 1,
            )
        }
        Text(
            text = MoneyFormatter.format(amount),
            style = CentsTheme.typography.headline,
            color = color,
            maxLines = 1,
        )
    }
}

/** Small coloured dots with their names, in one line under a chart. */
@Composable
internal fun ChartLegend(entries: List<Pair<Color, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CentsDimens.rowHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(DOT.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(CentsDimens.spacingXs + 2.dp))
                Text(text = label, style = CentsTheme.typography.footnote, color = CentsTheme.colors.secondaryLabel)
            }
        }
    }
}

private const val MINOR_UNITS_PER_UNIT = 100.0
private const val THOUSAND = 1_000.0
private const val MILLION = 1_000_000.0
private const val STAT_RADIUS = 14
private const val DOT = 8
