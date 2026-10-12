package io.github.anderescaray.cents.ui.language

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Where the app's own language is kept, and how it reaches every screen and notification.
 *
 * **From Android 13 the system keeps it.** [LocaleManager] is Android's per-app language: the
 * system applies it to the whole app, notifications included, recreates what is on screen, and
 * shows the same choice under the phone's *App languages* (`res/xml/locales_config.xml`).
 *
 * **Before Android 13 the app keeps it itself**, in a `SharedPreferences` file rather than the
 * app's DataStore, because it is read synchronously in `attachBaseContext`, before injection and
 * before the first frame. [applyTo] puts it on the activity and on the notifiers' context, the
 * only two places text is made; the default locale is set to it, as Android 13 sets it. The screen
 * that changes it recreates the activity ([appliedBySystem]).
 *
 * Either way the language changes words only. The currency and the way amounts and dates are
 * written come from [PhoneLocale] (D36).
 */
object AppLanguages {
    /** Whether the system applies the language and recreates the screens itself. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val appliedBySystem: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Run once when the process starts, before anything is drawn. */
    fun onAppStart(context: Context) {
        if (!appliedBySystem) setDefault(stored(context))
    }

    /**
     * Run when the phone's configuration changes. Before Android 13 the system then resets the
     * default locale to the phone's; this puts the chosen language back, as 13 keeps it.
     */
    fun onConfigurationChanged(context: Context) {
        if (!appliedBySystem) setDefault(stored(context))
    }

    fun current(context: Context): AppLanguage =
        if (appliedBySystem) {
            AppLanguage.of(localeManager(context).applicationLocales.firstOrNull())
        } else {
            stored(context)
        }

    /**
     * Shows the app in [language] from now on. Before Android 13 the caller recreates the
     * activity; from 13 the system does.
     */
    fun choose(
        context: Context,
        language: AppLanguage,
    ) {
        if (appliedBySystem) {
            localeManager(context).applicationLocales = language.localeList()
        } else {
            preferences(context)
                .edit()
                .apply { if (language.tag == null) remove(KEY_TAG) else putString(KEY_TAG, language.tag) }
                .apply()
            setDefault(language)
        }
    }

    /**
     * [context] showing the app's language: the activity's base, or a notifier's application
     * context. Unchanged from Android 13, where the system has already applied it.
     */
    fun applyTo(context: Context): Context {
        val locale = (if (appliedBySystem) null else stored(context).locale) ?: return context
        val configuration = Configuration(context.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return context.createConfigurationContext(configuration)
    }

    /** Before Android 13: the default locales are the chosen language, as 13 sets them, or the phone's. */
    private fun setDefault(language: AppLanguage) {
        LocaleList.setDefault(language.locale?.let { LocaleList(it) } ?: Resources.getSystem().configuration.locales)
    }

    private fun stored(context: Context): AppLanguage =
        AppLanguage.of(preferences(context).getString(KEY_TAG, null)?.let(Locale::forLanguageTag))

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(context: Context): LocaleManager = context.getSystemService(LocaleManager::class.java)

    private const val PREFERENCES_NAME = "app_language"
    private const val KEY_TAG = "tag"
}

private fun AppLanguage.localeList(): LocaleList =
    tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
