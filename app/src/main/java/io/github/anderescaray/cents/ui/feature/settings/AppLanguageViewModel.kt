package io.github.anderescaray.cents.ui.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.ui.language.AppLanguage
import io.github.anderescaray.cents.ui.language.AppLanguageSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * The *Language* row at the end of Settings: which language the app is shown in, and changing it.
 *
 * Read synchronously, as the system reads it before drawing anything: one preference, or one call
 * to the system's per-app language. Starting from a placeholder instead would flash the wrong
 * answer on the row every time Settings opens.
 */
@HiltViewModel
class AppLanguageViewModel
    @Inject
    constructor(
        private val setting: AppLanguageSetting,
    ) : ViewModel() {
        private val state = MutableStateFlow(setting.current())

        val language: StateFlow<AppLanguage> = state.asStateFlow()

        /**
         * Reads it again. The phone's own *App languages* can change it while this view model
         * outlives the screen it belonged to, which a language change recreates.
         */
        fun refresh() {
            state.value = setting.current()
        }

        /**
         * Shows the app in [language] from now on. Returns whether the caller must recreate the
         * activity, which before Android 13 is how the new language reaches the screen; from 13
         * the system recreates it.
         */
        fun choose(language: AppLanguage): Boolean {
            if (language == state.value) return false
            setting.choose(language)
            state.value = language
            return !setting.appliedBySystem
        }
    }
