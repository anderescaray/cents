package io.github.anderescaray.cents.data.local.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import io.github.anderescaray.cents.domain.repository.AppLockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One flag in DataStore: whether the app asks who you are.
 *
 * **A store that cannot be read reads as locked**, which is the opposite of how the home layout
 * next door treats the same failure — and deliberately. Losing the bubble arrangement to an I/O
 * error costs an arrangement; treating "I could not tell" as *unlocked* would mean a disk hiccup
 * quietly turning the lock off, which is the one direction this flag must never fail in.
 *
 * The cost of that choice is the other failure: somebody with the lock **off** could be asked to
 * authenticate after a read error. That is recoverable — they unlock, and the next successful
 * read puts it right — and being asked once too often is a far better wrong answer than a
 * ledger shown to whoever picked the phone up.
 */
@Singleton
class DataStoreAppLockRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : AppLockRepository {
        override fun observeEnabled(): Flow<Boolean> =
            dataStore.data
                .map { preferences ->
                    // Absent means never turned on, which is off: the lock is something the
                    // owner asks for rather than something the app imposes on first run.
                    preferences[LOCK_ENABLED] ?: false
                }.catch { failure ->
                    // Caught *after* the mapping, so that a read which fails can answer `true`.
                    // Catching before it could only offer empty preferences, which read as off —
                    // the one direction this flag must never fail in.
                    if (failure is IOException) emit(true) else throw failure
                }

        override suspend fun setEnabled(enabled: Boolean) {
            try {
                dataStore.edit { it[LOCK_ENABLED] = enabled }
            } catch (failure: IOException) {
                // Unlike a bubble's position, this one is worth saying out loud: the switch the
                // owner just moved did not stick, and they have no way of knowing from here.
                Log.w(TAG, "the app lock setting could not be saved", failure)
            }
        }

        private companion object {
            const val TAG = "AppLock"
            val LOCK_ENABLED = booleanPreferencesKey("appLockEnabled")
        }
    }
