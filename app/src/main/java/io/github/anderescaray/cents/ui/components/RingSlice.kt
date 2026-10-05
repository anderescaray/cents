package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** One arc of the ring: a share of the whole, in a category's colour. */
@Immutable
data class RingSlice(
    val key: String,
    val share: Float,
    val color: Color,
)
