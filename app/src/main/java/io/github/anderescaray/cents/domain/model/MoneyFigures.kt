package io.github.anderescaray.cents.domain.model

/**
 * Which money figures Wallet and the top of Home show (`DECISIONS.md` D34): the cash and bank
 * accounts, what is invested, and the two together, each on or off.
 *
 * One choice for both screens. It starts with the two parts and no total, as the owner first
 * asked, and with none of them on, neither screen draws any.
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
}

/** One of the [MoneyFigures], switched on or off by itself. */
enum class MoneyFigure { NORMAL, INVESTED, TOTAL }
