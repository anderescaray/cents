package io.github.anderescaray.cents.ui.feature

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.data.export.DocumentWriter
import io.github.anderescaray.cents.data.export.ExportFormat
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.account
import io.github.anderescaray.cents.data.local.createTestDatabase
import io.github.anderescaray.cents.data.local.transaction
import io.github.anderescaray.cents.data.repository.RoomBackupRepository
import io.github.anderescaray.cents.ui.feature.settings.ExportStep
import io.github.anderescaray.cents.ui.feature.settings.ExportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExportViewModelTest {
    private lateinit var database: CentsDatabase
    private lateinit var writer: RecordingWriter
    private val viewModels = ViewModelKeeper()

    private val clock = Clock.fixed(Instant.parse("2026-09-16T18:00:00Z"), ZoneOffset.UTC)
    private val somewhere: Uri = Uri.parse("content://documents/cents-2026-09-16.json")

    @Before
    fun setUp() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            database = createTestDatabase()
            writer = RecordingWriter(ApplicationProvider.getApplicationContext())
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
    fun `choosing a format decides what the picker is opened for`() =
        runTest {
            val viewModel = exporter()
            viewModel.start()
            viewModel.choose(ExportFormat.CSV)

            val state = viewModel.uiState.value
            assertEquals(ExportStep.PICKING, state.step)
            assertEquals(ExportFormat.CSV, state.format)
            // The name carries the day, so two exports in a week do not overwrite each other
            // by default — the owner can still type over it in the picker.
            assertEquals("cents-2026-09-16.csv", state.suggestedName)
        }

    @Test
    fun `writing puts the whole app into the file and says how much`() =
        runTest {
            val viewModel = exporter()
            viewModel.start()
            viewModel.choose(ExportFormat.JSON)
            viewModel.writeTo(somewhere)

            val state = viewModel.uiState.first { it.step == ExportStep.DONE }
            assertEquals(2, state.rowsWritten)
            val written = writer.written.orEmpty()
            assertTrue(written, written.contains("\"schemaVersion\""))
            assertTrue(written, written.contains("Checking"))
            assertEquals(somewhere, writer.target)
        }

    @Test
    fun `the csv choice writes a csv`() =
        runTest {
            val viewModel = exporter()
            viewModel.start()
            viewModel.choose(ExportFormat.CSV)
            viewModel.writeTo(somewhere)

            viewModel.uiState.first { it.step == ExportStep.DONE }
            assertTrue(writer.written.orEmpty().startsWith("date,account,category,"))
        }

    @Test
    fun `backing out of the picker is not a failure`() =
        runTest {
            val viewModel = exporter()
            viewModel.start()
            viewModel.choose(ExportFormat.JSON)
            viewModel.writeTo(null)

            // The most common way this flow ends. Reporting it as an error would teach the
            // owner to distrust the one message that matters — the one that says it failed.
            assertEquals(ExportStep.IDLE, viewModel.uiState.value.step)
            assertNull(writer.written)
        }

    @Test
    fun `a file that cannot be written says so rather than saying nothing`() =
        runTest {
            writer.fail = true
            val viewModel = exporter()
            viewModel.start()
            viewModel.choose(ExportFormat.JSON)
            viewModel.writeTo(somewhere)

            // The one thing worse than having no backup is believing you have one.
            assertEquals(ExportStep.FAILED, viewModel.uiState.first { it.step == ExportStep.FAILED }.step)
        }

    @Test
    fun `acknowledging puts the screen back where it started`() =
        runTest {
            val viewModel = exporter()
            viewModel.start()
            viewModel.choose(ExportFormat.JSON)
            viewModel.writeTo(somewhere)
            viewModel.uiState.first { it.step == ExportStep.DONE }

            viewModel.acknowledge()

            assertEquals(ExportStep.IDLE, viewModel.uiState.value.step)
            assertNull(viewModel.uiState.value.format)
        }

    private fun exporter(): ExportViewModel =
        viewModels.keep(
            ExportViewModel(
                RoomBackupRepository(database, clock),
                writer,
                clock,
            ),
        )
}

/**
 * The real writer with its one Android call replaced.
 *
 * Opening an output stream on a `content://` Uri needs a document provider that no test has, so
 * the write itself is the one thing faked; everything above it — what goes in the file, and what
 * the screen says afterwards — is the real code.
 */
private class RecordingWriter(
    context: Context,
) : DocumentWriter(context) {
    var written: String? = null
    var target: Uri? = null
    var fail: Boolean = false

    override suspend fun write(
        uri: Uri,
        text: String,
    ) {
        if (fail) error("no room on the device")
        target = uri
        written = text
    }
}
