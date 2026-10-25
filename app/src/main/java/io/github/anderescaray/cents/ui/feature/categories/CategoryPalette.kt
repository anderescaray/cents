package io.github.anderescaray.cents.ui.feature.categories

/**
 * The colours a category can be given: the iOS system palette, then a second row of deeper and
 * softer tones, so that a ring of many categories still has slices that can be told apart.
 *
 * A closed set rather than a colour wheel on purpose — colours that look right together beat
 * sixteen million that do not, and a grid of dots is one tap instead of a dialog with a gradient
 * in it. The second dozen goes after the first, so the colour suggested for a new category stays
 * the same until the first twelve are used.
 */
val CategoryPalette: List<Int> =
    listOf(
        0xFFFF3B30.toInt(), // red
        0xFFFF9500.toInt(), // orange
        0xFFFFCC00.toInt(), // yellow
        0xFF34C759.toInt(), // green
        0xFF00C7BE.toInt(), // teal
        0xFF30B0C7.toInt(), // cyan
        0xFF007AFF.toInt(), // blue
        0xFF5856D6.toInt(), // indigo
        0xFFAF52DE.toInt(), // purple
        0xFFFF2D55.toInt(), // pink
        0xFFA2845E.toInt(), // brown
        0xFF8E8E93.toInt(), // grey
        0xFF8E1B2B.toInt(), // maroon
        0xFFFF8A65.toInt(), // salmon
        0xFFC99A06.toInt(), // mustard
        0xFF9ACD32.toInt(), // lime
        0xFF1E7B3A.toInt(), // forest
        0xFF0B7A75.toInt(), // deep teal
        0xFF64D2FF.toInt(), // sky
        0xFF1D3F8A.toInt(), // navy
        0xFF7B2D8E.toInt(), // plum
        0xFFD98CF2.toInt(), // lavender
        0xFFFF9EB5.toInt(), // rose
        0xFF3A3A3C.toInt(), // graphite
    )

/**
 * The colour offered for the next new category: the first one nothing uses yet, so a list
 * built one category at a time comes out varied without the owner having to think about it.
 */
fun nextPaletteColor(inUse: Collection<Int>): Int =
    CategoryPalette.firstOrNull { it !in inUse } ?: CategoryPalette[inUse.size % CategoryPalette.size]
