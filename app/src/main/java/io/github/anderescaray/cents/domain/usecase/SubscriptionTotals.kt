package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Postponement
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

/**
 * @param postponements every occurrence moved to another day (D28). A charge counts in the month
 * of the day it is **recorded** on, which is the day it was moved to: rent moved from the 30th to
 * the 2nd is next month's, and one moved in from last month is this month's.
 */
fun subscriptionTotalsOf(
    rules: List<RecurringRule>,
    today: LocalDate,
    postponements: List<Postponement>,
): SubscriptionTotals {
    val moved = postponements.byRule()
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
    val commitments = expenses.map { it to it.committedOccurrenceCount(today, moved[it.id].orEmpty()) }
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
 * What this rule is still known to take **in this month's books** before the month ends — what a
 * budget adds to what was spent.
 *
 * The same count as *committed this month* on the Subscriptions screen, with one difference, and
 * it is deliberate: a variable bill still waiting from an **earlier** month is left out. That
 * screen counts it because it is money still owed; but it will be recorded on its own due date,
 * in its own month (D23), so it is no part of this month's budget, unless it was moved into this
 * month, when it is recorded on the day it was moved to (D28). Counting it here made a budget
 * turn red over last month's electricity.
 *
 * [moved] is this rule's postponed occurrences, each against the day it was moved to. Each one
 * counts in the month of that day, because that is the day the movement is dated.
 */
fun RecurringRule.committedThisMonth(
    today: LocalDate,
    moved: Map<LocalDate, LocalDate>,
): Money =
    Money(
        Math.multiplyExact(amount.minorUnits, committedOccurrenceCount(today, moved, earlierMonthsToo = false)),
        amount.currency,
    )

/** Every rule's postponements, as rule id → (occurrence → the day it was moved to). */
fun List<Postponement>.byRule(): Map<Long, Map<LocalDate, LocalDate>> =
    groupBy { it.ruleId }.mapValues { (_, rows) -> rows.associate { it.occurrence to it.postponedTo } }

/**
 * How many of this rule's occurrences are still to be recorded this month.
 *
 * Three kinds, and none counted twice:
 * - one on its own day, from today to the end of the month, that has not been moved;
 * - one **moved** to a day from today to the end of the month, wherever it started: from last
 *   month, or from earlier this month;
 * - for a variable bill, one already waiting, dated the day it will be recorded on, whether that
 *   is its own day or the one it was moved to, if that day is already past.
 */
private fun RecurringRule.committedOccurrenceCount(
    today: LocalDate,
    moved: Map<LocalDate, LocalDate>,
    earlierMonthsToo: Boolean = true,
): Long {
    val month = YearMonth.from(today)
    val ahead = today..month.atEndOfMonth()

    fun unwritten(date: LocalDate) = lastGenerated == null || date > lastGenerated
    val onTheirOwnDay =
        (today.dayOfMonth..month.lengthOfMonth()).count { day ->
            val date = month.atDay(day)
            unwritten(date) && occursOn(date) && date !in moved
        }
    val movedHere = moved.count { (occurrence, to) -> unwritten(occurrence) && occursOn(occurrence) && to in ahead }
    val waiting =
        if (mode == RuleMode.CONFIRM) {
            dueOccurrences(today.minusDays(1)).count { occurrence ->
                val recordedOn = moved[occurrence] ?: occurrence
                recordedOn < today && (earlierMonthsToo || YearMonth.from(recordedOn) == month)
            }
        } else {
            0
        }
    return onTheirOwnDay.toLong() + movedHere + waiting
}

private fun List<Money>.total(fallbackCurrency: String?): Money? {
    val currency = if (isEmpty()) fallbackCurrency else map { it.currency }.distinct().singleOrNull()
    return currency?.let { Money(fold(0L) { sum, amount -> Math.addExact(sum, amount.minorUnits) }, it) }
}
