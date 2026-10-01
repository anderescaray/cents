package io.github.anderescaray.cents.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing and shape, kept in one place so every card and row agrees (ADR 0003, *Tokens*). */
object CentsDimens {
    /** Standard horizontal margin between a card and the screen edge. */
    val screenMargin: Dp = 16.dp
    val rowHorizontalPadding: Dp = 16.dp
    val rowVerticalPadding: Dp = 12.dp

    /** A row inside a card: roomier than a table row, so a thumb never lands on its neighbour. */
    val minRowHeight: Dp = 54.dp

    /** Every card's corner. The name predates cards; it is kept because every screen asks for it. */
    val groupedCornerRadius: Dp = 22.dp
    val cardCornerRadius: Dp = 22.dp

    /** Between one card and the next. */
    val sectionSpacing: Dp = 16.dp
    val sectionHeaderSpacing: Dp = 6.dp
    val separatorThickness: Dp = 0.5.dp
    val barHeight: Dp = 52.dp
    val tabBarHeight: Dp = 64.dp
    val chipHeight: Dp = 36.dp
    val primaryButtonHeight: Dp = 56.dp
    val primaryButtonRadius: Dp = 20.dp
    val keyHeight: Dp = 56.dp
    val keyRadius: Dp = 18.dp
    val badgeSize: Dp = 36.dp
    val spacingXs: Dp = 4.dp
    val spacingS: Dp = 8.dp
    val spacingM: Dp = 12.dp
    val spacingL: Dp = 16.dp
    val spacingXl: Dp = 24.dp
}
