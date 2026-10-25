package io.github.anderescaray.cents.domain.model

/**
 * Which money figures the top of Home shows (`DECISIONS.md` D34, D37): the cash and bank
 * accounts, what is invested, and the two together, each on or off.
 *
 * It starts with the two parts and no total, as the owner first asked, and with none of them on,
 * Home draws none. Wallet does not follow it: it always shows [ALL].
 */
data class MoneyFigures(
    val normal: Boolean = true,
    val invested: Boolean = true,
    val total: Boolean = false,
) {
    /** Whether there is anything to draw at all. */
    val any: Boolean get() = normal || invested || total

    fun isOn(figure: MoneyFigure): Boolean =
        when (figure) {
            MoneyFigure.NORMAL -> normal
            MoneyFigure.INVESTED -> invested
            MoneyFigure.TOTAL -> total
        }

    companion object {
        /** Every figure, which is what Wallet shows whatever Home was set to (`DECISIONS.md` D37). */
        val ALL = MoneyFigures(normal = true, invested = true, total = true)
    }
}

/** One of the [MoneyFigures], switched on or off by itself. */
enum class MoneyFigure { NORMAL, INVESTED, TOTAL }
