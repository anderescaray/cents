package io.github.anderescaray.cents.data.local.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.github.anderescaray.cents.domain.model.MoneyFigure
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.domain.repository.MoneyFiguresRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The money figures chosen, in DataStore: one key per switch, each written by itself.
 *
 * A switch never set reads as the default ([MoneyFigures]), and so does a store that cannot be
 * read. That is the harmless direction: the figures only show money, they never change it.
 */
@Singleton
class DataStoreMoneyFiguresRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : MoneyFiguresRepository {
        override fun observe(): Flow<MoneyFigures> =
            dataStore.data
                .catch { failure -> if (failure is IOException) emit(emptyPreferences()) else throw failure }
                .map { preferences ->
                    MoneyFigures(
                        normal = preferences[NORMAL] ?: DEFAULT.normal,
                        invested = preferences[INVESTED] ?: DEFAULT.invested,
                        total = preferences[TOTAL] ?: DEFAULT.total,
                    )
                }.distinctUntilChanged()

        override suspend fun set(
            figure: MoneyFigure,
            on: Boolean,
        ) {
            val key =
                when (figure) {
                    MoneyFigure.NORMAL -> NORMAL
                    MoneyFigure.INVESTED -> INVESTED
                    MoneyFigure.TOTAL -> TOTAL
                }
            try {
                dataStore.edit { it[key] = on }
            } catch (failure: IOException) {
                Log.w(TAG, "the money figures could not be saved", failure)
            }
        }

        private companion object {
            const val TAG = "MoneyFigures"
            val DEFAULT = MoneyFigures()
            val NORMAL = booleanPreferencesKey("figuresNormal")
            val INVESTED = booleanPreferencesKey("figuresInvested")
            val TOTAL = booleanPreferencesKey("figuresTotal")
        }
    }
