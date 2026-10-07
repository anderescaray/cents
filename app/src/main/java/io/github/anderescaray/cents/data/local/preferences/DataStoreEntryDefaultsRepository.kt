package io.github.anderescaray.cents.data.local.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import io.github.anderescaray.cents.di.ApplicationScope
import io.github.anderescaray.cents.domain.repository.EntryDefaultsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The movement form's remembered account, in DataStore.
 *
 * A store that cannot be read reads as nothing remembered, and the form then starts on its first
 * account, which is what it did before there was anything to remember. A write that fails is
 * logged and dropped: the movement it follows is already saved, and must not look as if it failed.
 */
@Singleton
class DataStoreEntryDefaultsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : EntryDefaultsRepository {
        override fun observeLastAccountId(): Flow<Long?> =
            dataStore.data
                .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
                .map { it[LAST_ACCOUNT] }
                .distinctUntilChanged()

        // The app's scope rather than the form's: the form closes the moment its movement is saved,
        // and a write tied to it would be cancelled with it.
        override fun rememberLastAccount(accountId: Long) {
            scope.launch {
                try {
                    dataStore.edit { it[LAST_ACCOUNT] = accountId }
                } catch (failure: IOException) {
                    Log.w(TAG, "the last account could not be remembered", failure)
                }
            }
        }

        private companion object {
            const val TAG = "EntryDefaults"
            val LAST_ACCOUNT = longPreferencesKey("entryLastAccountId")
        }
    }
