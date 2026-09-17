package io.github.anderescaray.cents.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Whether the app asks who you are before showing anything.
 *
 * One flag, and deliberately nothing else. There is no passcode here, no key and no secret: the
 * device already knows how to recognise its owner, and this app only asks it and believes the
 * answer (rule 6). What is under the lock is not encrypted either — `DECISIONS.md` D16 settled
 * that, because Android encrypts the device and a key kept to unlock a database is a key that
 * can be lost along with the money it protects.
 *
 * So what this buys is honest and small: somebody holding your unlocked phone cannot read your
 * finances. It is not protection against somebody holding your phone *and* your fingerprint,
 * and it never claims to be.
 */
interface AppLockRepository {
    /** True when the app should ask before showing anything. */
    fun observeEnabled(): Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}
