package io.github.anderescaray.cents.ui.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking

/**
 * Every view model a test makes, so the test can stop them all when it ends.
 *
 * A view model outlives the test that built it unless something says otherwise. `uiState` is
 * shared with `WhileSubscribed`, so the Room query behind it stays subscribed after Turbine
 * lets go, and its collector keeps running on the `Dispatchers.Main` the test installed. Two
 * things then go wrong, and the second is the one that is hard to read:
 *
 * 1. The query outlives the database it reads, because `@After` closes that.
 * 2. The *next* test class calls `Dispatchers.setMain` while a coroutine from this one is
 *    still using the old dispatcher, and dies with "Dispatchers.Main is used concurrently
 *    with setting it" — in a class that did nothing wrong. The failure lands on the victim,
 *    never on the leak, and which class is the victim depends on the order JUnit happens to
 *    run them in.
 *
 * Cancelling is what `onCleared` does on the phone, so a test that cancels is the one
 * behaving like the real thing.
 */
class ViewModelKeeper {
    private val made = mutableListOf<ViewModel>()

    /** Hands the view model straight back, so it can wrap the call that builds one. */
    fun <T : ViewModel> keep(viewModel: T): T = viewModel.also { made += it }

    /**
     * Stops them and **waits**, which is the whole point: `cancel()` only asks. It returns
     * before the coroutines it cancelled have finished, so they were still completing while
     * `resetMain()` ran, and that is itself a concurrent use of `Dispatchers.Main` — the same
     * `IllegalStateException`, now thrown by the very teardown meant to prevent it. Joining
     * is what makes the class actually finished when JUnit thinks it is.
     *
     * Call before closing the database, and from inside a `try` whose `finally` resets the
     * main dispatcher — an exception escaping teardown would otherwise leave `Main`
     * overridden for the whole JVM, which is the same bug one level up.
     */
    fun cancelAll() =
        runBlocking {
            made.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
            made.clear()
        }
}
