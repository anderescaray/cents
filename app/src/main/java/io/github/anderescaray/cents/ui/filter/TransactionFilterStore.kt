package io.github.anderescaray.cents.ui.filter

import io.github.anderescaray.cents.domain.model.TransactionFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the transaction list is currently narrowed to.
 *
 * It lives above both screens because the filter is *chosen* on one and *obeyed* on
 * another: the list holds the search field and shows the result, the filter form holds
 * everything else. Passing it through the navigation route instead would mean serialising a
 * growing object into a URL and re-deriving it on the way back, which is a lot of machinery
 * for state that only has to outlive a push and a pop.
 *
 * **Deliberately in memory only.** A filter that survives closing the app is a saved view —
 * a different, larger feature — and until it exists, reopening Cents to a list that silently
 * hides most of the movements would read as data loss rather than as a filter still being on.
 */
@Singleton
class TransactionFilterStore
    @Inject
    constructor() {
        private val state = MutableStateFlow(TransactionFilter.None)

        val filter: StateFlow<TransactionFilter> = state.asStateFlow()

        /**
         * The search field, which is the one part of the filter that lives on the list
         * itself. Kept here rather than in the list view model so that going to the filter
         * form and back does not quietly discard what was typed.
         */
        fun setQuery(query: String) = state.update { it.copy(query = query) }

        /**
         * Replaces everything the form owns.
         *
         * The search text is normally left alone, because it is typed on the list and the
         * form has no field for it — applying a filter must not silently empty something
         * visible behind it. [clearQuery] is the one exception, and it is what "show
         * everything again" means: that row promises everything, so it has to include the
         * text too.
         */
        fun apply(
            filter: TransactionFilter,
            clearQuery: Boolean = false,
        ) = state.update { filter.copy(query = if (clearQuery) "" else it.query) }

        /** Back to everything, search text included: one action, one obvious result. */
        fun clear() {
            state.value = TransactionFilter.None
        }
    }
