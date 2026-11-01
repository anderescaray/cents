package io.github.anderescaray.cents.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Whether the Friday reminder to type the week's values is on (`DECISIONS.md` D40). On unless the
 * owner turns it off in Settings.
 */
interface InvestmentReminderRepository {
    fun observeEnabled(): Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}
