package io.github.anderescaray.cents.domain.model

/**
 * One app whose notifications capture reads, and the account what it announces lands in
 * (`DECISIONS.md` D29).
 *
 * [label] is the app's name as the phone showed it when it was chosen, kept so Settings can name
 * it even after the app is uninstalled.
 */
data class CaptureSource(
    val packageName: String,
    val label: String,
    val accountId: Long,
)

/**
 * One app's own words, when the owner has edited them: null for a kind never edited for that app,
 * which then uses the starting words in [CaptureSettings].
 */
data class AppWords(
    val expense: List<String>? = null,
    val income: List<String>? = null,
) {
    fun of(kind: TransactionKind): List<String>? =
        when (kind) {
            TransactionKind.EXPENSE -> expense
            TransactionKind.INCOME -> income
        }
}

/**
 * How capture from notifications behaves (PRD §4.6, D29): whether it is on, which apps it reads,
 * and the words that make a notification an expense or an income.
 *
 * **The words are per app** (the owner, 2026-10-01): each bank writes its own way, so each chosen
 * app has its own expense list and its own income list. [expenseWords] and [incomeWords] are the
 * starting words — what an app uses until its own list is edited — and default to D29's lists.
 *
 * A list that was never edited is the starting one, and a list emptied on purpose stays empty,
 * which is why "absent" and "empty" are kept apart in storage.
 */
data class CaptureSettings(
    val enabled: Boolean = false,
    val sources: List<CaptureSource> = emptyList(),
    val expenseWords: List<String> = DEFAULT_EXPENSE_WORDS,
    val incomeWords: List<String> = DEFAULT_INCOME_WORDS,
    /** Each app's own lists, by package name. */
    val appWords: Map<String, AppWords> = emptyMap(),
    /**
     * A notification after each movement capture records. On unless turned off: the owner asked
     * to be told every time (2026-10-05), and a silent write is the one a wrong amount hides in.
     */
    val notifyOnCapture: Boolean = true,
) {
    fun sourceFor(packageName: String): CaptureSource? = sources.firstOrNull { it.packageName == packageName }

    /** The words that make [packageName]'s notification a [kind]: its own, or the starting ones. */
    fun wordsFor(
        packageName: String?,
        kind: TransactionKind,
    ): List<String> = packageName?.let { appWords[it]?.of(kind) } ?: startingWords(kind)

    /** Whether [packageName]'s [kind] list was edited for it, rather than inherited. */
    fun hasOwnWords(
        packageName: String,
        kind: TransactionKind,
    ): Boolean = appWords[packageName]?.of(kind) != null

    fun startingWords(kind: TransactionKind): List<String> =
        when (kind) {
            TransactionKind.EXPENSE -> expenseWords
            TransactionKind.INCOME -> incomeWords
        }

    companion object {
        /** D29's defaults, Spanish first because that is how the owner's bank writes. */
        val DEFAULT_EXPENSE_WORDS =
            listOf(
                "pago",
                "compra",
                "cargo",
                "has pagado",
                "retirada",
                "domiciliación",
                "payment",
                "purchase",
                "paid",
                "spent",
            )

        val DEFAULT_INCOME_WORDS =
            listOf(
                "ingreso",
                "recibido",
                "has recibido",
                "abono",
                "te han enviado",
                "nómina",
                "received",
                "deposit",
            )
    }
}

/** What became of one notification. */
sealed interface CaptureOutcome {
    /** Written down, as the movement with this id. */
    data class Recorded(
        val transactionId: Long,
        val amount: Money,
        val kind: TransactionKind,
        val accountName: String,
    ) : CaptureOutcome

    /** The same payment was already written in the same minute, so nothing new was. */
    data object Duplicate : CaptureOutcome

    /** Nothing was kept, for [reason]. */
    data class Ignored(
        val reason: IgnoreReason,
    ) : CaptureOutcome

    enum class IgnoreReason {
        /** Capture is switched off. */
        OFF,

        /** The app is not one the owner chose. */
        NOT_CHOSEN,

        /** The chosen app's account no longer exists, or is archived. */
        NO_ACCOUNT,

        /** Neither an expense word nor an income word: an advert, a security notice. */
        NO_WORD,

        /** A word, but no amount in the text. */
        NO_AMOUNT,

        /** An amount in another currency than the account's; there is no conversion (D2). */
        OTHER_CURRENCY,
    }
}
