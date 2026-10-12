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

    suspend fun setNotifyOnCapture(enabled: Boolean)

    /** Adds the app, or changes its account if it was already chosen. */
    suspend fun putSource(source: CaptureSource)

    suspend fun removeSource(packageName: String)

    /**
     * Replaces one kind's words for [packageName], or the starting words when it is null. An empty
     * list is kept as empty, not read as the defaults.
     */
    suspend fun setWords(
        kind: TransactionKind,
        words: List<String>,
        packageName: String? = null,
    )

    /**
     * Puts one kind's words back: an app's to the starting words, the starting words to D29's
     * defaults.
     */
    suspend fun resetWords(
        kind: TransactionKind,
        packageName: String? = null,
    )

    /** Replaces the words that say an amount is not the payment. Empty stays empty. */
    suspend fun setIgnoreWords(words: List<String>)

    /** Puts the ignore words back to their defaults. */
    suspend fun resetIgnoreWords()
}

/**
 * Turns one notification into a movement, or into nothing.
 *
 * What the notification listener calls, once per notification from any app: every check that
 * decides whether to keep it lives here, so the listener is only the part that needs Android.
 */
interface CaptureRecorder {
    /**
     * @param announced whether a match would be announced, with Undo (D35). Without that
     * notification a wrong match could be neither seen nor undone, so the payment is saved as an
     * ordinary capture instead.
     */
    suspend fun record(
        packageName: String,
        text: String,
        postedAt: Instant,
        announced: Boolean = true,
    ): CaptureOutcome

    /**
     * Undoes [CaptureOutcome.Matched]: the payment is saved as an ordinary capture, and a charge
     * the match wrote goes back to waiting. Null when there is nothing left to undo.
     */
    suspend fun undoMatch(matchId: Long): CaptureOutcome.Recorded?
}
