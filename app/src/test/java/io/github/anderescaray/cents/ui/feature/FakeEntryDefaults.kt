package io.github.anderescaray.cents.ui.feature

import io.github.anderescaray.cents.domain.repository.EntryDefaultsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The movement form's remembered account, in memory: what DataStore keeps, without the file. */
class FakeEntryDefaults(
    initial: Long? = null,
) : EntryDefaultsRepository {
    val lastAccountId = MutableStateFlow(initial)

    override fun observeLastAccountId(): Flow<Long?> = lastAccountId

    override suspend fun rememberLastAccount(accountId: Long) {
        lastAccountId.value = accountId
    }
}
