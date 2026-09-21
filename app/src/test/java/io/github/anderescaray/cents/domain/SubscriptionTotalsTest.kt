package io.github.anderescaray.cents.domain

import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.usecase.IncomeShareUnavailableReason
import io.github.anderescaray.cents.domain.usecase.subscriptionTotalsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SubscriptionTotalsTest {
    @Test
    fun `every remaining weekly occurrence counts including today and the last day`() {
        val rule = rule().copy(frequency = Frequency.WEEKLY, startDate = LocalDate.of(2026, 9, 2))

        assertEquals(Money(3_000, "EUR"), totals(rule, today = LocalDate.of(2026, 9, 16)).committedThisMonth)
        assertEquals(Money(2_000, "EUR"), totals(rule, today = LocalDate.of(2026, 9, 17)).committedThisMonth)
    }

    @Test
    fun `the first day of a new month starts a new window`() {
        val daily = rule().copy(frequency = Frequency.EVERY_N_DAYS, intervalDays = 1)

        assertEquals(Money(1_000, "EUR"), totals(daily, today = LocalDate.of(2026, 9, 30)).committedThisMonth)
        assertEquals(Money(31_000, "EUR"), totals(daily, today = LocalDate.of(2026, 10, 1)).committedThisMonth)
    }

    @Test
    fun `December never includes a charge from January`() {
        val december = rule().copy(startDate = LocalDate.of(2026, 12, 31))
        val january = rule().copy(id = 2, startDate = LocalDate.of(2027, 1, 1))

        assertEquals(
            Money(1_000, "EUR"),
            subscriptionTotalsOf(listOf(december, january), LocalDate.of(2026, 12, 31)).committedThisMonth,
        )
    }

    @Test
    fun `the 31st clamps in February and returns in March`() {
        val january = rule().copy(startDate = LocalDate.of(2026, 1, 31), lastGenerated = LocalDate.of(2026, 1, 31))
        val february = january.copy(lastGenerated = LocalDate.of(2026, 2, 28))

        assertEquals(Money(1_000, "EUR"), totals(january, today = LocalDate.of(2026, 2, 28)).committedThisMonth)
        assertEquals(Money(1_000, "EUR"), totals(february, today = LocalDate.of(2026, 3, 29)).committedThisMonth)
        assertEquals(
            Money.zero("EUR"),
            totals(
                february.copy(endDate = LocalDate.of(2026, 3, 30)),
                today = LocalDate.of(2026, 3, 1),
            ).committedThisMonth,
        )
    }

    @Test
    fun `a leap day yearly charge keeps its anchor`() {
        val leapDay = rule().copy(frequency = Frequency.YEARLY, startDate = LocalDate.of(2024, 2, 29))

        assertEquals(Money(1_000, "EUR"), totals(leapDay, today = LocalDate.of(2026, 2, 28)).committedThisMonth)
        assertEquals(Money(1_000, "EUR"), totals(leapDay, today = LocalDate.of(2028, 2, 29)).committedThisMonth)
    }

    @Test
    fun `quarterly and fortnightly rules count actual dates rather than monthly averages`() {
        val quarterly = rule().copy(frequency = Frequency.QUARTERLY, startDate = LocalDate.of(2026, 1, 31))
        val fortnightly = rule().copy(frequency = Frequency.FORTNIGHTLY)

        assertEquals(Money(1_000, "EUR"), totals(quarterly, today = LocalDate.of(2026, 4, 30)).committedThisMonth)
        assertEquals(Money.zero("EUR"), totals(quarterly, today = LocalDate.of(2026, 5, 1)).committedThisMonth)
        assertEquals(Money(1_000, "EUR"), totals(fortnightly, today = LocalDate.of(2026, 9, 17)).committedThisMonth)
    }

    @Test
    fun `every N days stays anchored across month boundaries`() {
        val rule =
            rule().copy(
                frequency = Frequency.EVERY_N_DAYS,
                intervalDays = 10,
                startDate = LocalDate.of(2026, 8, 29),
            )

        assertEquals(Money(3_000, "EUR"), totals(rule, today = LocalDate.of(2026, 9, 1)).committedThisMonth)
        assertEquals(Money.zero("EUR"), totals(rule, today = LocalDate.of(2026, 9, 29)).committedThisMonth)
    }

    @Test
    fun `a missing or nonpositive day interval commits nothing`() {
        val rule = rule().copy(frequency = Frequency.EVERY_N_DAYS)

        listOf(null, 0, -1).forEach { interval ->
            assertEquals(Money.zero("EUR"), totals(rule.copy(intervalDays = interval)).committedThisMonth)
        }
    }

    @Test
    fun `an occurrence already generated today is excluded without losing later ones`() {
        val weekly =
            rule().copy(
                frequency = Frequency.WEEKLY,
                startDate = LocalDate.of(2026, 9, 2),
                lastGenerated = LocalDate.of(2026, 9, 16),
            )

        assertEquals(Money(2_000, "EUR"), totals(weekly, today = LocalDate.of(2026, 9, 16)).committedThisMonth)
    }

    @Test
    fun `a watermark ahead of today does not count already recorded future dates`() {
        val rule = rule().copy(frequency = Frequency.WEEKLY, lastGenerated = LocalDate.of(2026, 10, 1))

        assertEquals(Money.zero("EUR"), totals(rule).committedThisMonth)
    }

    @Test
    fun `an end date is inclusive and nothing beyond it counts`() {
        val weekly = rule().copy(frequency = Frequency.WEEKLY, endDate = LocalDate.of(2026, 9, 22))

        assertEquals(Money(1_000, "EUR"), totals(weekly).committedThisMonth)
        assertEquals(Money.zero("EUR"), totals(weekly.copy(endDate = LocalDate.of(2026, 9, 21))).committedThisMonth)
    }

    @Test
    fun `stopped rules and income never count as committed spending`() {
        val stopped = rule().copy(active = false, mode = RuleMode.CONFIRM)
        val income = rule().copy(id = 2, kind = TransactionKind.INCOME, mode = RuleMode.CONFIRM)
        val totals = subscriptionTotalsOf(listOf(stopped, income), LocalDate.of(2026, 9, 17))

        assertEquals(Money.zero("EUR"), totals.committedThisMonth)
        assertFalse(totals.committedIncludesEstimates)
        assertEquals(Money.zero("EUR"), totals.monthlyOutgoing)
    }

    @Test
    fun `unrecorded fixed charges before today are not future commitments`() {
        assertEquals(Money.zero("EUR"), totals(rule()).committedThisMonth)
    }

    @Test
    fun `every waiting variable occurrence is estimated even if it is from an earlier month`() {
        val variable =
            rule().copy(
                mode = RuleMode.CONFIRM,
                startDate = LocalDate.of(2026, 7, 15),
                lastGenerated = LocalDate.of(2026, 7, 15),
            )
        val totals = totals(variable)

        assertEquals(Money(2_000, "EUR"), totals.committedThisMonth)
        assertTrue(totals.committedIncludesEstimates)
    }

    @Test
    fun `waiting and future variable occurrences meet at today without double counting`() {
        val variable =
            rule().copy(
                mode = RuleMode.CONFIRM,
                frequency = Frequency.WEEKLY,
                startDate = LocalDate.of(2026, 9, 2),
                lastGenerated = LocalDate.of(2026, 9, 2),
            )
        val totals = totals(variable, today = LocalDate.of(2026, 9, 16))

        assertEquals(Money(4_000, "EUR"), totals.committedThisMonth)
        assertTrue(totals.committedIncludesEstimates)
    }

    @Test
    fun `answering or skipping a variable bill removes it from the estimate`() {
        val variable = rule().copy(mode = RuleMode.CONFIRM, lastGenerated = LocalDate.of(2026, 9, 1))
        val totals = totals(variable)

        assertEquals(Money.zero("EUR"), totals.committedThisMonth)
        assertFalse(totals.committedIncludesEstimates)
        assertTrue(totals.includesEstimates)
    }

    @Test
    fun `an ended active variable rule still includes its waiting bills`() {
        val variable =
            rule().copy(
                mode = RuleMode.CONFIRM,
                startDate = LocalDate.of(2026, 8, 1),
                endDate = LocalDate.of(2026, 8, 1),
            )

        assertEquals(Money(1_000, "EUR"), totals(variable).committedThisMonth)
    }

    @Test
    fun `a future variable occurrence is an estimate but one outside the month is not counted`() {
        val variable = rule().copy(mode = RuleMode.CONFIRM, startDate = LocalDate.of(2026, 9, 30))
        val future = variable.copy(startDate = LocalDate.of(2026, 10, 1))

        assertEquals(Money(1_000, "EUR"), totals(variable).committedThisMonth)
        assertTrue(totals(variable).committedIncludesEstimates)
        assertEquals(Money.zero("EUR"), totals(future).committedThisMonth)
        assertFalse(totals(future).committedIncludesEstimates)
    }

    @Test
    fun `income share uses monthly costs for both sides and keeps an exact fraction`() {
        val expense = rule().copy(amount = Money(30_000, "EUR"), frequency = Frequency.QUARTERLY)
        val otherExpense = rule().copy(id = 2, amount = Money(40_000, "EUR"))
        val income =
            rule().copy(
                id = 3,
                kind = TransactionKind.INCOME,
                amount = Money(3_000_000, "EUR"),
                frequency = Frequency.YEARLY,
            )
        val totals = subscriptionTotalsOf(listOf(expense, otherExpense, income), LocalDate.of(2026, 9, 17))
        val share = requireNotNull(totals.incomeShare)

        assertEquals(Money(50_000, "EUR"), share.monthlyExpenses)
        assertEquals(Money(250_000, "EUR"), share.monthlyIncome)
        assertEquals(totals.monthlyOutgoing, share.monthlyExpenses)
        assertNull(totals.incomeShareUnavailableReason)
        assertFalse(share.includesEstimates)
    }

    @Test
    fun `stopped income does not supply a denominator`() {
        val income = rule().copy(kind = TransactionKind.INCOME, active = false)
        val totals = subscriptionTotalsOf(listOf(rule(), income), LocalDate.of(2026, 9, 17))

        assertNull(totals.incomeShare)
        assertEquals(IncomeShareUnavailableReason.NO_INCOME_RULES, totals.incomeShareUnavailableReason)
    }

    @Test
    fun `no income rules means an unavailable share rather than zero percent`() {
        val totals = totals(rule())

        assertNull(totals.incomeShare)
        assertEquals(IncomeShareUnavailableReason.NO_INCOME_RULES, totals.incomeShareUnavailableReason)
    }

    @Test
    fun `income rounding to zero cannot be a denominator`() {
        val income = rule().copy(kind = TransactionKind.INCOME, amount = Money(1, "EUR"), frequency = Frequency.YEARLY)
        val totals = subscriptionTotalsOf(listOf(rule(), income), LocalDate.of(2026, 9, 17))

        assertNull(totals.incomeShare)
        assertEquals(IncomeShareUnavailableReason.ZERO_MONTHLY_INCOME, totals.incomeShareUnavailableReason)
    }

    @Test
    fun `an income with no expenses has a real zero share`() {
        val income = rule().copy(kind = TransactionKind.INCOME)
        val share = requireNotNull(totals(income).incomeShare)

        assertEquals(Money.zero("EUR"), share.monthlyExpenses)
        assertEquals(Money(1_000, "EUR"), share.monthlyIncome)
    }

    @Test
    fun `stopped expenses do not affect the monthly share`() {
        val income = rule().copy(id = 2, kind = TransactionKind.INCOME, amount = Money(2_000, "EUR"))
        val stopped = rule().copy(id = 3, active = false, mode = RuleMode.CONFIRM)
        val share =
            requireNotNull(
                subscriptionTotalsOf(listOf(rule(), income, stopped), LocalDate.of(2026, 9, 17)).incomeShare,
            )

        assertEquals(Money(1_000, "EUR"), share.monthlyExpenses)
        assertFalse(share.includesEstimates)
    }

    @Test
    fun `variable amounts on either side mark the income share as estimated`() {
        val income = rule().copy(id = 2, kind = TransactionKind.INCOME)
        listOf(
            listOf(rule().copy(mode = RuleMode.CONFIRM), income),
            listOf(rule(), income.copy(mode = RuleMode.CONFIRM)),
        ).forEach { rules ->
            val share = requireNotNull(subscriptionTotalsOf(rules, LocalDate.of(2026, 9, 17)).incomeShare)
            assertTrue(share.includesEstimates)
        }
    }

    @Test
    fun `large amounts stay exact and expenses may exceed income`() {
        val expense = rule().copy(amount = Money(300_000_000_000_000_001, "EUR"))
        val income = rule().copy(id = 2, kind = TransactionKind.INCOME, amount = Money(200_000_000_000_000_000, "EUR"))
        val share =
            requireNotNull(subscriptionTotalsOf(listOf(expense, income), LocalDate.of(2026, 9, 17)).incomeShare)

        assertEquals(expense.amount, share.monthlyExpenses)
        assertEquals(income.amount, share.monthlyIncome)
    }

    @Test
    fun `different expense currencies cannot be totalled`() {
        val other = rule().copy(id = 2, amount = Money(1_000, "USD"))
        val totals = subscriptionTotalsOf(listOf(rule(), other), LocalDate.of(2026, 9, 1))

        assertNull(totals.committedThisMonth)
        assertNull(totals.monthlyOutgoing)
    }

    @Test
    fun `income and expenses in different currencies cannot make a share`() {
        val income = rule().copy(id = 2, kind = TransactionKind.INCOME, amount = Money(1_000, "USD"))
        val totals = subscriptionTotalsOf(listOf(rule(), income), LocalDate.of(2026, 9, 1))

        assertEquals(Money(1_000, "EUR"), totals.committedThisMonth)
        assertNull(totals.incomeShare)
        assertEquals(IncomeShareUnavailableReason.MIXED_CURRENCIES, totals.incomeShareUnavailableReason)
    }

    private fun totals(
        rule: RecurringRule,
        today: LocalDate = LocalDate.of(2026, 9, 17),
    ) = subscriptionTotalsOf(listOf(rule), today)

    private fun rule() =
        RecurringRule(
            id = 1,
            name = "Subscription",
            accountId = 1,
            categoryId = null,
            kind = TransactionKind.EXPENSE,
            amount = Money(1_000, "EUR"),
            frequency = Frequency.MONTHLY,
            intervalDays = null,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = null,
            mode = RuleMode.AUTOMATIC,
            active = true,
            lastGenerated = null,
        )
}
