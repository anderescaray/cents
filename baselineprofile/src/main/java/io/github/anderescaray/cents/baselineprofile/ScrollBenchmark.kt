package io.github.anderescaray.cents.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times while scrolling the movements list, two years of them, without the profile and
 * with it. A frame over the phone's budget is a stutter the owner sees.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun seed() = seedOnce()

    @Test
    fun movementsWithoutProfile() = movements(CompilationMode.None())

    @Test
    fun movementsWithProfile() = movements(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun movements(compilationMode: CompilationMode) =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.WARM,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForHome()
                openTab("TRANSACTIONS")
            },
        ) {
            scrollDownAndUp()
        }

    private companion object {
        const val ITERATIONS = 10
    }
}
