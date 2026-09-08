package io.github.anderescaray.cents.domain.model

/**
 * Where one bubble sits on the canvas, **in fractions of the canvas rather than in pixels**.
 *
 * A layout has to mean the same thing on a screen it was not arranged on: a rotation, a split
 * screen, a font-scale change or a different device all resize the canvas, and a position saved
 * in pixels would put a bubble half outside it. [centerX] and [centerY] are fractions of the
 * canvas width and height, and [diameter] is a fraction of its **shorter side** — turning the
 * phone sideways then leaves a bubble the size it was in the hand, where a fraction of the
 * width would make it taller than the screen it is drawn on.
 */
data class BubblePlacement(
    val centerX: Float,
    val centerY: Float,
    val diameter: Float,
) {
    /**
     * The same placement, made to fit a canvas of this shape — [aspect] being width ÷ height.
     *
     * A bubble is kept whole and inside: too large for the canvas it is shrunk, too near an
     * edge it is pushed in. A bubble that cannot fit on an axis at all is centred on it rather
     * than clamped to an impossible range, which is what a very tall bubble on a very wide
     * canvas would otherwise ask for.
     */
    fun fittedTo(aspect: Float): BubblePlacement {
        val size = diameter.coerceIn(MIN_DIAMETER, MAX_DIAMETER)
        // The radius is a fraction of the shorter side, and each axis wants it as a fraction of
        // itself, so the longer one divides it down.
        val radiusX = if (aspect <= 1f) size / 2f else size / 2f / aspect
        val radiusY = if (aspect <= 1f) size / 2f * aspect else size / 2f
        return BubblePlacement(
            centerX = centerX.coerceIn(minOf(radiusX, HALF), maxOf(1f - radiusX, HALF)),
            centerY = centerY.coerceIn(minOf(radiusY, HALF), maxOf(1f - radiusY, HALF)),
            diameter = size,
        )
    }

    companion object {
        /** Small enough to be out of the way, never smaller than a comfortable tap target. */
        const val MIN_DIAMETER = 0.22f
        const val MAX_DIAMETER = 0.62f
        private const val HALF = 0.5f
    }
}

/**
 * What the owner arranged on the home canvas, and what the shell remembers about it.
 *
 * Only what was actually moved is held: a section with no placement is laid out where the
 * canvas puts it by default, so a new section does not need a migration and a layout saved
 * before it existed is not wrong, merely quiet about it.
 *
 * [lastSection] is the section that was open when the app was last used, so reopening lands
 * where it left off rather than always on the canvas.
 */
data class HomeLayout(
    val placements: Map<String, BubblePlacement> = emptyMap(),
    val tabBarCollapsed: Boolean = false,
    val lastSection: String? = null,
) {
    companion object {
        val Untouched = HomeLayout()
    }
}
