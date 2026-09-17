package io.github.anderescaray.cents.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.ui.components.CentsButton
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * What stands between the app and whoever is holding the phone.
 *
 * **It renders nothing of [content] while shut** — not blurred, not behind a scrim, not there at
 * all. A lock that covers the app still composes it, and a screenshot in the recents list, an
 * accessibility service or a stray recomposition can each find what is underneath. The cheapest
 * way to not show something is to not draw it.
 *
 * It also shows nothing at all until the setting has been read, which is a frame or two. Assuming
 * *open* for that frame would flash a ledger at exactly the person this exists to stop.
 */
@Composable
fun AppLockGate(
    viewModel: AppLockViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WatchLifecycle(viewModel)
    AskWhenShut(state = state, viewModel = viewModel)

    when {
        !state.loaded -> Curtain()
        state.isShut -> LockScreen(onUnlock = viewModel::onRetry, offersRetry = state.offersRetry)
        else -> content()
    }
}

/**
 * Tells the lock when the app leaves and comes back.
 *
 * `ON_STOP` rather than `ON_PAUSE`: a dialog or the system's own prompt pauses the activity
 * without the app ever leaving the screen, and locking on that would mean re-locking behind
 * every alert in the app.
 */
@Composable
private fun WatchLifecycle(viewModel: AppLockViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> viewModel.onBackgrounded()
                    Lifecycle.Event.ON_START -> viewModel.onForegrounded()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Puts the prompt up whenever the app is shut and not already asking.
 *
 * Driven from the state rather than from a tap, so the owner arrives at an unlocked app instead
 * of at a button they have to press first — and so the prompt comes back by itself after a
 * dismissal is retried, without a second path through the same code.
 */
@Composable
private fun AskWhenShut(
    state: AppLockUiState,
    viewModel: AppLockViewModel,
) {
    val context = LocalContext.current
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val cancel = stringResource(R.string.action_cancel)

    LaunchedEffect(state.shouldAsk) {
        if (!state.shouldAsk) return@LaunchedEffect
        val activity = context as? FragmentActivity ?: return@LaunchedEffect
        viewModel.onAsking()
        val unlocked = BiometricAuthenticator.authenticate(activity, title, subtitle, cancel)
        if (unlocked) viewModel.onUnlocked() else viewModel.onAskingFinished()
    }
}

/** Nothing, in the app's own colour: shown for the frame before the setting has been read. */
@Composable
private fun Curtain() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground),
    )
}

/**
 * What is on screen while the app is shut: the app's name, why nothing is showing, and a way to
 * ask again for somebody who dismissed the prompt.
 */
@Composable
private fun LockScreen(
    onUnlock: () -> Unit,
    offersRetry: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CentsTheme.colors.groupedBackground)
                .testTag(LOCK_SCREEN_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingL),
            modifier = Modifier.padding(horizontal = CentsDimens.screenMargin),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = CentsTheme.typography.largeTitle,
                color = CentsTheme.colors.label,
            )
            Text(
                text = stringResource(R.string.lock_locked_message),
                style = CentsTheme.typography.body,
                color = CentsTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            // Offered only once the prompt is out of the way. Behind it, it would be a button
            // nobody can reach and a second way to raise a prompt that is already up.
            if (offersRetry) {
                CentsButton(text = stringResource(R.string.lock_unlock), onClick = onUnlock)
            }
        }
    }
}

internal const val LOCK_SCREEN_TAG = "appLockScreen"
