package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class DefaultDataSeederTest {
    private lateinit var database: CentsDatabase
    private lateinit var seeder: DefaultDataSeeder

    private val clock = Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = createTestDatabase()
        seeder = DefaultDataSeeder(database.accountDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a fresh install has no categories at all`() =
        runTest {
            seeder.seedIfEmpty(Locale.forLanguageTag("es-ES"))

            assertEquals(
                emptyList<String>(),
                database
                    .categoryDao()
                    .observeAll()
                    .first()
                    .map { it.name },
            )
        }

    @Test
    fun `a fresh install gets one account in the locale currency`() =
        runTest {
            seeder.seedIfEmpty(Locale.forLanguageTag("es-ES"))

            val accounts = database.accountDao().observeAll().first()
            assertEquals(1, accounts.size)
            assertEquals("EUR", accounts.single().currency)
        }

    @Test
    fun `seeding again on a database that already has an account changes nothing`() =
        runTest {
            seeder.seedIfEmpty(Locale.forLanguageTag("es-ES"))
            seeder.seedIfEmpty(Locale.forLanguageTag("es-ES"))

            assertEquals(
                1,
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .size,
            )
        }
}
