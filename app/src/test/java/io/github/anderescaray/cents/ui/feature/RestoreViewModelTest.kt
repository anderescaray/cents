package io.github.anderescaray.cents.ui.feature

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.data.export.BackupWriter
import io.github.anderescaray.cents.data.export.DocumentReader
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.backupRepository
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomBackupRepository
import io.github.anderescaray.cents.ui.feature.settings.RestoreProblem
import io.github.anderescaray.cents.ui.feature.settings.RestoreStep
import io.github.anderescaray.cents.ui.feature.settings.RestoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The sequence in front of the most destructive thing this app can do.
 *
 * What is being protected is not the happy path — that is `RestoreTest`'s job — but the moments
 * where the owner should still have everything they had: a file that is not a backup, a file
 * from a newer app, a picker backed out of, and a write that fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RestoreViewModelTest {
    private lateinit var database: CentsDatabase
    private lateinit var reader: HandwrittenReader
    private val viewModels = ViewModelKeeper()

    private val clock = Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC)
    private val somewhere: Uri = Uri.parse("content://documents/cents-2026-09-17.json")

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            database = createTestDatabase()
            reader = HandwrittenReader(ApplicationProvider.getApplicationContext())
            val accountId = database.accountDao().insert(account(name = "Checking"))
            database.transactionDao().insert(transaction(accountId, amountMinor = 2_550))
        }

    @After
    fun tearDown() {
        try {
            viewModels.cancelAll()
            if (::database.isInitialized) database.close()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `the question names what is coming and what is going`() =
        runTest {
            reader.text = backupOfAnotherLedger()
            val viewModel = restorer()

            viewModel.start()
            viewModel.readFrom(somewhere)

            val state = viewModel.uiState.first { it.step == RestoreStep.CONFIRMING }
            // Both halves of the trade. Naming only one would be asking half a question.
            assertEquals(2, state.incoming?.accounts)
            assertEquals(1, state.current.accounts)
            assertEquals(1, state.current.movements)
        }

    @Test
    fun `nothing is written until it is confirmed`() =
        runTest {
            reader.text = backupOfAnotherLedger()
            val viewModel = restorer()

            viewModel.start()
            viewModel.readFrom(somewhere)
            viewModel.uiState.first { it.step == RestoreStep.CONFIRMING }

            // Reading the file must not be the same act as accepting it.
            assertEquals(
                listOf("Checking"),
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .map { it.name },
            )
        }

    @Test
    fun `confirming replaces everything`() =
        runTest {
            reader.text = backupOfAnotherLedger()
            val viewModel = restorer()

            viewModel.start()
            viewModel.readFrom(somewhere)
            viewModel.uiState.first { it.step == RestoreStep.CONFIRMING }
            viewModel.confirm()

            viewModel.uiState.first { it.step == RestoreStep.DONE }
            assertEquals(
                setOf("Savings", "Broker"),
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .map { it.name }
                    .toSet(),
            )
        }

    @Test
    fun `the wrong file changes nothing and says which way it was wrong`() =
        runTest {
            reader.text = "this is a shopping list"
            val viewModel = restorer()

            viewModel.start()
            viewModel.readFrom(somewhere)

            val state = viewModel.uiState.first { it.step == RestoreStep.REFUSED }
            assertEquals(RestoreProblem.NOT_A_BACKUP, state.problem)
            assertEquals(
                listOf("Checking"),
                database
                    .accountDao()
                    .observeAll()
                    .first()
                    .map { it.name },
            )
        }

    @Test
    fun `a newer file is refused, and the message can name its version`() =
        runTest {
            reader.text = """{"schemaVersion":99,"accounts":[]}"""
            val viewModel = restorer()

            viewModel.start()
            viewModel.readFrom(somewhere)

            val state = viewModel.uiState.first { it.step == RestoreStep.REFUSED }
            assertEquals(RestoreProblem.TOO_NEW, state.problem)
            assertEquals(99, state.fileVersion)
        }

    @Test
    fun `a backup with a hole in it is refused whole`() =
        runTest {
            reader.text =
                """
                {"schemaVersion":4,"accounts":[],"categories":[],
                 "transactions":[{"id":1,"accountId":1,"kind":"EXPENSE","currency":"EUR","date":20684}],
                 "holdings":[],"trades":[],"quotes":[]}
                """.trimIndent()
            val viewModel = restorer()

            viewModel.start()
            viewModel.readFrom(somewhere)

            // Half a restore is worse than none: the movement with no amount would have been
            // written as zero, and the owner would have no way of knowing which one.
            assertEquals(RestoreProblem.DAMAGED, viewModel.uiState.first { it.step == RestoreStep.REFUSED }.problem)
            assertEquals(
                1,
                database
                    .transactionDao()
                    .observeAll()
                    .first()
                    .size,
            )
        }

    @Test
    fun `backing out of the picker is not a failure`() =
        runTest {
            val viewModel = restorer()
            viewModel.start()
            viewModel.readFrom(null)

            assertEquals(RestoreStep.IDLE, viewModel.uiState.value.step)
        }

    @Test
    fun `a file that cannot be read at all is refused rather than crashing`() =
        runTest {
            reader.fail = true
            val viewModel = restorer()

            viewModel.start()
            viewModel.readFrom(somewhere)

            assertNotNull(viewModel.uiState.first { it.step == RestoreStep.REFUSED }.problem)
        }

    @Test
    fun `an empty app has nothing to lose, and the question says so by omission`() =
        runTest {
            database.transactionDao().deleteAll()
            database.accountDao().deleteAll()
            reader.text = backupOfAnotherLedger()
            val viewModel = restorer()

            viewModel.start()
            viewModel.readFrom(somewhere)

            // Warning somebody that this replaces their 0 accounts is alarming about nothing,
            // so the alert leaves that half out — which it can only do if it knows.
            val state = viewModel.uiState.first { it.step == RestoreStep.CONFIRMING }
            assertTrue(state.current.isEmpty)
        }

    /** A backup of a different ledger, so a restore is visibly a replacement. */
    private suspend fun backupOfAnotherLedger(): String {
        val other = createTestDatabase()
        return try {
            other.accountDao().insert(account(name = "Savings"))
            other.accountDao().insert(account(name = "Broker"))
            BackupWriter.toJson(backupRepository(other, clock).snapshot())
        } finally {
            other.close()
        }
    }

    private fun restorer(): RestoreViewModel =
        viewModels.keep(RestoreViewModel(backupRepository(database, clock), reader))
}

/**
 * The real reader with its one Android call replaced: opening a `content://` Uri needs a
 * document provider no test has, so the file's *contents* are handed over directly and
 * everything above — parsing, refusing, replacing — is the real code.
 */
private class HandwrittenReader(
    context: Context,
) : DocumentReader(context) {
    var text: String = ""
    var fail: Boolean = false

    override suspend fun read(uri: Uri): String {
        if (fail) error("the file went away")
        return text
    }
}
