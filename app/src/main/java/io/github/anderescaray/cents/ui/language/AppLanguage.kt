package io.github.anderescaray.cents.ui.language

import java.util.Locale

/**
 * The languages the app can be shown in, chosen in Settings (`docs/next-improvements.md`, item 1).
 *
 * [SYSTEM] follows the phone, and is where every install starts. The others carry a bare
 * language, with no country: the app's words and formats follow it, and money does not, because
 * the currency is read from the phone's region (`deviceCurrency`).
 */
enum class AppLanguage(
    /** The BCP 47 tag the app is set to, or null to follow the phone. */
    val tag: String?,
) {
    SYSTEM(null),
    ENGLISH("en"),
    SPANISH("es"),
    ;

    val locale: Locale? get() = tag?.let(Locale::forLanguageTag)

    companion object {
        /**
         * The choice a locale stands for. Matched by language alone, so `en-GB` set from the
         * phone's own per-app settings still reads as English; anything else follows the phone.
         */
        fun of(locale: Locale?): AppLanguage =
            entries.firstOrNull { it.tag != null && it.tag == locale?.language } ?: SYSTEM
    }
}
