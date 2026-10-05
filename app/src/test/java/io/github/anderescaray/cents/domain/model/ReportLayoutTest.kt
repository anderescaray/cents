package io.github.anderescaray.cents.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportLayoutTest {
    private val known = listOf("budgets", "spending", "flow", "balance")

    @Test
    fun `nothing saved is the default order, everything shown`() {
        val layout = ReportLayout()

        assertEquals(known, layout.arrange(known))
        assertTrue(known.all(layout::isVisible))
    }

    @Test
    fun `a saved order comes first, and a card added since is appended in its default place`() {
        val layout = ReportLayout(order = listOf("flow", "budgets", "spending"))

        assertEquals(listOf("flow", "budgets", "spending", "balance"), layout.arrange(known))
    }

    @Test
    fun `a card that no longer exists is dropped rather than shown`() {
        val layout = ReportLayout(order = listOf("gone", "flow"))

        assertEquals(listOf("flow", "budgets", "spending", "balance"), layout.arrange(known))
    }

    @Test
    fun `moving budgets down puts it after the card that followed it`() {
        val moved = ReportLayout().move("budgets", by = 1, known = known)

        assertEquals(listOf("spending", "budgets", "flow", "balance"), moved.arrange(known))
    }

    @Test
    fun `moving past either end changes nothing`() {
        val layout = ReportLayout()

        assertEquals(layout, layout.move("budgets", by = -1, known = known))
        assertEquals(layout, layout.move("balance", by = 1, known = known))
    }

    @Test
    fun `hiding a card keeps its place, and showing it again puts it back there`() {
        val hidden = ReportLayout().withVisible("spending", visible = false)

        assertFalse(hidden.isVisible("spending"))
        assertEquals(known, hidden.arrange(known))
        assertTrue(hidden.withVisible("spending", visible = true).isVisible("spending"))
    }
}
