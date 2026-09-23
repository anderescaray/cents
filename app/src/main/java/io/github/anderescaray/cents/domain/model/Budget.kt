package io.github.anderescaray.cents.domain.model

/** A monthly limit on what one expense category may take (PRD §4.4, `DECISIONS.md` D26). */
data class Budget(
    val id: Long,
    val categoryId: Long,
    val limit: Money,
) {
    companion object {
        const val NEW = 0L
    }
}
