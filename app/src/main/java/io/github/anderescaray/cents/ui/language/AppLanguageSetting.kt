package io.github.anderescaray.cents.ui.language

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The app's language as Settings sees it: an interface, so its view model is tested without a phone. */
interface AppLanguageSetting {
    /** Whether the system recreates the screens itself when the language changes (Android 13+). */
    val appliedBySystem: Boolean

    fun current(): AppLanguage

    fun choose(language: AppLanguage)
}

/** The real one, kept by [AppLanguages]. */
class DeviceAppLanguageSetting
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AppLanguageSetting {
        override val appliedBySystem: Boolean get() = AppLanguages.appliedBySystem

        override fun current(): AppLanguage = AppLanguages.current(context)

        override fun choose(language: AppLanguage) = AppLanguages.choose(context, language)
    }
