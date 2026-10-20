package io.github.anderescaray.cents.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the app's baseline profile: the code to compile ahead of time, so the first launch and
 * the first scroll after an install are not left to the interpreter.
 *
 * Run with `./gradlew :app:generateBaselineProfile` with the phone connected. The result lands in
 * `app/src/release/generated/baselineProfiles` and is committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
            seedOnce()
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
