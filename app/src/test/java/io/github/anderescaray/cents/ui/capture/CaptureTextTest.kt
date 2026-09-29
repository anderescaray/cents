package io.github.anderescaray.cents.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the listener reads of a notification, and what it treats as already read. */
class CaptureTextTest {
    @Test
    fun `the title and the expanded body are read as one line`() {
        assertEquals(
            "Mi Banco Compra en CAFÉ CENTRAL por 23,40 €. Saldo 1.502,10 €",
            CaptureText.of(
                title = "Mi Banco",
                text = "Compra en CAFÉ CENTRAL por 23,4…",
                bigText = "Compra en CAFÉ CENTRAL por 23,40 €. Saldo 1.502,10 €",
            ),
        )
    }

    @Test
    fun `without an expanded body the short one is read`() {
        assertEquals("Wallet Pago de 9,99 €", CaptureText.of("Wallet", "Pago de 9,99 €", bigText = null))
        assertEquals("Wallet Pago de 9,99 €", CaptureText.of("Wallet", "Pago de 9,99 €", bigText = "  "))
    }

    @Test
    fun `a body that repeats the title is not read twice, and nothing is null`() {
        assertEquals("Bizum recibido", CaptureText.of("Bizum recibido", "Bizum recibido", bigText = null))
        assertNull(CaptureText.of(title = null, text = " ", bigText = null))
    }

    @Test
    fun `a repost of the same notification is not new, an update with new words is`() {
        val seen = SeenNotifications()

        assertTrue(seen.isNew("bank|1", "Compra por 23,40 €"))
        assertFalse(seen.isNew("bank|1", "Compra por 23,40 €"))
        assertTrue(seen.isNew("bank|1", "Compra por 23,40 €. Saldo 1.502,10 €"))
        assertTrue(seen.isNew("bank|2", "Compra por 23,40 €"))
    }

    @Test
    fun `the memory is bounded, forgetting the oldest first`() {
        val seen = SeenNotifications(capacity = 2)
        seen.isNew("a", "1")
        seen.isNew("b", "2")
        seen.isNew("c", "3")

        // "a" was forgotten to make room, so it reads as new again; "c" is still remembered.
        assertTrue(seen.isNew("a", "1"))
        assertFalse(seen.isNew("c", "3"))
    }
}
