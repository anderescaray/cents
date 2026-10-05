package io.github.anderescaray.cents.domain

import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.usecase.CaptureParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How a notification's text becomes a kind and an amount (`DECISIONS.md` D29), on the kind of
 * sentence a bank's app actually sends. Nothing here needs a phone.
 */
class CaptureParserTest {
    @Test
    fun `a card payment is an expense for its amount`() {
        val text = "Compra con tarjeta en CAFÉ CENTRAL por 23,40 €"

        assertEquals(TransactionKind.EXPENSE, kind(text))
        assertEquals(CaptureParser.Amount(2_340, "EUR"), CaptureParser.amountIn(text))
    }

    @Test
    fun `money arriving is an income, even when it mentions a payment`() {
        // "pago" is an expense word, "recibido" an income one: the income wins (D29).
        assertEquals(TransactionKind.INCOME, kind("Has recibido un pago de Ana de 120,05 €"))
    }

    @Test
    fun `words are matched ignoring case and accents, and only as whole words`() {
        assertEquals(TransactionKind.INCOME, kind("NOMINA ingresada: 2.412,50 EUR"))
        assertEquals(TransactionKind.EXPENSE, kind("Domiciliacion de recibo 45,00 €"))
        // "encargo" holds "cargo", but it is not the word.
        assertNull(kind("Tu encargo está en camino, 12,00 €"))
    }

    @Test
    fun `no word, or no amount, is nothing`() {
        assertNull(kind("Nuevas ofertas en tu banco: hasta 30 € de descuento"))
        assertNull(CaptureParser.amountIn("Compra aprobada"))
    }

    @Test
    fun `the amount is read however the bank writes it`() {
        assertEquals(CaptureParser.Amount(2_340, "EUR"), CaptureParser.amountIn("23,40 €"))
        assertEquals(CaptureParser.Amount(2_340, "EUR"), CaptureParser.amountIn("€23.40"))
        assertEquals(CaptureParser.Amount(123_456, "EUR"), CaptureParser.amountIn("EUR 1.234,56"))
        assertEquals(CaptureParser.Amount(123_456, "USD"), CaptureParser.amountIn("USD 1,234.56"))
        // A bare "$" is also the peso and several dollars: it names no currency.
        assertNull(CaptureParser.amountIn("$1,234.56"))
        assertEquals(CaptureParser.Amount(1_500, "EUR"), CaptureParser.amountIn("15 euros"))
        assertNull(CaptureParser.amountIn("importe 23,40"))
        assertEquals(CaptureParser.Amount(250, "EUR"), CaptureParser.amountIn("2,5 €"))
    }

    @Test
    fun `a bare number without decimals or a currency is not an amount`() {
        // A card's last digits, a day, a count: a bank's text is full of numbers that are not money.
        assertNull(CaptureParser.amountIn("Tarjeta terminada en 1234, el día 29"))
    }

    @Test
    fun `multiple currency marked amounts are ambiguous in either order`() {
        assertNull(CaptureParser.amountIn("Pago de 23,40 €. Saldo disponible: 1.502,10 €"))
        assertNull(CaptureParser.amountIn("Saldo disponible: 1.502,10 €. Pago de 23,40 €"))
        assertNull(CaptureParser.amountIn("Compra 23 €. Otra compra 23 €"))
    }

    @Test
    fun `a date or a time before the amount is not the amount`() {
        assertEquals(
            CaptureParser.Amount(2_340, "EUR"),
            CaptureParser.amountIn("Compra el 12.03.2026 por 23,40 €"),
        )
        assertEquals(CaptureParser.Amount(500, "EUR"), CaptureParser.amountIn("A las 10.30, pago de 5,00 €"))
    }

    @Test
    fun `a card number or a cash machine network is not the amount`() {
        assertEquals(CaptureParser.Amount(15_000, "EUR"), CaptureParser.amountIn("Tarjeta *1234 150,00 €"))
        assertEquals(
            CaptureParser.Amount(5_000, "EUR"),
            CaptureParser.amountIn("Retirada en cajero Euro 6000 de 50,00 €"),
        )
    }

