package io.github.anderescaray.cents.ui.feature.categories

/**
 * The colours a category can be given: the iOS system palette, which is also what the
 * design system paints everything else with.
 *
 * A closed set rather than a colour wheel on purpose — twelve colours that look right
 * together beat sixteen million that do not, and a list of dots is one tap instead of a
 * dialog with a gradient in it.
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
    )

/**
 * The colour offered for the next new category: the first one nothing uses yet, so a list
 * built one category at a time comes out varied without the owner having to think about it.
 */
fun nextPaletteColor(inUse: Collection<Int>): Int =
    CategoryPalette.firstOrNull { it !in inUse } ?: CategoryPalette[inUse.size % CategoryPalette.size]
