package io.github.anderescaray.cents.ui.capture

import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.usecase.CaptureParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the listener reads of a notification, whether it reads it, and what it has already read. */
class CaptureTextTest {
    @Test
    fun `only a chosen app's notification is read, and only while capture is on`() {
        assertTrue(worth())
        assertFalse(worth(packageName = "com.whatsapp"))
        assertFalse(worth(enabled = false))
        // Cents' own reminders, "Netflix, 12,99 €, in 3 days", are not payments.
        assertFalse(worth(packageName = OWN, chosen = setOf(OWN)))
    }

    @Test
    fun `ongoing notifications and a group's summary are never read`() {
        assertFalse(worth(ongoing = true))
        assertFalse(worth(groupSummary = true))
    }

    @Test
    fun `the expanded body is read first, then the title`() {
        assertEquals(
            "Compra en CAFÉ CENTRAL por 23,40 €. Saldo 1.502,10 €${SEP}Mi Banco",
            CaptureText.of(
                title = "Mi Banco",
                text = "Compra en CAFÉ CENTRAL por 23,4…",
                bigText = "Compra en CAFÉ CENTRAL por 23,40 €. Saldo 1.502,10 €",
            ),
        )
    }

    @Test
    fun `a balance in the title does not come before the payment`() {
        val text = requireNotNull(CaptureText.of("Saldo 1.502,10 €", "Compra por 23,40 €", bigText = null))

        assertTrue(text.startsWith("Compra por 23,40 €"))
    }

    @Test
    fun `without an expanded body the short one is read`() {
        assertEquals("Pago de 9,99 €${SEP}Wallet", CaptureText.of("Wallet", "Pago de 9,99 €", bigText = null))
        assertEquals("Pago de 9,99 €${SEP}Wallet", CaptureText.of("Wallet", "Pago de 9,99 €", bigText = "  "))
    }

    @Test
    fun `a notification that bundles movements is read line by line`() {
        assertEquals(
            "Compra 23,40 €. Compra 9,99 €${SEP}Mi Banco",
            CaptureText.of(
                title = "Mi Banco",
                text = "2 nuevos movimientos",
                bigText = null,
                lines = listOf("Compra 23,40 €", "Compra 9,99 €"),
            ),
        )
    }

    @Test
    fun `a title that repeats the body's amount is one payment, and a different one is not guessed`() {
        val ignore = CaptureSettings.DEFAULT_IGNORE_WORDS
        val repeated = requireNotNull(CaptureText.of("23,40 €", "Compra por 23,40 € en Amazon", bigText = null))
        assertEquals(CaptureParser.Amount(2_340, "EUR"), CaptureParser.amountIn(repeated, ignore))
        val inTitleOnly = requireNotNull(CaptureText.of("Compra 23,40 €", "Pago en Amazon", bigText = null))
        assertEquals(CaptureParser.Amount(2_340, "EUR"), CaptureParser.amountIn(inTitleOnly, ignore))
        // A fee in the body is still a second amount, whatever the title says.
        val fee = requireNotNull(CaptureText.of("23,40 €", "Compra 23,40 €, comisión 1,50 €", bigText = null))
        assertEquals(null, CaptureParser.amountIn(fee, ignore))
        // So is a different amount in the title, and a balance there is skipped by its word.
        assertEquals(
            null,
            CaptureParser.amountIn(requireNotNull(CaptureText.of("9,99 €", "Compra 23,40 €", null)), ignore),
        )
        val balance = requireNotNull(CaptureText.of("Saldo 1.502,10 €", "Compra por 23,40 €", bigText = null))
        assertEquals(CaptureParser.Amount(2_340, "EUR"), CaptureParser.amountIn(balance, ignore))
        // And it reads as one sentence wherever it is shown.
        assertEquals("Compra por 23,40 € en Amazon 23,40 €", CaptureParser.readable(repeated))
    }

    @Test
    fun `a body that repeats the title is not read twice, and nothing is null`() {
        assertEquals("Bizum recibido", CaptureText.of("Bizum recibido", "Bizum recibido", bigText = null))
        assertNull(CaptureText.of(title = null, text = " ", bigText = null))
    }

    @Test
    fun `a repost of the same notification is not new, an update or a new movement is`() {
        val seen = SeenNotifications()

        assertTrue(seen.isNew("bank|1", MORNING, "Compra por 2,50 €"))
        assertFalse(seen.isNew("bank|1", MORNING, "Compra por 2,50 €"))
        assertTrue(seen.isNew("bank|1", MORNING, "Compra por 2,50 €. Saldo 1.502,10 €"))
        // The same key and the same words, hours later: the bank reuses one key, and this is a
        // second coffee, not a repost of the first.
        assertTrue(seen.isNew("bank|1", AFTERNOON, "Compra por 2,50 €. Saldo 1.502,10 €"))
    }

    @Test
    fun `a forgotten key reads as new again`() {
        val seen = SeenNotifications()
        seen.isNew("bank|1", MORNING, "Compra por 2,50 €")

        seen.forget("bank|1")

        assertTrue(seen.isNew("bank|1", MORNING, "Compra por 2,50 €"))
    }

    @Test
    fun `the memory is bounded, forgetting the oldest first`() {
        val seen = SeenNotifications(capacity = 2)
        seen.isNew("a", MORNING, "1")
        seen.isNew("b", MORNING, "2")
        seen.isNew("c", MORNING, "3")

        // "a" was forgotten to make room, so it reads as new again; "c" is still remembered.
        assertTrue(seen.isNew("a", MORNING, "1"))
        assertFalse(seen.isNew("c", MORNING, "3"))
    }

    private fun worth(
        packageName: String = BANK,
        chosen: Set<String> = setOf(BANK),
        enabled: Boolean = true,
        ongoing: Boolean = false,
        groupSummary: Boolean = false,
    ) = CaptureText.worthReading(packageName, OWN, chosen, enabled, ongoing, groupSummary)

    private companion object {
        const val BANK = "es.example.bank"
        const val OWN = "io.github.anderescaray.cents"
        const val MORNING = 1_790_000_000_000L
        const val AFTERNOON = MORNING + 7 * 60 * 60 * 1_000L
        const val SEP = CaptureParser.TITLE_SEPARATOR
    }
}