    @Test
    fun `edited words replace the defaults`() {
        val text = "Bizum de Ana: 15,00 €"

        assertNull(kind(text))
        assertEquals(
            TransactionKind.INCOME,
            CaptureParser.kindOf(text, expenseWords = emptyList(), incomeWords = listOf("bizum de")),
        )
    }

    @Test
    fun `the payment is recognized with currency on either side and a masked card`() {
        listOf(
            "23€ paid with the card ****7146",
            "Paid EUR 23 with card ****7146",
            "Paid with card ****7146 EUR 23",
            "Paid with card XXXX7146 EUR 23",
            "Paid with card ••••7146 EUR 23",
            "Paid with card …7146 EUR 23",
            "Paid with card ....7146 EUR 23",
            "Paid with card ****71 46 EUR 23",
            "Paid with card ****1234 5678 EUR 23",
        ).forEach { text ->
            assertEquals(text, CaptureParser.Amount(2_300, "EUR"), CaptureParser.amountIn(text))
        }
    }

    @Test
    fun `a masked number is never an amount even when next to a currency`() {
        listOf(
            "Paid with card ****7146 EUR",
            "Payment EUR ****7146",
            "Payment XXXX 7146€",
            "Payment ••••7146 EUR",
            "Payment 23 ****7146 €",
            "Payment ****71 46 EUR",
            "Payment ****1234 5678 EUR",
            "Payment ****71-46 EUR",
        ).forEach { text -> assertNull(text, CaptureParser.amountIn(text)) }
    }

    @Test
    fun `an amount needs a currency and never falls back to a time or bare decimals`() {
        listOf(
            "Payment at 10.30 with card ****7146",
            "Compra el 12.03.2026",
            "Paid 23.40 with card ****7146",
            "Ingreso 23,40",
        ).forEach { text -> assertNull(text, CaptureParser.amountIn(text)) }
    }

    @Test
    fun `markers around the same number must agree on its currency`() {
        assertEquals(CaptureParser.Amount(2_300, "EUR"), CaptureParser.amountIn("Paid EUR 23 €"))
        assertNull(CaptureParser.amountIn("Paid EUR 23 USD"))
        assertNull(CaptureParser.amountIn("Paid €23 and USD 23"))
        assertNull(CaptureParser.amountIn("Paid EUR23USD"))
        assertNull(CaptureParser.amountIn("Paid EUR23£"))
        assertNull(CaptureParser.amountIn("Paid USD23€"))
        assertEquals(CaptureParser.Amount(2_300, "EUR"), CaptureParser.amountIn("Paid EUR23€"))
    }

    @Test
    fun `mask removal never joins card digits to money or mistakes a merchant for a mask`() {
        assertEquals(CaptureParser.Amount(15_000, "EUR"), CaptureParser.amountIn("Payment card ***146 150,00 €"))
        assertEquals(CaptureParser.Amount(2_300, "EUR"), CaptureParser.amountIn("Netflix 23€ paid"))
    }

    @Test
    fun `currency markers accept nonbreaking spaces and grouped thousands`() {
        assertEquals(CaptureParser.Amount(123_456, "EUR"), CaptureParser.amountIn("EUR\u00a01\u202f234,56"))
        assertEquals(CaptureParser.Amount(123_456, "EUR"), CaptureParser.amountIn("1 234,56\u00a0EUR"))
        assertEquals(CaptureParser.Amount(123_456, "EUR"), CaptureParser.amountIn("EUR 1,234.56"))
    }

    @Test
    fun `zero malformed and oversized amounts are not recorded`() {
        assertNull(CaptureParser.amountIn("Paid 0 EUR"))
        assertNull(CaptureParser.amountIn("Paid EUR 1.23.45"))
        assertNull(CaptureParser.amountIn("Paid 23€45"))
        assertNull(CaptureParser.amountIn("Paid 999999999999999999999 EUR"))
    }

    private fun kind(text: String) =
        CaptureParser.kindOf(text, CaptureSettings.DEFAULT_EXPENSE_WORDS, CaptureSettings.DEFAULT_INCOME_WORDS)
}
