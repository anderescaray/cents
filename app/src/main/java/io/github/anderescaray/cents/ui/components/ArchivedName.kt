package io.github.anderescaray.cents.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.anderescaray.cents.R

/**
 * A name as a list offers it: as it is, or said to be archived. An archived account, category or
 * group is still offered wherever history points at it, and the owner should see which it is.
 */
@Composable
fun archivedName(
    name: String,
    archived: Boolean,
): String = if (archived) stringResource(R.string.filters_archived, name) else name
