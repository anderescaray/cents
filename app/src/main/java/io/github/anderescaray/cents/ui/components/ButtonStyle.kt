package io.github.anderescaray.cents.ui.components

/** How a [CentsButton] is painted. */
enum class ButtonStyle {
    /** The one filled action a sheet ends in: *Add expense*. */
    PRIMARY,

    /** A tinted action that is not the main one: *Back up now*. */
    TINTED,

    /** A grey key-like action: *JSON*, *CSV*. */
    PLAIN,

    /** Filled red, for the action that removes something. */
    DESTRUCTIVE,
}
