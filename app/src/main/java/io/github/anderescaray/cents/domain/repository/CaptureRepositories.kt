package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.CaptureOutcome
import io.github.anderescaray.cents.domain.model.CaptureSettings
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * How capture from notifications is set up (PRD §4.6, `DECISIONS.md` D29): the switch, the
 * apps it reads with their accounts, and the words that decide expense or income.
 */
interface CaptureSettingsRepository {
    fun observe(): Flow<CaptureSettings>

    suspend fun setEnabled(enabled: Boolean)

    /** Adds the app, or changes its account if it was already chosen. */
    suspend fun putSource(source: CaptureSource)

    suspend fun removeSource(packageName: String)

    /** Replaces one kind's words. An empty list is kept as empty, not read as the defaults. */
    suspend fun setWords(
        kind: TransactionKind,
        words: List<String>,
    )

    /** Puts one kind's words back to D29's defaults. */
    suspend fun resetWords(kind: TransactionKind)
}

/**
 * Turns one notification into a movement, or into nothing.
 *
 * What the notification listener calls, once per notification from any app: every check that
 * decides whether to keep it lives here, so the listener is only the part that needs Android.
 */
interface CaptureRecorder {
    suspend fun record(
        packageName: String,
        text: String,
        postedAt: Instant,
    ): CaptureOutcome
}
