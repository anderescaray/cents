package io.github.anderescaray.cents.ui.feature.settings

import io.github.anderescaray.cents.ui.language.AppLanguage
import io.github.anderescaray.cents.ui.language.AppLanguageSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageViewModelTest {
    private class FakeSetting(
        override val appliedBySystem: Boolean,
        var stored: AppLanguage = AppLanguage.SYSTEM,
    ) : AppLanguageSetting {
        val written = mutableListOf<AppLanguage>()

        override fun current(): AppLanguage = stored

        override fun choose(language: AppLanguage) {
            written += language
            stored = language
        }
    }

    @Test
    fun `the row starts on what is kept, with no placeholder first`() {
        val viewModel = AppLanguageViewModel(FakeSetting(appliedBySystem = true, stored = AppLanguage.SPANISH))

        assertEquals(AppLanguage.SPANISH, viewModel.language.value)
    }

    @Test
    fun `from Android 13 the system recreates the screen, so the caller does not`() {
        val setting = FakeSetting(appliedBySystem = true)
        val viewModel = AppLanguageViewModel(setting)

        assertFalse(viewModel.choose(AppLanguage.ENGLISH))
        assertEquals(listOf(AppLanguage.ENGLISH), setting.written)
        assertEquals(AppLanguage.ENGLISH, viewModel.language.value)
    }

    @Test
    fun `before Android 13 the caller recreates the screen to show the new language`() {
        val viewModel = AppLanguageViewModel(FakeSetting(appliedBySystem = false))

        assertTrue(viewModel.choose(AppLanguage.SPANISH))
    }

    @Test
    fun `choosing the language already shown changes nothing and recreates nothing`() {
        val setting = FakeSetting(appliedBySystem = false, stored = AppLanguage.ENGLISH)
        val viewModel = AppLanguageViewModel(setting)

        assertFalse(viewModel.choose(AppLanguage.ENGLISH))
        assertTrue(setting.written.isEmpty())
    }

    @Test
    fun `a change made in the phone's own settings is read again`() {
        val setting = FakeSetting(appliedBySystem = true)
        val viewModel = AppLanguageViewModel(setting)

        setting.stored = AppLanguage.SPANISH
        viewModel.refresh()

        assertEquals(AppLanguage.SPANISH, viewModel.language.value)
    }
}
