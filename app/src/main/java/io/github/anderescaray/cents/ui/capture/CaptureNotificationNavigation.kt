package io.github.anderescaray.cents.ui.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Opens the movement a capture notification was tapped for, once.
 *
 * Keyed on the id, so a second notification tapped while the app is open lands too, through
 * `onNewIntent`. [onOpened] is where the caller forgets the id, so a rotation or a return from the
 * background does not open the same movement again.
 */
@Composable
internal fun OpenCapturedFromNotification(
    transactionId: Long?,
    onOpenTransaction: (Long) -> Unit,
    onOpened: () -> Unit,
) {
    LaunchedEffect(transactionId) {
        if (transactionId != null) {
            onOpenTransaction(transactionId)
            onOpened()
        }
    }
}
