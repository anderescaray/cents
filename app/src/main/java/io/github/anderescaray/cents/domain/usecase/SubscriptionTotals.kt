package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.LocalDate
import java.time.YearMonth

data class SubscriptionTotals(
    val monthlyOutgoing: Money? = null,
    val yearlyOutgoing: Money? = null,
    val includesEstimates: Boolean = false,
    val committedThisMonth: Money? = null,
    val committedIncludesEstimates: Boolean = false,
    val incomeShare: FixedChargeIncomeShare? = null,
    val incomeShareUnavailableReason: IncomeShareUnavailableReason? = IncomeShareUnavailableReason.NO_INCOME_RULES,
)

data class FixedChargeIncomeShare(
    val monthlyExpenses: Money,
    val monthlyIncome: Money,
    val includesEstimates: Boolean = false,
) {
    init {
        require(monthlyExpenses.currency == monthlyIncome.currency)
        require(monthlyIncome.minorUnits > 0)
    }
}

enum class IncomeShareUnavailableReason {
    NO_INCOME_RULES,
    ZERO_MONTHLY_INCOME,
    MIXED_CURRENCIES,
}

fun subscriptionTotalsOf(
    rules: List<RecurringRule>,
    today: LocalDate,
): SubscriptionTotals {
    val active = rules.filter { it.active }
    val expenses = active.filter { it.kind == TransactionKind.EXPENSE }
    val income = active.filter { it.kind == TransactionKind.INCOME }
    // Only an empty side falls back to this, and an empty sum is zero in any currency — so it
    // needs some rule's currency, not one they all share. Requiring agreement here made two
    // incomes in different currencies turn "no charges" into "cannot be added up".
    val currency =
        active
            .ifEmpty { rules }
            .firstOrNull()
            ?.amount
            ?.currency
    val monthlyOutgoing = expenses.map { it.monthlyCost }.total(currency)
    val monthlyIncome = income.map { it.monthlyCost }.total(currency)
    val commitments = expenses.map { it to it.committedOccurrenceCount(today) }
    val unavailable =
        when {
            income.isEmpty() -> {
                IncomeShareUnavailableReason.NO_INCOME_RULES
            }

            monthlyOutgoing == null || monthlyIncome == null || monthlyOutgoing.currency != monthlyIncome.currency -> {
                IncomeShareUnavailableReason.MIXED_CURRENCIES
            }

            monthlyIncome.minorUnits <= 0 -> {
                IncomeShareUnavailableReason.ZERO_MONTHLY_INCOME
            }

            else -> {
                null
            }
        }
    return SubscriptionTotals(
        monthlyOutgoing = monthlyOutgoing,
        yearlyOutgoing = expenses.map { it.yearlyCost }.total(currency),
        includesEstimates = expenses.any { it.mode == RuleMode.CONFIRM },
        committedThisMonth =
            commitments
                .map { (rule, count) ->
                    Money(Math.multiplyExact(rule.amount.minorUnits, count), rule.amount.currency)
                }.total(currency),
        committedIncludesEstimates = commitments.any { (rule, count) -> count > 0 && rule.mode == RuleMode.CONFIRM },
        incomeShare =
            if (unavailable == null) {
                FixedChargeIncomeShare(
                    monthlyExpenses = requireNotNull(monthlyOutgoing),
                    monthlyIncome = requireNotNull(monthlyIncome),
                    includesEstimates = active.any { it.mode == RuleMode.CONFIRM },
                )
            } else {
                null
            },
        incomeShareUnavailableReason = unavailable,
    )
}

/**
 * What this rule is still known to take before the month ends, counted the way *committed this
 * month* on the Subscriptions screen counts it — so a budget and that screen can never disagree
 * about the same rent.
 */
fun RecurringRule.committedThisMonth(today: LocalDate): Money =
    Money(Math.multiplyExact(amount.minorUnits, committedOccurrenceCount(today)), amount.currency)

private fun RecurringRule.committedOccurrenceCount(today: LocalDate): Long {
    val month = YearMonth.from(today)
    val remaining =
        (today.dayOfMonth..month.lengthOfMonth()).count { day ->
            val date = month.atDay(day)
            (lastGenerated == null || date > lastGenerated) && occursOn(date)
        }
    val waiting = if (mode == RuleMode.CONFIRM) dueOccurrences(today.minusDays(1)).size else 0
    return remaining.toLong() + waiting
}

private fun List<Money>.total(fallbackCurrency: String?): Money? {
    val currency = if (isEmpty()) fallbackCurrency else map { it.currency }.distinct().singleOrNull()
    return currency?.let { Money(fold(0L) { sum, amount -> Math.addExact(sum, amount.minorUnits) }, it) }
}
