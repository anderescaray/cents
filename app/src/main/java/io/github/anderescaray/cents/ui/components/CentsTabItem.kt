package io.github.anderescaray.cents.ui.components

import androidx.annotation.DrawableRes

/** One destination in the bottom bar. */
data class CentsTabItem(
    val key: String,
    val label: String,
    @param:DrawableRes val iconRes: Int,
)
