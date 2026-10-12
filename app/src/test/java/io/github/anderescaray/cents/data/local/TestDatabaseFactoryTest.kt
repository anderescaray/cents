package io.github.anderescaray.cents.data.local

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
            val testThread = Thread.currentThread()
            val seen = mutableListOf<Int>()
            val threads = mutableSetOf<Thread>()
            val watching =
                launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                    database.accountDao().observeAll().collect { accounts ->
                        seen += accounts.size
                        threads += Thread.currentThread()
                    }
                }

            database.accountDao().insert(account(name = "Checking"))

            // Read straight after the write, with no waiting: on Room's own threads this would race.
            assertEquals(listOf(0, 1), seen)
            assertTrue("emitted on $threads, not on the test thread", threads.all { it === testThread })
            watching.cancel()
        }
}
