package io.github.anderescaray.cents.ui.feature

import app.cash.turbine.TurbineTestContext

/**
 * Waits for a state matching [predicate] instead of guessing how many emissions arrive.
 *
 * A view model combining several flows emits an unpredictable number of intermediate
 * states, so asserting on "the next one" makes tests flaky for reasons that have nothing
 * to do with the behaviour under test.
 */
suspend fun <T> TurbineTestContext<T>.awaitStateThat(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
