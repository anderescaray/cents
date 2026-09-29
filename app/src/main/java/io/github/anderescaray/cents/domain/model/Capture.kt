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
 * How capture from notifications behaves (PRD §4.6, D29): whether it is on, which apps it reads,
 * and the words that make a notification an expense or an income.
 *
 * The words are the owner's to edit. A list that was never edited is the default one, and a list
 * emptied on purpose stays empty, which is why "absent" and "empty" are kept apart in storage.
 */
data class CaptureSettings(
    val enabled: Boolean = false,
    val sources: List<CaptureSource> = emptyList(),
    val expenseWords: List<String> = DEFAULT_EXPENSE_WORDS,
    val incomeWords: List<String> = DEFAULT_INCOME_WORDS,
) {
    fun sourceFor(packageName: String): CaptureSource? = sources.firstOrNull { it.packageName == packageName }

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
