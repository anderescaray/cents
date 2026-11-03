package io.github.anderescaray.cents.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The palette of the film (docs/decisions/0003-the-ring.md): white cards on a pale cool canvas,
 * ink rather than black, and signal colours that mean something.
 *
 * These are fixed values, not derived from the wallpaper: there is no dynamic colour. Names are
 * roles, so a screen asks for `groupedBackground`, never for "grey 100". The `…Text` twins are the
 * signal colours darkened until they pass 4.5:1 as text on the canvas; the plain ones are fills.
 */
@Immutable
data class CentsColors(
    val accent: Color,
    /** Text and glyphs sitting on top of an accent fill. */
    val onAccent: Color,
    val destructive: Color,
    val positive: Color,
    val warning: Color,
    /** The accent as text: a link, a text button. */
    val accentText: Color,
    /** Money in, written as text. */
    val positiveText: Color,
    /** Near a limit, written as text. */
    val warningText: Color,
    /** Over a limit, written as text. */
    val destructiveText: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    /** Unselected icons in the tab capsule. */
    val inactiveIcon: Color,
    /** Background behind the cards: a cool pale grey in light, black in dark. */
    val groupedBackground: Color,
    /** The cards sitting on that background: white in light, near-black in dark. */
    val groupedCard: Color,
    /** Chips, keypad keys, the segmented track and input fields: a grey that sits inside a card. */
    val fill: Color,
    /** Pressed state for a tappable row. */
    val rowPressed: Color,
    val separator: Color,
    /** Navigation and tab bars, which sit above content. */
    val barBackground: Color,
    val barSeparator: Color,
    val switchTrackOff: Color,
    /**
     * Settings' round button, the same on every tab: a quiet grey of its own, set apart from the
     * canvas and from the white buttons beside it without calling attention to itself.
     */
    val settingsButton: Color,
    /** The soft shadow under a card, in light only: dark lifts a card by its lighter fill. */
    val cardShadow: Color,
    /** The veil over the screen behind a sheet or an alert. */
    val scrim: Color,
    /**
     * The one dark card of a screen, the total of what the owner has: deep ink from [heroTop] to
     * [heroBottom], with a glow of the accent in its corner. White text sits on it.
     */
    val heroTop: Color,
    val heroBottom: Color,
    val heroGlow: Color,
    /** Money below zero, as text on the hero card, where the plain red is too dark to read. */
    val heroNegative: Color,
    /**
     * The two parts of what the owner has, cash and bank and invested: indigo and teal, apart
     * from green (money in) and the accent blue (a transfer, and anything tappable).
     */
    val cashPart: Color,
    val investedPart: Color,
    val isDark: Boolean,
)

val LightColors =
    CentsColors(
        accent = Color(0xFF007AFF),
        onAccent = Color(0xFFFFFFFF),
        destructive = Color(0xFFFF3B30),
        positive = Color(0xFF28B45A),
        warning = Color(0xFFFF9500),
        accentText = Color(0xFF006BE0),
        positiveText = Color(0xFF1C8040),
        warningText = Color(0xFFA16000),
        destructiveText = Color(0xFFD43128),
        label = Color(0xFF0E1117),
        secondaryLabel = Color(0xFF6B717D),
        tertiaryLabel = Color(0xFFA9AFB9),
        inactiveIcon = Color(0xFF898E96),
        groupedBackground = Color(0xFFF4F5F9),
        groupedCard = Color(0xFFFFFFFF),
        fill = Color(0xFFEEF0F4),
        rowPressed = Color(0xFFEEF0F4),
        separator = Color(0x120E1117),
        barBackground = Color(0xFFF4F5F9),
        barSeparator = Color(0x120E1117),
        switchTrackOff = Color(0xFFE3E5EA),
        settingsButton = Color(0xFFE2E4EA),
        cardShadow = Color(0x1A101828),
        scrim = Color(0x470E1117),
        heroTop = Color(0xFF1D2540),
        heroBottom = Color(0xFF0E1117),
        heroGlow = Color(0x66007AFF),
        heroNegative = Color(0xFFFF8A80),
        cashPart = Color(0xFF5856D6),
        investedPart = Color(0xFF0E9AA7),
        isDark = false,
    )

val DarkColors =
    CentsColors(
        accent = Color(0xFF0A84FF),
        onAccent = Color(0xFFFFFFFF),
        destructive = Color(0xFFFF453A),
        positive = Color(0xFF30D158),
        warning = Color(0xFFFF9F0A),
        accentText = Color(0xFF0A84FF),
        positiveText = Color(0xFF30D158),
        warningText = Color(0xFFFF9F0A),
        destructiveText = Color(0xFFFF453A),
        label = Color(0xFFFFFFFF),
        secondaryLabel = Color(0x99EBEBF5),
        tertiaryLabel = Color(0x4DEBEBF5),
        inactiveIcon = Color(0x99EBEBF5),
        groupedBackground = Color(0xFF000000),
        groupedCard = Color(0xFF1C1C1E),
        fill = Color(0xFF2C2C2E),
        rowPressed = Color(0xFF2C2C2E),
        separator = Color(0x33FFFFFF),
        barBackground = Color(0xFF000000),
        barSeparator = Color(0x33FFFFFF),
        switchTrackOff = Color(0xFF39393D),
        settingsButton = Color(0xFF3A3A3C),
        cardShadow = Color(0x00000000),
        scrim = Color(0x99000000),
        heroTop = Color(0xFF243058),
        heroBottom = Color(0xFF15171F),
        heroGlow = Color(0x660A84FF),
        heroNegative = Color(0xFFFF8A80),
        cashPart = Color(0xFF7D7AFF),
        investedPart = Color(0xFF3CC8D6),
        isDark = true,
    )

val LocalCentsColors = staticCompositionLocalOf { LightColors }
