package io.github.anderescaray.cents.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.anderescaray.cents.data.local.preferences.DataStoreHomeLayoutRepository
import io.github.anderescaray.cents.domain.repository.HomeLayoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Preferences, kept apart from `RepositoryModule` because none of this goes near Room — and
 * because a module per storage mechanism is one fewer file two branches both have to edit.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {
    @Binds
    abstract fun bindHomeLayoutRepository(impl: DataStoreHomeLayoutRepository): HomeLayoutRepository

    companion object {
        private const val PREFERENCES_NAME = "cents.preferences"

        /**
         * One store for the whole app. Its own scope on the IO dispatcher, not the caller's:
         * a write started by a screen that then goes away must still finish, since a dropped
         * write here is a bubble that jumps back where it was.
         */
        @Provides
        @Singleton
        fun providePreferences(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { context.preferencesDataStoreFile(PREFERENCES_NAME) },
            )
    }
}
