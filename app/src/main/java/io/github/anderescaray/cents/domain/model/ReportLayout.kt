package io.github.anderescaray.cents.domain.model

/**
 * How the owner arranged the Reports tab: which cards come first, and which are hidden.
 *
 * Cards are named by a stable key rather than an index, so a card added in a later version slots
 * in at the end of what was saved instead of shuffling the owner's order, and a card that no
 * longer exists is simply dropped. That is what [arrange] does, and it is the only way the screen
 * reads the layout.
 */
data class ReportLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    /**
     * The cards that exist, [known] in their default order, put in the owner's order: saved ones
     * first as saved, then any new ones in their default place at the end. Hidden ones are kept —
     * the customise screen lists them too — and it is the caller that leaves them out.
     */
    fun arrange(known: List<String>): List<String> {
        val saved = order.filter { it in known }.distinct()
        return saved + known.filterNot { it in saved }
    }

    fun isVisible(key: String): Boolean = key !in hidden

    /** [key] one place earlier (or later, for a positive [by]), among [known] in their arranged order. */
    fun move(
        key: String,
        by: Int,
        known: List<String>,
    ): ReportLayout {
        val arranged = arrange(known).toMutableList()
        val from = arranged.indexOf(key)
        val to = from + by
        if (from < 0 || to !in arranged.indices) return this
        arranged.add(to, arranged.removeAt(from))
        return copy(order = arranged)
    }

    fun withVisible(
        key: String,
        visible: Boolean,
    ): ReportLayout = copy(hidden = if (visible) hidden - key else hidden + key)
}
