package io.github.anderescaray.cents.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the app's baseline profile: the code to compile ahead of time, so the first launch and
 * the first scroll after an install are not left to the interpreter.
 *
 * Two journeys, because only one of them is startup: the launch to Home also goes into the
 * startup profile, which decides what R8 puts in the first dex file, and the tabs do not.
 *
 * Run with `./gradlew :app:generateBaselineProfile` with the phone connected. The result lands in
 * `app/src/release/generated/baselineProfiles` and is committed; the with-profile benchmarks need
 * it there.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Before
    fun seed() = seedOnce()

    @Test
    fun startup() =
        rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            waitForHome()
        }

    @Test
    fun tabs() =
        rule.collect(packageName = targetPackage) {
            pressHome()
            startActivityAndWait()
            waitForHome()
            scrollDownAndUp()
            for (tab in listOf("TRANSACTIONS", "REPORTS", "ACCOUNTS", "HOME")) {
                openTab(tab)
                scrollDownAndUp()
            }
        }
}
