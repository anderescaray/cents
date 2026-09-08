package io.github.anderescaray.cents.ui.feature.home

import io.github.anderescaray.cents.domain.model.BubblePlacement

/**
 * Where the bubbles sit before anybody moves them.
 *
 * Staggered rather than a grid of four, because a grid reads as a launcher of icons and the
 * point of the canvas is that the sections float. The numbers are fractions of the canvas, so
 * this arrangement is the same shape on every screen — and [BubblePlacement.fittedTo] is what
 * keeps it inside a canvas the arrangement was not designed for.
 */
internal fun defaultPlacement(index: Int): BubblePlacement = DEFAULT_PLACEMENTS[index % DEFAULT_PLACEMENTS.size]

/**
 * The gap between two bubbles as a fraction of the smaller one's diameter, negative when they
 * overlap. Used by the tests that keep the default arrangement from crowding on a real screen,
 * and by nothing at runtime: bubbles may overlap once the owner drags them, and stopping that
 * would mean fighting the finger.
 */
internal fun clearanceBetween(
    first: BubblePlacement,
    second: BubblePlacement,
    aspect: Float,
): Float {
    // The two centres are fractions of different axes, so both come back to one square space
    // measured in shorter sides — the same unit the diameters are already in.
    val dx = (first.centerX - second.centerX) * if (aspect <= 1f) 1f else aspect
    val dy = (first.centerY - second.centerY) * if (aspect <= 1f) 1f / aspect else 1f
    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
    val radii = (first.diameter + second.diameter) / 2f
    return (distance - radii) / minOf(first.diameter, second.diameter)
}

private const val DEFAULT_DIAMETER = 0.36f

private val DEFAULT_PLACEMENTS =
    listOf(
        BubblePlacement(centerX = 0.30f, centerY = 0.22f, diameter = DEFAULT_DIAMETER),
        BubblePlacement(centerX = 0.71f, centerY = 0.38f, diameter = DEFAULT_DIAMETER),
        BubblePlacement(centerX = 0.29f, centerY = 0.56f, diameter = DEFAULT_DIAMETER),
        BubblePlacement(centerX = 0.70f, centerY = 0.74f, diameter = DEFAULT_DIAMETER),
    )
