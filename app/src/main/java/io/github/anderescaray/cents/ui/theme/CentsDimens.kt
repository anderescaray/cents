package io.github.anderescaray.cents.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Metrics taken from iOS, kept in one place so spacing stays consistent across screens. */
object CentsDimens {
    /** Standard horizontal margin of a grouped list. */
    val screenMargin: Dp = 16.dp
    val rowHorizontalPadding: Dp = 16.dp
    val rowVerticalPadding: Dp = 11.dp

    /** Minimum comfortable tap target; iOS rows are 44pt. */
    val minRowHeight: Dp = 44.dp
    val groupedCornerRadius: Dp = 10.dp
    val sectionSpacing: Dp = 35.dp
    val sectionHeaderSpacing: Dp = 6.dp
    val separatorThickness: Dp = 0.5.dp
    val barHeight: Dp = 44.dp
    val tabBarHeight: Dp = 49.dp
    val spacingXs: Dp = 4.dp
    val spacingS: Dp = 8.dp
    val spacingM: Dp = 12.dp
    val spacingL: Dp = 16.dp
}
