package io.github.anderescaray.cents.data.local.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.anderescaray.cents.domain.model.AppWords
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Capture's settings in DataStore, beside the app lock and the home layout.
 *
 * **Not in Room, on purpose.** These are settings, not the ledger: which apps to read and which
 * words to look for. What capture *writes* is an ordinary movement in Room, like any other. So
 * the feature needs no schema change, and a restore of a backup, which replaces the ledger (D21),
 * leaves the way the phone is set up alone.
 *
 * **A store that cannot be read reads as switched off.** Unlike the lock next door, the safe
 * failure here is doing nothing: a capture that stops for a moment loses a movement the owner can
 * type, while one that runs on settings it could not read could write into the wrong account.
 */
@Singleton
class DataStoreCaptureSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : CaptureSettingsRepository {
        override fun observe(): Flow<CaptureSettings> =
            dataStore.data
                .map { preferences ->
                    CaptureSettings(
                        enabled = preferences[ENABLED] ?: false,
                        sources = preferences[SOURCES].orEmpty().mapNotNull(::decode).sortedBy { it.label },
                        // Absent is "never edited", which is the defaults; an empty set is a list
                        // the owner emptied, and stays empty.
                        expenseWords =
                            preferences[EXPENSE_WORDS]?.sorted() ?: CaptureSettings.DEFAULT_EXPENSE_WORDS,
                        incomeWords = preferences[INCOME_WORDS]?.sorted() ?: CaptureSettings.DEFAULT_INCOME_WORDS,
                        appWords = appWordsIn(preferences),
                    )
                }.catch { failure ->
                    if (failure is IOException) emit(CaptureSettings()) else throw failure
                }

        override suspend fun setEnabled(enabled: Boolean) = write { it[ENABLED] = enabled }

        override suspend fun putSource(source: CaptureSource) =
            write { preferences ->
                val others = preferences[SOURCES].orEmpty().filterNot { decode(it)?.packageName == source.packageName }
                preferences[SOURCES] = (others + encode(source)).toSet()
            }

        override suspend fun removeSource(packageName: String) =
            write { preferences ->
                preferences[SOURCES] =
                    preferences[SOURCES].orEmpty().filterNot { decode(it)?.packageName == packageName }.toSet()
                // Its words go with it: choosing the app again starts it from the starting words.
                TransactionKind.entries.forEach { preferences.remove(keyFor(it, packageName)) }
            }

        override suspend fun setWords(
            kind: TransactionKind,
            words: List<String>,
            packageName: String?,
        ) = write { it[keyFor(kind, packageName)] = words.map(String::trim).filter(String::isNotEmpty).toSet() }

        override suspend fun resetWords(
            kind: TransactionKind,
            packageName: String?,
        ) = write { it.remove(keyFor(kind, packageName)) }

        /** Every app's own lists, read back from the keys that carry its package name. */
        private fun appWordsIn(preferences: Preferences): Map<String, AppWords> {
            val lists = mutableMapOf<String, AppWords>()
            preferences.asMap().forEach { (key, value) ->
                val name = key.name
                if (!name.startsWith(APP_PREFIX)) return@forEach
                val kindAndPackage = name.removePrefix(APP_PREFIX).split(APP_SEPARATOR, limit = 2)
                val kind =
                    TransactionKind.entries.firstOrNull { it.name == kindAndPackage.firstOrNull() } ?: return@forEach
                val packageName = kindAndPackage.getOrNull(1) ?: return@forEach
                val words = (value as? Set<*>)?.filterIsInstance<String>()?.sorted() ?: return@forEach
                val current = lists[packageName] ?: AppWords()
                lists[packageName] =
                    when (kind) {
                        TransactionKind.EXPENSE -> current.copy(expense = words)
                        TransactionKind.INCOME -> current.copy(income = words)
                    }
            }
            return lists
        }

        private suspend fun write(change: (MutablePreferences) -> Unit) {
            try {
                dataStore.edit(change)
            } catch (failure: IOException) {
                // Said out loud, like the lock: the owner just changed a setting, and from here
                // they have no way of knowing it did not stick.
                Log.w(TAG, "a capture setting could not be saved", failure)
            }
        }

        private fun keyFor(
            kind: TransactionKind,
            packageName: String? = null,
        ) = when {
            packageName != null -> stringSetPreferencesKey("$APP_PREFIX${kind.name}$APP_SEPARATOR$packageName")
            kind == TransactionKind.EXPENSE -> EXPENSE_WORDS
            else -> INCOME_WORDS
        }

        /**
         * One app as one string: `package|account|label`. The package name and the id cannot
         * hold the separator, and the label goes last so that one holding it still reads back.
         */
        private fun encode(source: CaptureSource): String = "${source.packageName}|${source.accountId}|${source.label}"

        private fun decode(value: String): CaptureSource? {
            val parts = value.split('|', limit = PARTS)
            val accountId = parts.getOrNull(1)?.toLongOrNull()
            return if (parts.size == PARTS && accountId != null) {
                CaptureSource(packageName = parts[0], label = parts[2], accountId = accountId)
            } else {
                null
            }
        }

        private companion object {
            const val TAG = "Capture"
            const val PARTS = 3
            val ENABLED = booleanPreferencesKey("captureEnabled")
            val SOURCES = stringSetPreferencesKey("captureSources")
            val EXPENSE_WORDS = stringSetPreferencesKey("captureExpenseWords")
            val INCOME_WORDS = stringSetPreferencesKey("captureIncomeWords")

            /** `captureWords:EXPENSE:com.bank.app` — a package name cannot hold the colon. */
            const val APP_PREFIX = "captureWords:"
            const val APP_SEPARATOR = ":"
        }
    }
