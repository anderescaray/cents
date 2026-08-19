package io.github.anderescaray.cents.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The iOS system palette, transcribed for both appearances.
 *
 * These are fixed values, not derived from the wallpaper: dynamic colour is deliberately
 * out (see docs/decisions/0001-ios-flavored-ui.md). Names follow Apple's semantic naming
 * so a screen asks for `groupedBackground`, never for "grey 100".
 */
@Immutable
data class CentsColors(
    val accent: Color,
    /** Text and glyphs sitting on top of an accent fill. */
    val onAccent: Color,
    val destructive: Color,
    val positive: Color,
    val warning: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    /** Background behind grouped lists: grey in light, black in dark. */
    val groupedBackground: Color,
    /** The cards sitting on that background: white in light, near-black in dark. */
    val groupedCard: Color,
    /** Pressed state for a tappable row. */
    val rowPressed: Color,
    val separator: Color,
    /** Navigation and tab bars, which sit above content. */
    val barBackground: Color,
    val barSeparator: Color,
    val switchTrackOff: Color,
    val isDark: Boolean,
)

val LightColors =
    CentsColors(
        accent = Color(0xFF007AFF),
        onAccent = Color(0xFFFFFFFF),
        destructive = Color(0xFFFF3B30),
        positive = Color(0xFF34C759),
        warning = Color(0xFFFF9500),
        label = Color(0xFF000000),
        secondaryLabel = Color(0x993C3C43),
        tertiaryLabel = Color(0x4D3C3C43),
        groupedBackground = Color(0xFFF2F2F7),
        groupedCard = Color(0xFFFFFFFF),
        rowPressed = Color(0xFFD1D1D6),
        separator = Color(0x493C3C43),
        barBackground = Color(0xFAF9F9F9),
        barSeparator = Color(0x4D3C3C43),
        switchTrackOff = Color(0xFFE9E9EA),
        isDark = false,
    )

val DarkColors =
    CentsColors(
        accent = Color(0xFF0A84FF),
        onAccent = Color(0xFFFFFFFF),
        destructive = Color(0xFFFF453A),
        positive = Color(0xFF30D158),
        warning = Color(0xFFFF9F0A),
        label = Color(0xFFFFFFFF),
        secondaryLabel = Color(0x99EBEBF5),
        tertiaryLabel = Color(0x4DEBEBF5),
        groupedBackground = Color(0xFF000000),
        groupedCard = Color(0xFF1C1C1E),
        rowPressed = Color(0xFF2C2C2E),
        separator = Color(0xA6545458),
        barBackground = Color(0xFA1D1D1D),
        barSeparator = Color(0xA6545458),
        switchTrackOff = Color(0xFF39393D),
        isDark = true,
    )

val LocalCentsColors = staticCompositionLocalOf { LightColors }
