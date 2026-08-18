package io.github.anderescaray.cents.domain.model

/**
 * An amount of money: an exact integer of minor units (cents) plus its ISO-4217 currency.
 *
 * No `Double` comes near this type and no currency is implicit. Arithmetic is only allowed
 * between amounts in the same currency — mixing them is a programming error, not something
 * to paper over with a silent conversion. Formatting to text belongs to the UI layer,
 * which is the only place that knows the device locale.
 */
data class Money(
    val minorUnits: Long,
    val currency: String,
) {
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits + other.minorUnits)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits - other.minorUnits)
    }

    operator fun unaryMinus(): Money = copy(minorUnits = -minorUnits)

    val isNegative: Boolean get() = minorUnits < 0

    val isZero: Boolean get() = minorUnits == 0L

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot combine $currency with ${other.currency}: convert one of them first"
        }
    }

    companion object {
        fun zero(currency: String): Money = Money(0, currency)
    }
}
