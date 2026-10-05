package io.github.anderescaray.cents.data.local.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.anderescaray.cents.domain.model.ReportLayout
import io.github.anderescaray.cents.domain.repository.ReportLayoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Reports arrangement in DataStore: two comma-separated lists of card keys.
 *
 * A store that cannot be read reads as the default arrangement. That is the harmless direction —
 * every card shown, in the order the app ships — and nothing about money depends on it.
 */
@Singleton
class DataStoreReportLayoutRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : ReportLayoutRepository {
        override fun observe(): Flow<ReportLayout> =
            dataStore.data
                .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
                .map { preferences ->
                    ReportLayout(
                        order = preferences[ORDER].toKeys(),
                        hidden = preferences[HIDDEN].toKeys().toSet(),
                    )
                }

        override suspend fun save(layout: ReportLayout) {
            try {
                dataStore.edit {
                    it[ORDER] = layout.order.joinToString(SEPARATOR)
                    it[HIDDEN] = layout.hidden.joinToString(SEPARATOR)
                }
            } catch (failure: IOException) {
                Log.w(TAG, "the Reports arrangement could not be saved", failure)
            }
        }

        private fun String?.toKeys(): List<String> = this?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

        private companion object {
            const val TAG = "ReportLayout"
            const val SEPARATOR = ","
            val ORDER = stringPreferencesKey("reportsOrder")
            val HIDDEN = stringPreferencesKey("reportsHidden")
        }
    }
