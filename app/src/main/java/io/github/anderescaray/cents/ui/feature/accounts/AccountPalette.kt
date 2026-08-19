package io.github.anderescaray.cents.ui.feature.accounts

import io.github.anderescaray.cents.ui.feature.categories.CategoryPalette

/**
 * Accounts are coloured from the same closed palette as categories: one set of colours
 * across the app is what makes a screen mixing both read as one thing.
 */
val AccountPalette: List<Int> = CategoryPalette

/** The colour offered for the next account: the first one no account uses yet. */
fun nextAccountColor(inUse: Collection<Int>): Int =
    AccountPalette.firstOrNull { it !in inUse } ?: AccountPalette[inUse.size % AccountPalette.size]
