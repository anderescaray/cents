package io.github.anderescaray.cents.ui.feature.investments

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.ChartPoint
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.util.MoneyFormatter

/** One line of a summary: what it is, and how much. */
data class SummaryLine(
    val label: String,
    val amount: Money,
    /** A gain, written with its sign and coloured; [ratio] beside it when there is one. */
    val isProfit: Boolean = false,
    val ratio: Double? = null,
    val tag: String? = null,
)

/** The figures at the top of each investment screen, one row each. */
@Composable
fun SummarySection(
    lines: List<SummaryLine>,
    footer: String? = null,
) {
    CentsListSection(footer = footer) {
        lines.forEachIndexed { index, line ->
            CentsListRow(
                title = line.label,
                value = if (line.isProfit) profitText(line.amount, line.ratio) else MoneyFormatter.format(line.amount),
                valueColor = if (line.isProfit) profitColor(line.amount) else null,
                showSeparator = index < lines.lastIndex,
                modifier = line.tag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}

/** The chart as a list item, keyed so it keeps its place while the list scrolls. */
fun LazyListScope.chartItem(
    points: List<ChartPoint>,
    scale: ChartScale,
    onScaleChange: (ChartScale) -> Unit,
) {
    item(key = "chart") { InvestmentChart(points = points, scale = scale, onScaleChange = onScaleChange) }
}

/** What an asset or an account has made so far, as the summary's profit line. */
@Composable
fun profitLine(
    profit: Money,
    ratio: Double?,
) = SummaryLine(
    label = stringResource(R.string.investments_profit),
    amount = profit,
    isProfit = true,
    ratio = ratio,
    tag = PROFIT_TAG,
)

internal const val PROFIT_TAG = "investmentProfit"
internal const val REALISED_TAG = "investmentRealised"
