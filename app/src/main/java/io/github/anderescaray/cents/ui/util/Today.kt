package io.github.anderescaray.cents.ui.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What day it is, as something a screen can watch.
 *
 * Reading the clock once, when a screen's state is built, is how an app left open across
 * midnight kept calling yesterday *Today* and kept this month's summary on the month that had
 * ended. Every screen that shows a day relative to now reads [date] instead, and moves on its own
 * when the date does.
 *
 * **Nothing here keeps time.** [refresh] re-reads the clock, and it is called from outside: by
 * the activity when it comes to the front, and by the system's time broadcasts while it is there.
 * A timer sleeping until the next midnight would have done the same on a phone and would never
 * finish in a test, where the clock is fixed and virtual time runs ahead looking for the date to
 * change. Asking the clock again costs nothing, and [date] only emits when the day is different.
 */
@Singleton
class Today
    @Inject
    constructor(
        private val clock: Clock,
    ) {
        private val state = MutableStateFlow(LocalDate.now(clock))

        val date: StateFlow<LocalDate> = state.asStateFlow()

        fun refresh() {
            state.value = LocalDate.now(clock)
        }
    }
