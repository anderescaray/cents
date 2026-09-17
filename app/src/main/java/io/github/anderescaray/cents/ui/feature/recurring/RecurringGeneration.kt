package io.github.anderescaray.cents.ui.feature.recurring

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import io.github.anderescaray.cents.ui.notifications.ReminderWorker
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * Writes whatever has fallen due, every time the app comes to the front.
 *
 * **On every start, not only on a cold one.** A phone that is never actually closed would
 * otherwise go days without the rent appearing, and "my subscriptions are recorded" would
 * quietly depend on how the owner happens to use their launcher.
 *
 * Running it that often is only safe because generating is idempotent: each rule records how far
 * it has got, in the same database transaction as the movements it made, so a second run finds
 * nothing due. That property lives in `RoomRecurringRuleRepository`, and this composable is the
 * reason it has to be true rather than merely tidy.
 *
 * It sits inside the lock, so nothing is generated until the phone's owner has proved who they
 * are — the charges are a few seconds later than they could be, and no work happens on behalf of
 * somebody who has not got in.
 */
@Composable
fun GenerateDueMovements(viewModel: RecurringGenerationViewModel = hiltViewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    viewModel.generate()
                    viewModel.scheduleReminders(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@HiltViewModel
class RecurringGenerationViewModel
    @Inject
    constructor(
        private val repository: RecurringRuleRepository,
        private val clock: Clock,
    ) : ViewModel() {
        /**
         * Catches up, and says nothing about it.
         *
         * Deliberately silent: the movements appear in the list, which is where the owner would
         * look anyway, and a banner announcing that the app did the thing it exists to do would
         * be in the way every single morning. A failure is silent too — the watermark is
         * untouched, so the next start tries again.
         */
        fun generate() {
            viewModelScope.launch { runCatching { repository.generateDue() } }
        }

        /**
         * Makes sure tomorrow morning's look at the reminders is booked.
         *
         * Booked from here rather than from `Application.onCreate`, and the reason is worth
         * recording: initialising WorkManager at application start pulls it into **every**
         * Robolectric test, including the ones about migrations, which have no business knowing
         * it exists. Enqueuing the same unique work again is free, so doing it whenever the app
         * comes to the front is what keeps it booked across updates and reboots.
         */
        fun scheduleReminders(context: Context) {
            runCatching { ReminderWorker.schedule(context, clock) }
        }
    }
