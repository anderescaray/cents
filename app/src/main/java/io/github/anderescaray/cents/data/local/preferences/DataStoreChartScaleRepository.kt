package io.github.anderescaray.cents.data.local.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.anderescaray.cents.domain.model.ChartScale
import io.github.anderescaray.cents.domain.repository.ChartScaleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Each investment chart's scale, in DataStore: one key per chart, by name. A chart never left on
 * a scale, or a store that cannot be read, opens by week, the scale the owner records in.
 */
@Singleton
class DataStoreChartScaleRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : ChartScaleRepository {
        override fun observe(key: String): Flow<ChartScale> =
            dataStore.data
                .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
                .map { preferences ->
                    val name = preferences[keyFor(key)]
                    ChartScale.entries.firstOrNull { it.name == name } ?: ChartScale.WEEK
                }.distinctUntilChanged()

        override suspend fun set(
            key: String,
            scale: ChartScale,
        ) {
            try {
                dataStore.edit { it[keyFor(key)] = scale.name }
            } catch (failure: IOException) {
                Log.w(TAG, "a chart's scale could not be saved", failure)
            }
        }

        private fun keyFor(chart: String) = stringPreferencesKey("chartScale-$chart")

        private companion object {
            const val TAG = "ChartScale"
        }
    }
