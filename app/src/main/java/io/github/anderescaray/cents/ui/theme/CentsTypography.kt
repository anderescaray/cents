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
 * Apple's text styles, at their documented sizes and line heights. Screens name the role
 * (`headline`, `footnote`) rather than a size, so scaling the whole app is one edit here.
 */
@Immutable
data class CentsTypography(
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
)

val DefaultTypography =
    CentsTypography(
        largeTitle = interStyle(34, 41, FontWeight.Bold, tracking = -0.012),
        title1 = interStyle(28, 34, FontWeight.Bold, tracking = -0.010),
        title2 = interStyle(22, 28, FontWeight.Bold, tracking = -0.008),
        title3 = interStyle(20, 25, FontWeight.SemiBold, tracking = -0.006),
        headline = interStyle(17, 22, FontWeight.SemiBold, tracking = -0.004),
        body = interStyle(17, 22, tracking = -0.004),
        callout = interStyle(16, 21, tracking = -0.003),
        subhead = interStyle(15, 20, tracking = -0.002),
        footnote = interStyle(13, 18),
        caption1 = interStyle(12, 16),
        caption2 = interStyle(11, 13),
    )

val LocalCentsTypography = staticCompositionLocalOf { DefaultTypography }
