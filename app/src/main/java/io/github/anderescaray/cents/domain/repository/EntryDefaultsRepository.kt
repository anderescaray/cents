package io.github.anderescaray.cents.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * What the movement form remembers from one movement to the next: the account the last one went
 * in, so the next starts there (`docs/next-improvements.md` §5).
 *
 * Only an id, and only a suggestion. Whoever reads it checks it still names an account that can
 * take the movement — the account may have been deleted, archived, made a broker or replaced by
 * a restore since — and falls back when it does not.
 */
interface EntryDefaultsRepository {
    /** The account of the last new income or expense recorded by hand, or null before the first. */
    fun observeLastAccountId(): Flow<Long?>

    /** Called after a new income or expense is saved. Never throws: losing it costs one tap. */
    suspend fun rememberLastAccount(accountId: Long)
}
