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
 * only two places text is made; the default locale is set to it so formats follow it as they do
 * on Android 13. The screen that changes it recreates the activity ([appliedBySystem]).
 *
 * Either way the language never touches money: the currency comes from [phoneLocale].
 */
object AppLanguages {
    /** The application, kept from [onAppStart] so [phoneLocale] can ask the system. */
    @Volatile private var application: Context? = null

    /** Whether the system applies the language and recreates the screens itself. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val appliedBySystem: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Run once when the process starts, before anything formats an amount or a date. */
    fun onAppStart(context: Context) {
        application = context.applicationContext
        if (!appliedBySystem) setDefault(stored(context))
    }

    /**
     * Run when the phone's configuration changes. Before Android 13 the system then resets the
     * default locale to the phone's, which would leave formats in one language and words in
     * another until the app restarts.
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

    /**
     * The phone's own locale, whatever language the app is shown in: what names its currency.
     *
     * From Android 13 a per-app language is part of the process's configuration, which the
     * system's resources take on too, so only [LocaleManager] still tells the two apart. Before
     * 13 the app's language never reaches the system's resources.
     */
    fun phoneLocale(): Locale {
        val context = application
        val locales =
            if (appliedBySystem && context != null) {
                localeManager(context).systemLocales
            } else {
                Resources.getSystem().configuration.locales
            }
        return locales.firstOrNull() ?: Locale.getDefault()
    }

    /** Before Android 13: formats follow the chosen language, or the phone's list again. */
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

private fun LocaleList.firstOrNull(): Locale? = if (isEmpty) null else get(0)
