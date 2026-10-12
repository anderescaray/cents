package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.domain.model.AppWords
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.CaptureSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock

/** Capture's settings in memory, for the tests that record notifications. */
internal class FakeCaptureSettings(
    initial: CaptureSettings,
) : CaptureSettingsRepository {
    private val state = MutableStateFlow(initial)
    var value: CaptureSettings
        get() = state.value
        set(next) {
            state.value = next
        }

    override fun observe(): Flow<CaptureSettings> = state

    override suspend fun setEnabled(enabled: Boolean) {
        value = value.copy(enabled = enabled)
    }

    override suspend fun setNotifyOnCapture(enabled: Boolean) {
        value = value.copy(notifyOnCapture = enabled)
    }

    override suspend fun putSource(source: CaptureSource) {
        value = value.copy(sources = value.sources.filterNot { it.packageName == source.packageName } + source)
    }

    override suspend fun removeSource(packageName: String) {
        value = value.copy(sources = value.sources.filterNot { it.packageName == packageName })
    }

    override suspend fun setWords(
        kind: TransactionKind,
        words: List<String>,
        packageName: String?,
    ) {
        value =
            if (packageName != null) {
                val own = value.appWords[packageName] ?: AppWords()
                val changed =
                    when (kind) {
                        TransactionKind.EXPENSE -> own.copy(expense = words)
                        TransactionKind.INCOME -> own.copy(income = words)
                    }
                value.copy(appWords = value.appWords + (packageName to changed))
            } else {
                when (kind) {
                    TransactionKind.EXPENSE -> value.copy(expenseWords = words)
                    TransactionKind.INCOME -> value.copy(incomeWords = words)
                }
            }
    }

    override suspend fun resetWords(
        kind: TransactionKind,
        packageName: String?,
    ) {
        if (packageName != null) {
            val own = value.appWords[packageName] ?: return
            val cleared =
                when (kind) {
                    TransactionKind.EXPENSE -> own.copy(expense = null)
                    TransactionKind.INCOME -> own.copy(income = null)
                }
            value = value.copy(appWords = value.appWords + (packageName to cleared))
            return
        }
        setWords(
            kind,
            when (kind) {
                TransactionKind.EXPENSE -> CaptureSettings.DEFAULT_EXPENSE_WORDS
                TransactionKind.INCOME -> CaptureSettings.DEFAULT_INCOME_WORDS
            },
            null,
        )
    }

    override suspend fun setIgnoreWords(words: List<String>) {
        value = value.copy(ignoreWords = words)
    }

    override suspend fun resetIgnoreWords() {
        value = value.copy(ignoreWords = CaptureSettings.DEFAULT_IGNORE_WORDS)
    }
}

/** The subscriptions a capture can match, over [database], on [clock]'s day. */
internal fun ruleRepository(
    database: CentsDatabase,
    clock: Clock,
) = RoomRecurringRuleRepository(database.recurringRuleDao(), database.ruleReminderDao(), database, clock)
