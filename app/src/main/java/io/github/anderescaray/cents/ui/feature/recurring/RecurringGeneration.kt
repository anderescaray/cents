package io.github.anderescaray.cents.ui.feature.recurring

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.repository.RecurringRuleRepository
import kotlinx.coroutines.launch
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
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) viewModel.generate()
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
    }
