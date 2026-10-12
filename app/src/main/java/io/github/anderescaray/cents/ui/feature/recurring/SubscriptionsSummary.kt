package io.github.anderescaray.cents.ui.feature.recurring

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.usecase.FixedChargeIncomeShare
import io.github.anderescaray.cents.domain.usecase.IncomeShareUnavailableReason
import io.github.anderescaray.cents.domain.usecase.SubscriptionTotals
import io.github.anderescaray.cents.ui.components.CentsListRow
import io.github.anderescaray.cents.ui.components.CentsListSection
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import io.github.anderescaray.cents.ui.util.formatLocale
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal fun LazyListScope.subscriptionSummary(totals: SubscriptionTotals) {
    item(key = "committed") {
        SubscriptionFigure(
            title = stringResource(R.string.subscriptions_committed_title),
            value =
                totals.committedThisMonth?.let { MoneyFormatter.format(it) }
                    ?: stringResource(R.string.subscriptions_figure_unavailable),
            footer =
                stringResource(
                    when {
                        totals.committedThisMonth == null -> R.string.subscriptions_mixed_currency
                        totals.committedIncludesEstimates -> R.string.subscriptions_committed_estimate
                        else -> R.string.subscriptions_committed_caption
                    },
                ),
            tag = COMMITTED_TOTAL_TAG,
        )
    }
    item(key = "incomeShare") {
        SubscriptionFigure(
            title = stringResource(R.string.subscriptions_income_share_title),
            value =
                totals.incomeShare?.let { formatIncomeShare(it) }
                    ?: stringResource(R.string.subscriptions_figure_unavailable),
            footer = stringResource(totals.incomeShareCaption),
            tag = INCOME_SHARE_TAG,
        )
    }
}

@Composable
private fun SubscriptionFigure(
    title: String,
    value: String,
    footer: String,
    tag: String,
) {
    CentsListSection(footer = footer) {
        CentsListRow(
            title = title,
            value = value,
            modifier = Modifier.testTag(tag).semantics(mergeDescendants = true) {},
            showSeparator = false,
        )
    }
}

@get:StringRes
private val SubscriptionTotals.incomeShareCaption: Int
    get() =
        when (incomeShareUnavailableReason) {
            IncomeShareUnavailableReason.NO_INCOME_RULES -> {
                R.string.subscriptions_income_share_no_income
            }

            IncomeShareUnavailableReason.ZERO_MONTHLY_INCOME -> {
                R.string.subscriptions_income_share_zero_income
            }

            IncomeShareUnavailableReason.MIXED_CURRENCIES -> {
                R.string.subscriptions_income_share_mixed_currency
            }

            null -> {
                if (incomeShare?.includesEstimates == true) {
                    R.string.subscriptions_income_share_estimate
                } else {
                    R.string.subscriptions_income_share_caption
                }
            }
        }

internal fun formatIncomeShare(
    share: FixedChargeIncomeShare,
    locale: Locale = formatLocale(),
): String {
    val ratio =
        share.monthlyExpenses.minorUnits
            .toBigDecimal()
            .divide(share.monthlyIncome.minorUnits.toBigDecimal(), PERCENT_RATIO_SCALE, RoundingMode.HALF_UP)
    return NumberFormat
        .getPercentInstance(locale)
        .apply {
            maximumFractionDigits = 1
            roundingMode = RoundingMode.HALF_UP
        }.format(ratio)
}

private const val PERCENT_RATIO_SCALE = 3
internal const val COMMITTED_TOTAL_TAG = "subscriptionsCommittedTotal"
internal const val INCOME_SHARE_TAG = "subscriptionsIncomeShare"
