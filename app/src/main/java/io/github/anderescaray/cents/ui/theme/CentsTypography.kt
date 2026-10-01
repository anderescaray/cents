package io.github.anderescaray.cents.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.anderescaray.cents.R

/**
 * Inter, the substitute for SF Pro — which Apple licenses for Apple platforms only and
 * cannot ship in an APK. One variable font file covers every weight.
 */
@OptIn(ExperimentalTextApi::class)
val InterFamily =
    FontFamily(
        listOf(400, 500, 600, 700).map { weight ->
            Font(
                resId = R.font.inter_variable,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )

/**
 * The type scale of the film (ADR 0003). Screens name the role (`headline`, `footnote`) rather
 * than a size, so scaling the whole app is one edit here. Running text is Medium rather than
 * Regular: at 13–16 sp Inter's Regular reads thin beside the bold numbers.
 */
@Immutable
data class CentsTypography(
    /** The one number a card leads with: net worth, what a month cost. */
    val display: TextStyle,
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption1: TextStyle,
    val caption2: TextStyle,
    /** A small label in capitals, spaced out: *MOST SPENT THIS MONTH*. */
    val overline: TextStyle,
)

private fun interStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = InterFamily,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    // Tabular figures: a total that changes does not jitter, and amounts in a column line up.
    fontFeatureSettings = "tnum",
)

val DefaultTypography =
    CentsTypography(
        display = interStyle(40, 46, FontWeight.Bold, tracking = -0.03),
        largeTitle = interStyle(30, 36, FontWeight.Bold, tracking = -0.025),
        title1 = interStyle(27, 32, FontWeight.Bold, tracking = -0.02),
        title2 = interStyle(22, 28, FontWeight.Bold, tracking = -0.02),
        title3 = interStyle(20, 25, FontWeight.Bold, tracking = -0.02),
        headline = interStyle(17, 22, FontWeight.SemiBold, tracking = -0.01),
        body = interStyle(16, 21, FontWeight.Medium, tracking = -0.01),
        callout = interStyle(16, 21, FontWeight.Medium, tracking = -0.01),
        subhead = interStyle(15, 20, FontWeight.Medium, tracking = -0.01),
        footnote = interStyle(13, 18, FontWeight.Medium),
        caption1 = interStyle(12, 16, FontWeight.Medium),
        caption2 = interStyle(11, 13, FontWeight.Medium),
        overline = interStyle(12, 16, FontWeight.SemiBold, tracking = 0.08),
    )

val LocalCentsTypography = staticCompositionLocalOf { DefaultTypography }
