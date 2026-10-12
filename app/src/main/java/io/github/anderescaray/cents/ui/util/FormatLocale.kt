package io.github.anderescaray.cents.ui.util

import io.github.anderescaray.cents.ui.language.PhoneLocale
import java.util.Locale

/**
 * The locale amounts and dates are written in: the phone's, whatever language the app is shown in
 * (`DECISIONS.md` D36). English chosen on a Spanish phone reads `1.234,56 €` and `12 oct`; the
 * language changes the words only. Every formatter that would read `Locale.getDefault()` reads this
 * instead, because from Android 13 the default follows the app's language.
 */
fun formatLocale(): Locale = PhoneLocale.get()
