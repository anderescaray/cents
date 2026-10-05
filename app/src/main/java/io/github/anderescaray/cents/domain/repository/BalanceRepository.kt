package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.BalanceAdjustment
import io.github.anderescaray.cents.domain.model.BalanceChange
import io.github.anderescaray.cents.domain.model.Money
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Setting an account's balance to what it really holds, and the corrections that leaves. */
interface BalanceRepository {
    /** One account's corrections, newest first. */
    fun observeAdjustments(accountId: Long): Flow<List<BalanceAdjustment>>

    /**
     * Makes the account's balance [target], the way [change] says.
     *
     * The difference is measured **inside the same database transaction that writes it**, against
     * the balance as it stands then — opening balance, movements and earlier corrections — so a
     * movement captured from a notification while the screen was open cannot leave the account
     * anywhere but [target]. Nothing is written when the balance already is [target].
     *
     * No movement is created, changed or deleted, whichever [change] is chosen.
     *
     * @param on the day a [BalanceChange.CORRECT_FROM_TODAY] correction is dated: today.
     * @return the difference applied, zero when there was none, or null when the account is gone.
     * @throws IllegalArgumentException when [target] is not in the account's currency.
     */
    suspend fun setBalance(
        accountId: Long,
        target: Money,
        change: BalanceChange,
        on: LocalDate,
    ): Money?

    /** Removes one correction; the balance goes back to what it was without it. */
    suspend fun deleteAdjustment(id: Long)
}
