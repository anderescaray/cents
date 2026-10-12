package io.github.anderescaray.cents.ui.language

import java.util.Locale

/**
 * The languages the app can be shown in, chosen in Settings (`docs/next-improvements.md`, item 1).
 *
 * [SYSTEM] follows the phone, and is where every install starts. The others carry a bare
 * language, with no country. The app's words follow it; the currency and the way amounts and
 * dates are written do not, because both come from the phone (`deviceCurrency`, `formatLocale`).
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
