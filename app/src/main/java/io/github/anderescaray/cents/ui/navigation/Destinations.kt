package io.github.anderescaray.cents.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.TransactionKind
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Type-safe routes. Each destination is a serializable object, so navigating is a function
 * call the compiler checks rather than a string that fails at runtime.
 */
sealed interface Destination {
    /**
     * The canvas of bubbles the app opens on, and the floor of every stack: a section is
     * pushed on top of it, so back from a section root returns here rather than leaving the
     * app. It is not a tab and never appears in the bar — see
     * [`0002-bubble-launcher.md`](../../../../../../../../docs/decisions/0002-bubble-launcher.md).
     */
    @Serializable
    data object Home : Destination

    @Serializable
    data object Transactions : Destination

    @Serializable
    data object Accounts : Destination

    @Serializable
    data object Investments : Destination

    @Serializable
    data object Reports : Destination

    @Serializable
    data object Settings : Destination

    /**
     * The movement form, adding when [transactionId] is [TransactionForm.NEW] and editing
     * an existing movement otherwise. One screen for both: the fields are identical, and a
     * second copy of them would be a second place for them to drift.
     *
     * [amountInput] and [kind] are what the owner had typed and chosen on the *transfer* form
     * before deciding this was an ordinary movement after all. They are ignored when opening
     * something that already exists, which carries its own.
     */
    @Serializable
    data class TransactionForm(
        val transactionId: Long = NEW,
        val amountInput: String = "",
        val kind: TransactionKind = TransactionKind.EXPENSE,
    ) : Destination {
        companion object {
            const val NEW = 0L
        }
    }

    /**
     * The transfer form: money moving between two of the owner's own accounts. Adding when
     * [transferId] is [TransferForm.NEW], editing an existing transfer otherwise.
     *
     * Its own route rather than a mode of [TransactionForm], because a transfer asks for two
     * accounts and no category — one form serving both would be a form made of conditionals.
     * The two are stitched together by the control at the top of each: choosing a kind the
     * current screen cannot record navigates to the one that can, **replacing** it rather than
     * stacking on it, so back from either goes where the owner came from rather than to the
     * form they just left.
     *
     * [amountInput] is what has been typed so far, in [io.github.anderescaray.cents.ui.util.AmountEntry]'s
     * canonical form. It travels across that switch so that realising halfway through that
     * this is a transfer does not cost the amount.
     */
    @Serializable
    data class TransferForm(
        val transferId: Long = NEW,
        val amountInput: String = "",
    ) : Destination {
        companion object {
            const val NEW = 0L
        }
    }

    /**
     * The filter form. It carries no arguments: what is being filtered lives in
     * `TransactionFilterStore`, because a filter is edited on this screen and obeyed on the
     * one behind it, and serialising a growing object into a route would be a lot of
     * machinery for state that only has to survive a push and a pop.
     */
    @Serializable
    data object TransactionFilters : Destination

    @Serializable
    data class AccountDetail(
        val accountId: Long,
    ) : Destination

    /** [accountId] is [AccountEdit.NEW] when the screen is creating one. */
    @Serializable
    data class AccountEdit(
        val accountId: Long = NEW,
    ) : Destination {
        companion object {
            const val NEW = 0L
        }
    }

    /** One holding: its position, its price and the trades behind it. */
    @Serializable
    data class HoldingDetail(
        val holdingId: Long,
    ) : Destination

    /** [holdingId] is [HoldingEdit.NEW] when the screen is creating one. */
    @Serializable
    data class HoldingEdit(
        val holdingId: Long = NEW,
    ) : Destination {
        companion object {
            const val NEW = 0L
        }
    }

    /**
     * A purchase or a sale. [tradeId] is [TradeForm.NEW] when recording one, and the holding
     * is always known: a trade with no holding is not a record of anything.
     */
    @Serializable
    data class TradeForm(
        val holdingId: Long,
        val tradeId: Long = NEW,
        /** Opens straight on the correction segment: the owner is fixing a number, not trading. */
        val correcting: Boolean = false,
    ) : Destination {
        companion object {
            const val NEW = 0L
        }
    }

    /**
     * Every open holding's price, on one screen.
     *
     * No arguments: it is about the whole portfolio rather than about one holding, which is the
     * entire reason it exists beside the per-holding editor rather than replacing it.
     */
    @Serializable
    data object PriceUpdate : Destination

    @Serializable
    data object Categories : Destination

    /** Everything that repeats, and what it all comes to per month. */
    @Serializable
    data object Subscriptions : Destination

    /**
     * The bills waiting for a figure: every variable-amount rule that has fallen due.
     *
     * No arguments, like the price screen it is shaped after — it is about all of them at once,
     * which is the reason it exists rather than a form per bill.
     */
    @Serializable
    data object PendingCharges : Destination

    /** [ruleId] is [RuleForm.NEW] when the screen is defining one. */
    @Serializable
    data class RuleForm(
        val ruleId: Long = NEW,
    ) : Destination {
        companion object {
            const val NEW = 0L
        }
    }

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

/**
 * A tab in the bottom bar, tying a destination to how it is presented — and to the screens
 * that can only be reached from inside it.
 *
 * [owns] exists because a pushed screen otherwise carries no record of the tab it belongs to,
 * and two things need to know: the bar, which has to stay lit on *Accounts* while an account's
 * detail screen is open, and the slide, which has to travel backwards when you switch to a tab
 * whose saved stack has that detail screen on top.
 *
 * **A screen reachable from more than one tab is listed by none of them.** The movement form
 * opens from the list and from an account, and the category editor from Settings and from the
 * form: naming an owner for those would be picking one of two truths. They fall back to the tab
 * the owner was already in, which is the right answer in both cases and is why the tab is
 * remembered rather than derived. A new destination nobody claims behaves the same way, so
 * forgetting to list one degrades to today's behaviour rather than to a wrong tab.
 */
enum class TopLevelTab(
    val destination: Destination,
    val owns: List<KClass<out Destination>>,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    TRANSACTIONS(
        destination = Destination.Transactions,
        owns = listOf(Destination.TransactionFilters::class),
        labelRes = R.string.tab_transactions,
        iconRes = R.drawable.ic_tab_transactions,
    ),
    ACCOUNTS(
        destination = Destination.Accounts,
        owns = listOf(Destination.AccountDetail::class),
        labelRes = R.string.tab_accounts,
        iconRes = R.drawable.ic_tab_accounts,
    ),
    INVESTMENTS(
        destination = Destination.Investments,
        owns =
            listOf(
                Destination.HoldingDetail::class,
                Destination.HoldingEdit::class,
                Destination.TradeForm::class,
                Destination.PriceUpdate::class,
            ),
        labelRes = R.string.tab_investments,
        iconRes = R.drawable.ic_tab_investments,
    ),
    REPORTS(
        destination = Destination.Reports,
        owns = emptyList(),
        labelRes = R.string.tab_reports,
        iconRes = R.drawable.ic_tab_reports,
    ),
    SETTINGS(
        destination = Destination.Settings,
        owns =
            listOf(
                Destination.Categories::class,
                Destination.Subscriptions::class,
                Destination.RuleForm::class,
                Destination.PendingCharges::class,
            ),
        labelRes = R.string.tab_settings,
        iconRes = R.drawable.ic_tab_settings,
    ),
}

/**
 * Every route this tab answers for: the tab's own destination, then the screens pushed inside
 * it. Kept as one list so that "which tab is this screen in" is asked in exactly one place, and
 * so it can be tested without a navigation graph.
 */
val TopLevelTab.routes: List<KClass<out Destination>>
    get() = listOf(destination::class) + owns
