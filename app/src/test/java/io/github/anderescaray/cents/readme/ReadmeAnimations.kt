package io.github.anderescaray.cents.readme

import android.content.Context
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import io.github.anderescaray.cents.MainActivity
import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.local.migration.ALL_MIGRATIONS
import io.github.anderescaray.cents.data.repository.RoomBackupRepository
import io.github.anderescaray.cents.data.repository.TradeCashWriter
import io.github.anderescaray.cents.domain.model.CaptureSource
import io.github.anderescaray.cents.ui.capture.CaptureListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Records the README's animations from the real app: [MainActivity], its Hilt graph and its Room
 * database, filled with [ReadmeLedger] and driven by taps, with frames drawn by Robolectric's
 * native graphics.
 *
 * Skipped unless `CENTS_README_FRAMES` names a folder, so the test suite never spends time here.
 * How to turn the frames into GIFs is in `docs/media/README.md`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w392dp-h800dp-xhdpi")
class ReadmeAnimations {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val out = System.getenv("CENTS_README_FRAMES")?.let(::File)
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun seed() {
        assumeTrue("CENTS_README_FRAMES is not set", out != null)
        val database =
            Room
                .databaseBuilder(context, CentsDatabase::class.java, CentsDatabase.NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
        val clock = Clock.systemDefaultZone()
        val cash =
            TradeCashWriter(
                database.holdingDao(),
                database.tradeDao(),
                database.holdingValueDao(),
                database.transactionDao(),
                clock,
            )
        runBlocking(Dispatchers.IO) {
            RoomBackupRepository(database, clock, cash).restore(ReadmeLedger.build(LocalDate.now()))
        }
        database.close()
        val capture = EntryPointAccessors.fromApplication(context, CaptureListenerService.Dependencies::class.java)
        runBlocking(Dispatchers.IO) {
            capture.settings().setEnabled(true)
            capture.settings().setNotifyOnCapture(false)
            capture.settings().putSource(CaptureSource(BANK_APP, "Bank", ReadmeLedger.CHECKING))
        }
    }

    @Test
    fun probe() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val recorder = FrameRecorder(composeRule, File(out, "probe"))
            recorder.settle()
            Thread.sleep(1500)
            recorder.settle()
            recorder.hold(40)
            println(composeRule.onAllNodes(isRoot()).onFirst().printToString(maxDepth = 30))
            recorder.finish()
        }
    }

    private companion object {
        const val BANK_APP = "com.example.bank"
    }

    @Suppress("unused")
    private fun capture(text: String) {
        val capture = EntryPointAccessors.fromApplication(context, CaptureListenerService.Dependencies::class.java)
        runBlocking(Dispatchers.IO) { capture.recorder().record(BANK_APP, text, Instant.now(), announced = false) }
    }
}
