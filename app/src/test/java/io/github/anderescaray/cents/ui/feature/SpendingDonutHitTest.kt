package io.github.anderescaray.cents.ui.feature

import androidx.compose.ui.geometry.Offset
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.ui.feature.reports.section.sliceAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

/**
 * Where a tap on the ring lands.
 *
 * Vico draws the pie and offers no hit testing, so this geometry is reconstructed rather
 * than asked for — which makes it exactly the kind of thing that is wrong in a way no one
 * notices until a tap opens the wrong category's movements. It cannot be checked by
 * reasoning about a phone screen, so it is checked here.
 */
class SpendingDonutHitTest {
    // Four equal quarters, in the order the chart draws them: clockwise from twelve.
    private val breakdown =
        SpendingBreakdown(
            period = ReportPeriod.Month(YearMonth.of(2026, 8)),
            slices =
                listOf(
                    slice("North-east", 0.25f),
                    slice("South-east", 0.25f),
                    slice("South-west", 0.25f),
                    slice("North-west", 0.25f),
                ),
            total = Money(400, "EUR"),
        )

    @Test
    fun `each quarter of the ring belongs to the slice drawn there`() {
        // A point at 45°, on a radius halfway between the hole and the outer edge. Both
        // legs are that radius times cos 45°, or the point lands outside the circle.
        val leg = (RADIUS + HOLE / 2f) / 2f * ROOT2_OVER_2

        assertEquals("North-east", nameAt(Offset(CENTRE + leg, CENTRE - leg)))
        assertEquals("South-east", nameAt(Offset(CENTRE + leg, CENTRE + leg)))
        assertEquals("South-west", nameAt(Offset(CENTRE - leg, CENTRE + leg)))
        assertEquals("North-west", nameAt(Offset(CENTRE - leg, CENTRE - leg)))
    }

    @Test
    fun `the first slice starts at twelve o'clock, not at three`() {
        // Just clockwise of straight up. Reading the angle from three o'clock instead would
        // put this in the last slice rather than the first.
        val justPastTwelve = Offset(CENTRE + NUDGE, CENTRE - RADIUS * MID_RING)

        assertEquals("North-east", nameAt(justPastTwelve))
    }

    @Test
    fun `a tap in the hole hits nothing`() {
        assertNull(nameAt(Offset(CENTRE, CENTRE)))
    }

    @Test
    fun `a tap outside the ring hits nothing`() {
        // The corner of the box: inside the composable, outside the circle.
        assertNull(nameAt(Offset(0f, 0f)))
    }

    @Test
    fun `an uneven ring still splits where the shares say`() {
        val uneven =
            breakdown.copy(
                slices = listOf(slice("Most of it", 0.9f), slice("The rest", 0.1f)),
            )

        // Five o'clock is 150° clockwise from twelve, well inside the first 324°.
        val fiveOClock = Offset(CENTRE + RADIUS * MID_RING * HALF, CENTRE + RADIUS * MID_RING * ROOT3_OVER_2)
        // Eleven o'clock is 330°, inside the last 36°.
        val elevenOClock = Offset(CENTRE - RADIUS * MID_RING * HALF, CENTRE - RADIUS * MID_RING * ROOT3_OVER_2)

        assertEquals("Most of it", uneven.sliceAt(fiveOClock, SIDE, SIDE, HOLE)?.name)
        assertEquals("The rest", uneven.sliceAt(elevenOClock, SIDE, SIDE, HOLE)?.name)
    }

    @Test
    fun `the hole stays its own size when the ring shrinks`() {
        // Vico takes the hole as an absolute diameter, so on a box too narrow for the full
        // ring the ring shrinks and the hole does not. Treating the hole as a fraction of
        // the ring would start counting hole as ring here.
        val narrow = 140f
        val justOutsideTheHole = Offset(narrow / 2f, CENTRE - HOLE / 2f - NUDGE)

        assertEquals("North-east", breakdown.sliceAt(justOutsideTheHole, narrow, SIDE, HOLE)?.name)
        assertNull(breakdown.sliceAt(Offset(narrow / 2f, CENTRE - HOLE / 2f + NUDGE), narrow, SIDE, HOLE))
    }

    @Test
    fun `the circle is centred in a box wider than it is tall`() {
        // The composable fills the width and is fixed in height, so the ring is a circle in
        // the middle, not an ellipse: a tap out at the wide edge is off the chart.
        val wide = SIDE * 3f
        val centre = Offset(wide / 2f, CENTRE)

        val leg = RADIUS * MID_RING * ROOT2_OVER_2

        assertNull(breakdown.sliceAt(Offset(NUDGE, CENTRE), wide, SIDE, HOLE))
        assertNull(breakdown.sliceAt(centre, wide, SIDE, HOLE))
        assertEquals(
            "North-east",
            breakdown.sliceAt(Offset(wide / 2f + leg, CENTRE - leg), wide, SIDE, HOLE)?.name,
        )
    }

    private fun nameAt(tap: Offset) = breakdown.sliceAt(tap, SIDE, SIDE, HOLE)?.name

    private fun slice(
        name: String,
        share: Float,
    ) = CategorySpending(
        categoryId = name.hashCode().toLong(),
        name = name,
        colorArgb = null,
        total = Money((share * 400).toLong(), "EUR"),
        transactionCount = 1,
        share = share,
    )

    private companion object {
        const val SIDE = 220f
        const val CENTRE = SIDE / 2f
        const val RADIUS = SIDE / 2f

        /** The hole's diameter, the way the composable measures and passes it. */
        const val HOLE = 104f

        /** A radius safely inside the ring band, as a fraction of the outer radius. */
        const val MID_RING = 0.85f

        const val HALF = 0.5f
        const val ROOT2_OVER_2 = 0.7071f
        const val ROOT3_OVER_2 = 0.866f
        const val NUDGE = 1f
    }
}
