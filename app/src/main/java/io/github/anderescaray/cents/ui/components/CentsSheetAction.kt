package io.github.anderescaray.cents.ui.components

/**
 * One choice on a [CentsActionSheet].
 *
 * [enabled] is false for a choice that exists but cannot be taken right now. It stays on
 * the sheet, greyed, with [reason] underneath saying why — a sheet that changes shape for
 * invisible reasons teaches the owner nothing, and leaves them wondering whether the app
 * forgot the option or decided against it.
 */
data class CentsSheetAction(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val reason: String? = null,
)
