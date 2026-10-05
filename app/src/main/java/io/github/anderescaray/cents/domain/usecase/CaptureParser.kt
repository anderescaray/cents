package io.github.anderescaray.cents.domain.usecase

import io.github.anderescaray.cents.domain.model.TransactionKind
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads a notification's text the way `DECISIONS.md` D29 says: the words decide which way the
 * money went, and the amount is the one written in it.
 *
 * Pure text in, answer out, with nothing from Android, so every rule here is a JVM test rather
 * than something only a phone can check. The part that *is* the phone's, which apps notify what,
 * is the one thing this cannot know, and why the owner's words are editable.
 */
object CaptureParser {
    /**
     * Which way the money went, by the words in the text.
     *
     * **An income word wins over an expense word.** *"Has recibido un pago de Ana"* holds *pago*:
     * money arriving is often described as a payment, and money leaving is rarely described as
     * *received* (D29).
     *
     * Matched ignoring case and accents, and as whole words or phrases: *cargo* does not match
     * *encargo*, and *ingreso* matches *INGRESO* and *íngreso* alike.
     */
    fun kindOf(
        text: String,
        expenseWords: Collection<String>,
        incomeWords: Collection<String>,
    ): TransactionKind? {
        val folded = fold(text)
        return when {
            incomeWords.any { it.appearsIn(folded) } -> TransactionKind.INCOME
            expenseWords.any { it.appearsIn(folded) } -> TransactionKind.EXPENSE
            else -> null
        }
    }

    /**
     * The amount in the text must name its currency: *23,40 €*, *€23.40*, or *EUR 1.234,56*.
     * Masked identifiers are excluded before matching. Multiple numbers with currency markers
     * are ambiguous, including a payment beside a balance, and nothing is guessed between them.
     * Two markers around the same number are allowed only when they name the same currency.
     * Bare decimals, dates and times never become an amount on their own.
     *
     * Read as digits rather than through a `Double`, because money never passes through binary
     * floating point here (`CLAUDE.md` rule 1).
     */
    fun amountIn(text: String): Amount? {
        val unmasked = MASKED_NUMBER.replace(text, "|")
        val candidates =
            (
                PREFIXED.findAll(unmasked).map { it.candidate(numberGroup = 2, currencyGroup = 1) } +
                    SUFFIXED.findAll(unmasked).map { it.candidate(numberGroup = 1, currencyGroup = 2) }
            ).groupBy { it.range }.values.singleOrNull() ?: return null
        val candidate = candidates.first()
        val trailingCurrency = CURRENCY_AFTER_NUMBER.matchAt(unmasked, candidate.range.last + 1)?.groupValues?.get(1)
        val currencies = candidates.map { it.currency } + listOfNotNull(trailingCurrency)
        val currency = currencies.map(::currencyCode).distinct().singleOrNull()
        return if (currency == null) null else toMinorUnits(candidate.number)?.let { Amount(it, currency) }
    }

    /** An amount found in the text, and the currency it explicitly named. */
    data class Amount(
        val minorUnits: Long,
        val currency: String,
    )

    private data class Candidate(
        val range: IntRange,
        val number: String,
        val currency: String,
    )

    private fun MatchResult.candidate(
        numberGroup: Int,
        currencyGroup: Int,
    ): Candidate {
        val number = requireNotNull(groups[numberGroup])
        return Candidate(number.range, number.value, groupValues[currencyGroup])
    }

    private fun String.appearsIn(folded: String): Boolean =
        WORD_PATTERNS
            .getOrPut(this) {
                val word = fold(this).trim()
                Regex("(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])").takeIf { word.isNotEmpty() }
                    ?: NEVER
            }.containsMatchIn(folded)

    /**
     * Lower case, accents taken off, runs of spaces made one: the form every word is compared in.
     * Public so the settings that edit the words judge a duplicate exactly the way this reads one.
     */
    fun fold(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(SPACES, " ")

    /**
     * *1.234,56* and *1,234.56* are the same amount: the last separator followed by one or two
     * digits is the decimal one, and every other separator only groups thousands.
     */
    private fun toMinorUnits(number: String): Long? {
        val decimal = DECIMAL_TAIL.find(number)
        val whole = (decimal?.let { number.substring(0, it.range.first) } ?: number).filter(Char::isDigit)
        val cents = decimal?.groupValues?.get(1)?.padEnd(2, '0') ?: "00"
        val digits = (whole.ifEmpty { "0" } + cents).trimStart('0')
        if (digits.length > MAX_DIGITS) return null
        return digits.ifEmpty { "0" }.toLong().takeIf { it > 0 }
    }

    /**
     * The ISO code a currency sign or word stands for. A bare *$* names no currency, because it
     * is the sign of the peso and of several dollars as well as the US one, and reading it as
     * USD would turn every amount of a Mexican or Canadian account into a foreign one.
     */
    private fun currencyCode(token: String): String? =
        when (token.lowercase(Locale.ROOT).replace(" ", "")) {
            "€", "eur", "euro", "euros" -> "EUR"
            "us$", "usd" -> "USD"
            "£", "gbp" -> "GBP"
            "$" -> null
            else -> token.uppercase(Locale.ROOT)
        }

    /** A number, with thousands grouped by dots, commas or spaces, and up to two decimals. */
    private const val NUMBER = "\\d{1,3}(?:[.,\\u00A0\\u202F ]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?"

    /** Before a number only a sign or a code: *Euro 6000*, the cash machine network, is not money. */
    private const val PREFIX_CURRENCY = "€|US\\$|\\$|£|(?i:eur|usd|gbp)"
    private const val SUFFIX_CURRENCY = "€|\\$|£|(?i:eur|usd|gbp|euros?)"

    /** Never inside a longer number, so the *1234* of a card cannot become the start of an amount. */
    private const val NOT_AFTER_A_NUMBER = "(?<![\\p{L}\\p{N}.,])"

    /** Not followed by more digits, or by a separator and a digit, as a date or a time would be. */
    private const val NOT_BEFORE_A_NUMBER = "(?![\\d]|[.,]\\d)"

    private val PREFIXED =
        Regex("(?<![\\p{L}\\p{N}])($PREFIX_CURRENCY)\\h*($NUMBER)$NOT_BEFORE_A_NUMBER")
    private val SUFFIXED =
        Regex("$NOT_AFTER_A_NUMBER($NUMBER)\\h*($SUFFIX_CURRENCY)(?![\\p{L}\\p{N}])")
    private val CURRENCY_AFTER_NUMBER = Regex("\\h*($SUFFIX_CURRENCY)(?![\\p{L}\\p{N}])")
    private val MASKED_NUMBER =
        Regex(
            "(?:(?<![\\p{L}\\p{N}])[xX]|[*\\u2022\\u25CF\\u00B7\\u2026]|\\.{3,})" +
                "[\\h*xX\\u2022\\u25CF\\u00B7\\u2026.-]*\\d+(?:[\\h-]+\\d+(?![\\d.,]))*",
        )
    private val DECIMAL_TAIL = Regex("[.,](\\d{1,2})$")
    private val MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")
    private val NEVER = Regex("(?!)")

    /** Each word's pattern, built once rather than for every notification of every app. */
    private val WORD_PATTERNS = ConcurrentHashMap<String, Regex>()

    /** Fifteen digits is a trillion euros in cents; anything longer is not a payment. */
    private const val MAX_DIGITS = 15
}
