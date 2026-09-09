package io.github.anderescaray.cents.ui.feature.investments

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.AssetClass
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.ui.theme.CentsTheme
import java.text.NumberFormat
import java.util.Locale

/** The heading each kind of asset is listed under. */
@get:StringRes
val AssetClass.labelRes: Int
    get() =
        when (this) {
            AssetClass.STOCK -> R.string.investments_class_stock
            AssetClass.ETF -> R.string.investments_class_etf
            AssetClass.INDEX_FUND -> R.string.investments_class_index_fund
            AssetClass.CRYPTO -> R.string.investments_class_crypto
        }

@Composable
fun assetClassLabel(assetClass: AssetClass): String = stringResource(assetClass.labelRes)

/**
 * Green for a gain, red for a loss, and the ordinary label colour for neither.
 *
 * Money out being red and money in being green is the rule the movement list already follows,
 * and a profit is money in. Zero is deliberately not green: a position that has made nothing is
 * not good news, and colouring it as if it were is how a screen starts flattering the owner.
 */
@Composable
fun profitColor(profit: Money?): Color =
    when {
        profit == null || profit.minorUnits == 0L -> CentsTheme.colors.label
        profit.minorUnits > 0L -> CentsTheme.colors.positive
        else -> CentsTheme.colors.destructive
    }

/**
 * A return as a percentage — `+12,4 %`.
 *
 * The sign is always shown, because a percentage with no sign next to an amount that has one
 * reads as a gain whatever it is.
 */
fun formatReturn(
    ratio: Double,
    locale: Locale = Locale.getDefault(),
): String {
    val formatter =
        NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    val text = formatter.format(kotlin.math.abs(ratio))
    val sign =
        when {
            ratio > 0 -> "+"
            ratio < 0 -> MINUS
            else -> ""
        }
    return sign + text
}

/** The typographic minus sign, which is what iOS uses in numbers. */
private const val MINUS = "−"
