package io.github.anderescaray.cents.ui.feature

import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.usecase.FixedChargeIncomeShare
import io.github.anderescaray.cents.ui.feature.recurring.formatIncomeShare
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SubscriptionsSummaryTest {
    @Test
    fun `a fraction is rounded only for display`() {
        assertEquals("33.3%", formatIncomeShare(share(1, 3), Locale.US))
    }

    @Test
    fun `a half rounds up at the displayed precision`() {
        assertEquals("6.3%", formatIncomeShare(share(1, 16), Locale.US))
    }

    @Test
    fun `the decimal separator and percent spacing follow the locale`() {
        assertEquals("33,3\u00a0%", formatIncomeShare(share(1, 3), Locale.forLanguageTag("es-ES")))
    }

    @Test
    fun `the share can be zero or more than one hundred percent`() {
        assertEquals("0%", formatIncomeShare(share(0, 2), Locale.US))
        assertEquals("150%", formatIncomeShare(share(3, 2), Locale.US))
    }

    @Test
    fun `formatting does not overflow when multiplying cents by one hundred would`() {
        assertEquals("150%", formatIncomeShare(share(9_000_000_000_000_000_000, 6_000_000_000_000_000_000), Locale.US))
    }

    private fun share(
        expenses: Long,
        income: Long,
    ) = FixedChargeIncomeShare(Money(expenses, "EUR"), Money(income, "EUR"))
}
