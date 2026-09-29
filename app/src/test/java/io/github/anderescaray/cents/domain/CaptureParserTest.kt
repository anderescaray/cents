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
        assertEquals(CaptureParser.Amount(123_456, null), CaptureParser.amountIn("$1,234.56"))
        assertEquals(CaptureParser.Amount(1_500, "EUR"), CaptureParser.amountIn("15 euros"))
        assertEquals(CaptureParser.Amount(2_340, null), CaptureParser.amountIn("importe 23,40"))
        assertEquals(CaptureParser.Amount(250, "EUR"), CaptureParser.amountIn("2,5 €"))
    }

    @Test
    fun `a bare number without decimals or a currency is not an amount`() {
        // A card's last digits, a day, a count: a bank's text is full of numbers that are not money.
        assertNull(CaptureParser.amountIn("Tarjeta terminada en 1234, el día 29"))
    }

    @Test
    fun `the first amount is the one taken`() {
        assertEquals(
            CaptureParser.Amount(2_340, "EUR"),
            CaptureParser.amountIn("Pago de 23,40 €. Saldo disponible: 1.502,10 €"),
        )
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

    private fun kind(text: String) =
        CaptureParser.kindOf(text, CaptureSettings.DEFAULT_EXPENSE_WORDS, CaptureSettings.DEFAULT_INCOME_WORDS)
}
