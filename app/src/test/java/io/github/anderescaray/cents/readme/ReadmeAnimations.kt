package io.github.anderescaray.cents.readme

import android.content.Context
import android.view.View
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
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
import io.github.anderescaray.cents.ui.feature.investments.CHART_TAG
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
 * `docs/media/make_gifs.py` turns the frames into the GIFs, and says how to run both.
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

    /** A card payment's notification arrives, the movement is in Home by itself, one tap files it. */
    @Test
    fun capture() =
        scene("capture") { recorder ->
            recorder.hold(1_200)
            val first = recorder.frame
            recorder.hold(500)
            capture("Café Iruña: paid €12.40 with your card")
            recorder.settle { exists("To categorize") }
            recorder.hold(2_200)
            recorder.event(
                "notification $first ${recorder.frame} Bank | Card payment | Café Iruña: paid €12.40 with your card",
            )
            recorder.hold(600)
            recorder.tap(composeRule.onAllNodesWithText("Eating out").onFirst())
            recorder.hold(2_400)
        }

    /** What you have, then the brokerage: what it was worth month by month, and each asset. */
    @Test
    fun investments() =
        scene("investments") { recorder ->
            recorder.hold(600)
            recorder.tap(composeRule.onAllNodesWithText("Wallet").onFirst())
            recorder.hold(1_800)
            recorder.tap(composeRule.onAllNodesWithText("Brokerage").onFirst())
            recorder.hold(2_200)
            recorder.tap(composeRule.onAllNodesWithText("Months").onFirst())
            recorder.hold(1_400)
            // The chart opens on its first month; a swipe brings it to today.
            recorder.scroll(dx = -900f, ms = 800, node = composeRule.onNodeWithTag(CHART_TAG))
            recorder.hold(2_000)
            recorder.scroll(dy = -800f)
            recorder.hold(1_400)
            recorder.tap(composeRule.onAllNodesWithText("All-World ETF").onFirst())
            recorder.hold(2_600)
        }

    /** Where the money went: the ring, then the charts below it. */
    @Test
    fun reports() =
        scene("reports") { recorder ->
            recorder.hold(600)
            recorder.tap(composeRule.onAllNodesWithText("Reports").onFirst())
            recorder.hold(2_200)
            recorder.scroll(dy = -760f)
            recorder.hold(1_600)
            recorder.scroll(dy = -760f)
            recorder.hold(1_600)
            recorder.scroll(dy = -760f)
            recorder.hold(2_000)
        }

    private fun scene(
        name: String,
        body: (FrameRecorder) -> Unit,
    ) {
        assumeTrue(System.getenv("CENTS_README_SCENE").let { it == null || it == name })
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val recorder = FrameRecorder(composeRule, File(out, name), scenario.window())
            recorder.settle { exists("Left this month") }
            Thread.sleep(500)
            recorder.settle()
            body(recorder)
            recorder.finish()
        }
    }

    private fun exists(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun ActivityScenario<MainActivity>.window(): () -> View {
        lateinit var view: View
        onActivity { view = it.window.decorView }
        return { view }
    }

    private companion object {
        const val BANK_APP = "com.example.bank"
    }

    private fun capture(text: String) {
        val capture = EntryPointAccessors.fromApplication(context, CaptureListenerService.Dependencies::class.java)
        runBlocking(Dispatchers.IO) { capture.recorder().record(BANK_APP, text, Instant.now(), announced = false) }
    }
}
