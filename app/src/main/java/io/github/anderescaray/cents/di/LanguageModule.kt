package io.github.anderescaray.cents.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.ui.language.AppLanguageSetting
import io.github.anderescaray.cents.ui.language.DeviceAppLanguageSetting

/** The app's own language (`ui/language/AppLanguages.kt`), kept by the system or by the app itself. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LanguageModule {
    @Binds
    abstract fun bindAppLanguageSetting(impl: DeviceAppLanguageSetting): AppLanguageSetting
}
