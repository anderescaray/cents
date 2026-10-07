package io.github.anderescaray.cents.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the picker's search field finds: by name, with case and accents set aside. */
class PickerMatchingTest {
    private val choices =
        listOf(
            PickerChoice(1, "Café"),
            PickerChoice(2, "Groceries"),
            PickerChoice(3, "Peluquería"),
        )

    @Test
    fun `nothing typed shows everything, in its own order`() {
        assertEquals(listOf(1L, 2L, 3L), choices.matching("  ").map { it.id })
    }

    @Test
    fun `accents and capitals do not stand between the owner and the name`() {
        assertEquals(listOf(1L), choices.matching("CAFE").map { it.id })
        assertEquals(listOf(3L), choices.matching("peluqueria").map { it.id })
    }

    @Test
    fun `any part of the name will do`() {
        assertEquals(listOf(2L), choices.matching("roc").map { it.id })
    }
}
