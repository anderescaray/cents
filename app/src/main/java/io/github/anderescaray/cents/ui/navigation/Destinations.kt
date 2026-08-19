package io.github.anderescaray.cents.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategoryKind
import kotlinx.serialization.Serializable

/**
 * Type-safe routes. Each destination is a serializable object, so navigating is a function
 * call the compiler checks rather than a string that fails at runtime.
 */
sealed interface Destination {
    @Serializable
    data object Transactions : Destination

    @Serializable
    data object Accounts : Destination

    @Serializable
    data object Reports : Destination

    @Serializable
    data object Settings : Destination

    /**
     * The movement form, adding when [transactionId] is [TransactionForm.NEW] and editing
     * an existing movement otherwise. One screen for both: the fields are identical, and a
     * second copy of them would be a second place for them to drift.
     */
    @Serializable
    data class TransactionForm(
        val transactionId: Long = NEW,
    ) : Destination {
        companion object {
            const val NEW = 0L
        }
    }

    @Serializable
    data object Categories : Destination

    /** [categoryId] is [CategoryEdit.NEW] when the screen is creating one. */
    @Serializable
    data class CategoryEdit(
        val categoryId: Long = NEW,
        val kind: CategoryKind = CategoryKind.EXPENSE,
    ) : Destination {
        companion object {
            const val NEW = 0L
        }
    }
}

/** A tab in the bottom bar, tying a destination to how it is presented. */
enum class TopLevelTab(
    val destination: Destination,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    TRANSACTIONS(Destination.Transactions, R.string.tab_transactions, R.drawable.ic_tab_transactions),
    ACCOUNTS(Destination.Accounts, R.string.tab_accounts, R.drawable.ic_tab_accounts),
    REPORTS(Destination.Reports, R.string.tab_reports, R.drawable.ic_tab_reports),
    SETTINGS(Destination.Settings, R.string.tab_settings, R.drawable.ic_tab_settings),
}
