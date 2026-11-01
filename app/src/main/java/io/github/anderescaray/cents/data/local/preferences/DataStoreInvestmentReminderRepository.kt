package io.github.anderescaray.cents.data.local.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.github.anderescaray.cents.domain.repository.InvestmentReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The Friday reminder's switch, in DataStore. Never set, or unreadable, reads as on. */
@Singleton
class DataStoreInvestmentReminderRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : InvestmentReminderRepository {
        override fun observeEnabled(): Flow<Boolean> =
            dataStore.data
                .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
                .map { it[ENABLED] ?: true }
                .distinctUntilChanged()

        override suspend fun setEnabled(enabled: Boolean) {
            try {
                dataStore.edit { it[ENABLED] = enabled }
            } catch (failure: IOException) {
                Log.w(TAG, "the investment reminder's switch could not be saved", failure)
            }
        }

        private companion object {
            const val TAG = "InvestmentReminder"
            val ENABLED = booleanPreferencesKey("investmentReminder")
        }
    }
