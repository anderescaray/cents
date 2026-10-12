package io.github.anderescaray.cents.data.local

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.coroutines.Continuation

/**
 * What every Room test relies on: the test database works on the thread that asks it, so a view
 * model's collectors never run on two threads at once (`createTestDatabase`, `docs/testing.md`).
 */
@RunWith(RobolectricTestRunner::class)
class TestDatabaseFactoryTest {
    private lateinit var database: CentsDatabase

    @Before
    fun setUp() {
        database = createTestDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a write reaches the flows watching it on the writing thread, before it returns`() =
        runTest {
            val watched = watchAccounts()

            database.accountDao().insert(account(name = "Checking"))

            // Read straight after the write, with no waiting: on Room's own threads this would race.
            assertEquals(listOf(0, 1), watched.counts)
            assertTrue("emitted on ${watched.threads}", watched.threads.all { it === watched.testThread })
        }

    @Test
    fun `a transaction runs on the test thread too, and its flows follow it`() =
        runTest {
            val watched = watchAccounts()

            // The path every repository write takes.
            database.withTransaction {
                database.accountDao().insert(account(name = "Checking"))
                database.accountDao().insert(account(name = "Cash"))
            }

            assertEquals("one emission for the whole transaction", listOf(0, 2), watched.counts)
            assertTrue("emitted on ${watched.threads}", watched.threads.all { it === watched.testThread })
        }

    /**
     * The test database allows queries on the main thread, which is where Robolectric runs tests,
     * so it can no longer catch a blocking query made there. This catches the only way to write
     * one: a DAO method that neither suspends nor returns a `Flow`, both of which Room keeps off
     * the main thread on the phone.
     */
    @Test
    fun `every DAO method suspends or returns a flow`() {
        val daos =
            CentsDatabase::class.java.methods
                .map { it.returnType }
                .filter { it.simpleName.endsWith("Dao") }
        val blocking =
            daos.flatMap { dao ->
                dao.declaredMethods
                    .filterNot { it.isSynthetic }
                    .filterNot { method ->
                        method.parameterTypes.lastOrNull() == Continuation::class.java ||
                            Flow::class.java.isAssignableFrom(method.returnType)
                    }.map { "${dao.simpleName}.${it.name}" }
            }

        assertTrue("checked ${daos.size} DAOs", daos.size >= MIN_DAOS)
        assertEquals("blocking DAO methods", emptyList<String>(), blocking)
    }

    private class Watched(
        val testThread: Thread,
    ) {
        val counts = mutableListOf<Int>()
        val threads = mutableSetOf<Thread>()
    }

    /** Watches every account until the test ends, noting how many there were and on which thread. */
    private fun TestScope.watchAccounts(): Watched {
        val watched = Watched(Thread.currentThread())
        backgroundScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            database.accountDao().observeAll().collect { accounts ->
                watched.counts += accounts.size
                watched.threads += Thread.currentThread()
            }
        }
        return watched
    }

    private companion object {
        /** `CentsDatabase` has twelve DAOs; fewer found means the lookup broke, not that they went. */
        const val MIN_DAOS = 12
    }
}
