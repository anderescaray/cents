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
 *
 * Over detekt's function limit on purpose: every rule of reading lives in this one object, so the
 * listener and *Try a notification* cannot read a notification two different ways.
 */
@Suppress("TooManyFunctions")
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
    ): TransactionKind? = wordIn(text, expenseWords, incomeWords)?.kind

    /** [kindOf], and which of the owner's words decided it, for a screen that explains it. */
    fun wordIn(
        text: String,
        expenseWords: Collection<String>,
        incomeWords: Collection<String>,
    ): WordMatch? {
        val folded = fold(text)
        return incomeWords.firstOrNull { it.appearsIn(folded) }?.let { WordMatch(TransactionKind.INCOME, it) }
            ?: expenseWords.firstOrNull { it.appearsIn(folded) }?.let { WordMatch(TransactionKind.EXPENSE, it) }
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
    fun amountIn(
        text: String,
        ignoreWords: Collection<String> = emptyList(),
    ): Amount? = (readAmount(text, ignoreWords) as? AmountReading.One)?.amount

    /**
     * [amountIn], saying why when there is no single amount.
     *
     * **An amount led in by one of [ignoreWords] is skipped** before anything else is decided:
     * *Pago de 23,40 €. Saldo disponible: 1.502,10 €* is then one amount, the payment, rather than
     * two and nothing. "Led in" means among the last few words before the number in the same
     * sentence, so *saldo* early in a long notification cannot hide the payment after it.
     */
    fun readAmount(
        text: String,
        ignoreWords: Collection<String> = emptyList(),
    ): AmountReading {
        // The body, then the title when a notification has one (see [TITLE_SEPARATOR]).
        val parts = text.split(TITLE_SEPARATOR)
        val body = amountsIn(parts.first(), ignoreWords)
        // **A title that repeats the body's amount repeats the payment**: *23,40 €* over
        // *Compra por 23,40 € en Amazon* is one payment, not two. A different amount in the title
        // is still a second amount, and two in the body are still two, so a fee beside a payment,
        // or two purchases of the same price, are not guessed between.
        val title = parts.drop(1).flatMap { amountsIn(it, ignoreWords) }.filterNot { it != null && it in body }
        val amounts = body + title
        return when (amounts.size) {
            0 -> AmountReading.None
            1 -> amounts.single()?.let { AmountReading.One(it) } ?: AmountReading.None
            else -> AmountReading.Several(amounts.filterNotNull())
        }
    }

    /**
     * Each number in [text] that names a currency, in order, as an amount, or null where its
     * markers disagree or its digits do not read. Numbers led in by an ignore word are left out.
     */
    private fun amountsIn(
        text: String,
        ignoreWords: Collection<String>,
    ): List<Amount?> {
        val unmasked = MASKED_NUMBER.replace(text, "|")
        val numbers =
            (
                PREFIXED.findAll(unmasked).map { it.candidate(numberGroup = 2, currencyGroup = 1) } +
                    SUFFIXED.findAll(unmasked).map { it.candidate(numberGroup = 1, currencyGroup = 2) }
            ).groupBy { it.range }
                .values
                .sortedBy { it.first().range.first }
        return numbers
            .filterIndexed { index, markers ->
                val from =
                    numbers
                        .getOrNull(index - 1)
                        ?.first()
                        ?.range
                        ?.last
                        ?.plus(1) ?: 0
                !ledInByIgnoreWord(unmasked.substring(from, markers.first().range.first), ignoreWords)
            }.map { amountOf(unmasked, it) }
    }

    /**
     * Where a notification's body ends and its title begins, in the text capture reads.
     *
     * A control character no app writes, so the parser can tell the two apart: a title often
     * repeats the body's amount, and only knowing which is which lets that count once. Shown as a
     * space wherever the text is shown ([readable]).
     */
    const val TITLE_SEPARATOR = '\u001F'

    /** The text as a person reads it: body and title separated by a space. */
    fun readable(text: String): String = text.replace(TITLE_SEPARATOR, ' ')

    /**
     * The whole reading of one notification, word and amount, the way capture does it.
     *
     * The one function both the listener's recorder and the screen that tries a pasted text call,
     * so what the screen says would happen is what happens. The account's currency is checked
     * after this, by whoever knows the account.
     */
    fun read(
        text: String,
        expenseWords: Collection<String>,
        incomeWords: Collection<String>,
        ignoreWords: Collection<String>,
    ): Reading {
        val word = wordIn(text, expenseWords, incomeWords) ?: return Reading.NoWord
        return when (val amount = readAmount(text, ignoreWords)) {
            is AmountReading.One -> Reading.Movement(word, amount.amount)
            AmountReading.None -> Reading.NoAmount(word)
            is AmountReading.Several -> Reading.SeveralAmounts(word, amount.amounts)
        }
    }

    /** An amount found in the text, and the currency it explicitly named. */
    data class Amount(
        val minorUnits: Long,
        val currency: String,
    )

    /** The owner's word that decided which way the money went. */
    data class WordMatch(
        val kind: TransactionKind,
        val word: String,
    )

    /** What [readAmount] found. */
    sealed interface AmountReading {
        data class One(
            val amount: Amount,
        ) : AmountReading

        /** No number in the text names a currency, once masked and skipped ones are set aside. */
        data object None : AmountReading

        /** More than one does, and nothing is guessed between them. */
        data class Several(
            val amounts: List<Amount>,
        ) : AmountReading
    }

    /** What [read] found. */
    sealed interface Reading {
        /** A movement capture would write, in the account's currency or not. */
        data class Movement(
            val word: WordMatch,
            val amount: Amount,
        ) : Reading

        /** Neither an expense word nor an income word. */
        data object NoWord : Reading

        data class NoAmount(
            val word: WordMatch,
        ) : Reading

        /** A word, and more than one amount: which is the payment is not guessed. */
        data class SeveralAmounts(
            val word: WordMatch,
            val amounts: List<Amount>,
        ) : Reading
    }

    private data class Candidate(
        val range: IntRange,
        val number: String,
        val currency: String,
    )

    /** One number's markers as one amount, when they agree on a currency and the digits read. */
    private fun amountOf(
        text: String,
        markers: List<Candidate>,
    ): Amount? {
        val candidate = markers.first()
        val trailingCurrency = CURRENCY_AFTER_NUMBER.matchAt(text, candidate.range.last + 1)?.groupValues?.get(1)
        val currencies = markers.map { it.currency } + listOfNotNull(trailingCurrency)
        val currency = currencies.map(::currencyCode).distinct().singleOrNull() ?: return null
        return toMinorUnits(candidate.number)?.let { Amount(it, currency) }
    }

    /** Whether one of [ignoreWords] is among the last words before a number, in its sentence. */
    private fun ledInByIgnoreWord(
        leadIn: String,
        ignoreWords: Collection<String>,
    ): Boolean {
        if (ignoreWords.isEmpty()) return false
        val words =
            fold(SENTENCE_END.split(leadIn).last())
                .split(NOT_A_LETTER)
                .filter(String::isNotEmpty)
                .takeLast(LEAD_IN_WORDS)
                .joinToString(" ")
        return ignoreWords.any { it.appearsIn(words) }
    }

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

    /** A full stop, question mark or semicolon followed by a space, or a new line. Not *1.502*. */
    private val SENTENCE_END = Regex("[.!?;]\\s+|\\n")
    private val NOT_A_LETTER = Regex("[^\\p{L}]+")

    /** *Tu saldo actual es de 1.000 €*: five words reach back to *saldo*. */
    private const val LEAD_IN_WORDS = 5
    private val MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")
    private val NEVER = Regex("(?!)")

    /** Each word's pattern, built once rather than for every notification of every app. */
    private val WORD_PATTERNS = ConcurrentHashMap<String, Regex>()

    /** Fifteen digits is a trillion euros in cents; anything longer is not a payment. */
    private const val MAX_DIGITS = 15
}
