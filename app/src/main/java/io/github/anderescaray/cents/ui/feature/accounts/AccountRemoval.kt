package io.github.anderescaray.cents.ui.feature.accounts

import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where the owner is in deciding what "delete" means for this account.
 *
 * A category can be detached from a movement and the movement survives, unclassified. An
 * account cannot: `transactions.accountId` is `RESTRICT`, and a movement with no account is
 * not a movement. So deleting an account that holds history is not one operation with an
 * obvious meaning — it is a question, and this is the shape of asking it (`DECISIONS.md` D7).
 */
enum class RemovalStep {
    /** Nothing is being asked. */
    NONE,

    /** No movements live in it, so one yes/no is the whole question. */
    CONFIRM_EMPTY,

    /** It holds movements: the sheet offers the two things delete could honestly mean. */
    CHOOSE,

    /** Which account the movements move into before this one goes. */
    PICK_TARGET,

    /** The only path in the app that destroys recorded history, asked again on its own. */
    CONFIRM_DESTRUCTIVE,

    /**
     * It holds investments, and none of the three removals above touches them.
     *
     * This is a refusal rather than a question, and it is the one place in this flow where
     * that is the honest answer: `holdings.accountId` is `RESTRICT`, a holding cannot be
     * detached from its account the way a movement can be moved to another one, and nothing
     * here is going to delete somebody's recorded trades as a side effect of tidying up an
     * account. So the owner is told what stands in the way and where to go and deal with it.
     */
    BLOCKED_BY_HOLDINGS,

    /**
     * A recurring rule records into it, and the same reasoning applies.
     *
     * `recurring_rules.accountId` is `RESTRICT`, and a rule cannot be moved to another account
     * by this flow any more than a holding can. Told rather than attempted, so the owner gets
     * a sentence naming what is in the way instead of a constraint violation — and stopping a
     * rule is not enough, because a stopped rule is still a row pointing here.
     */
    BLOCKED_BY_RULES,

    /**
     * The database refused. Both foreign keys are `RESTRICT`, so a movement recorded
     * between counting and confirming turns a legitimate delete into a constraint
     * violation — rare, and worth saying out loud rather than leaving the row sitting
     * there as if the gesture had missed.
     */
    FAILED,
}

/** The account being asked about, and how far the question has got. */
data class AccountRemovalState(
    val accountId: Long? = null,
    val name: String = "",
    val step: RemovalStep = RemovalStep.NONE,
    val transactionCount: Int = 0,
    /**
     * How many movements the destructive path actually removes.
     *
     * The same as [transactionCount] until the account is one side of a transfer: a transfer
     * goes whole, so the leg living in the *other* account goes too, and that account's
     * balance changes. Reassignment moves only what is inside this account, so it keeps using
     * [transactionCount] — the two numbers are two different questions, and answering both
     * with one of them is how a confirmation ends up naming the wrong amount of history.
     */
    val removedCount: Int = 0,
    /** Investments held in it. Any at all is what [RemovalStep.BLOCKED_BY_HOLDINGS] says. */
    val holdingCount: Int = 0,
    /** Recurring rules recording into it, active or stopped — [RemovalStep.BLOCKED_BY_RULES]. */
    val ruleCount: Int = 0,
    /** Accounts the movements could move into: active, not this one, same currency. */
    val reassignTargets: List<Account> = emptyList(),
    /**
     * Per target, the transfers it has with this account. Moving into it leaves each of them
     * with both legs in one account, a row reading *Transfer to Savings* inside Savings, so the
     * sheet says so under that target rather than letting it be found afterwards.
     */
    val transfersWithTarget: Map<Long, Int> = emptyMap(),
) {
    /** True while the owner is being asked something about deleting this account. */
    val asking: Boolean get() = step != RemovalStep.NONE

    /** The destructive path reaches into other accounts, so it has to say so. */
    val removalReachesTransfers: Boolean get() = removedCount > transactionCount

    /**
     * Reassignment needs somewhere to reassign to. When there is nowhere, the choice is
     * still shown — disabled, with the reason — rather than quietly dropped.
     */
    val canReassign: Boolean get() = reassignTargets.isNotEmpty()
}

/**
 * "Delete this account" as a question that can be asked from anywhere.
 *
 * An empty account just goes. One that holds movements cannot simply disappear — its
 * movements would go with it — so the owner is asked which they mean: move the history into
 * another account and delete this one, or delete both. The second is the only operation in
 * the app that destroys recorded history, so it is reached through a sheet and then a second
 * confirmation that says how many movements are at stake (`DECISIONS.md` D7).
 *
 * **One that holds investments is refused outright**, which is a third answer rather than a
 * variation on those two. A movement can be moved to another account; a holding cannot, and
 * deleting one would take its recorded trades with it — a decision that belongs to the person
 * looking at the portfolio, not to somebody tidying up an account list.
 *
 * It lives outside the editor because the editor is no longer the only way in: a swipe on
 * the list asks the same thing, and a second implementation of this sequence is exactly how
 * one of the two ends up missing a confirmation.
 *
 * Archiving is not here. It is a separate action rather than a consolation prize hidden
 * behind "delete": it means keep everything and stop offering the account.
 */
class AccountRemoval(
    private val repository: AccountRepository,
    private val scope: CoroutineScope,
    private val onRemoved: () -> Unit = {},
) {
    private val question = MutableStateFlow(AccountRemovalState())
    val state: StateFlow<AccountRemovalState> = question.asStateFlow()

    /**
     * Opens the question, after reading how many movements are actually at stake and which
     * accounts could take them. Both are read now rather than trusted from whatever loaded
     * the list: the difference between them is the difference between deleting an empty
     * account and deleting a year of history.
     */
    fun request(
        id: Long,
        name: String,
    ) {
        scope.launch {
            val count = repository.countTransactions(id)
            val removed = repository.countTransactionsRemovedWith(id)
            val holdings = repository.countHoldings(id)
            val rules = repository.countRules(id)
            val targets = reassignTargets(id)
            val transfers =
                targets
                    .associate { it.id to repository.countTransfersBetween(id, it.id) }
                    .filterValues { it > 0 }
            question.value =
                AccountRemovalState(
                    accountId = id,
                    name = name,
                    // Holdings are checked first because they beat both other answers: with
                    // investments in it there is no version of "delete" this flow can carry
                    // out, so offering the choice would be offering something that ends in a
                    // constraint violation and a message about trying again.
                    step =
                        when {
                            holdings > 0 -> RemovalStep.BLOCKED_BY_HOLDINGS
                            rules > 0 -> RemovalStep.BLOCKED_BY_RULES
                            count == 0 -> RemovalStep.CONFIRM_EMPTY
                            else -> RemovalStep.CHOOSE
                        },
                    transactionCount = count,
                    removedCount = removed,
                    holdingCount = holdings,
                    ruleCount = rules,
                    reassignTargets = targets,
                    transfersWithTarget = transfers,
                )
        }
    }

    fun request(account: Account) = request(account.id, account.name)

    fun cancel() {
        question.value = AccountRemovalState()
    }

    /** Deletes an account nothing lives in. */
    fun confirmEmpty() {
        val id = question.value.accountId ?: return
        // A movement could have landed in the account between counting and confirming,
        // and the foreign key would refuse the delete: report that rather than letting
        // it escape the coroutine.
        finish { repository.deleteAccount(id) }
    }

    /** Step two of the harmless path: which account the movements move into. */
    fun chooseReassign() {
        if (!question.value.canReassign) return
        question.update { it.copy(step = RemovalStep.PICK_TARGET) }
    }

    /** Step two of the destructive path: ask again, alone, naming what is lost. */
    fun chooseDestructive() = question.update { it.copy(step = RemovalStep.CONFIRM_DESTRUCTIVE) }

    /** Moves every movement into [targetId] and then deletes this account. */
    fun reassignTo(targetId: Long) {
        val id = question.value.accountId ?: return
        if (targetId == id) return
        finish { repository.reassignAndDelete(id = id, targetId = targetId) }
    }

    /** Deletes the account and the movements in it. There is no undo behind this. */
    fun confirmDestructive() {
        val id = question.value.accountId ?: return
        finish { repository.deleteAccountWithTransactions(id) }
    }

    /**
     * Runs one of the three removals and says what happened, without throwing.
     *
     * A failure stays in this state rather than being handed to the screen: it belongs to
     * the question that was being asked, it is answered by acknowledging it, and it goes
     * when the alert does. A flag on a list's state would outlive all three.
     */
    private fun finish(removal: suspend () -> Unit) {
        scope.launch {
            val removed = runCatching { removal() }
            if (removed.isSuccess) {
                question.value = AccountRemovalState()
                onRemoved()
            } else {
                question.update { it.copy(step = RemovalStep.FAILED) }
            }
        }
    }

    /**
     * Where the movements could go: another account, still active, in the same currency.
     *
     * The currency filter is not fussiness. A movement keeps the currency it was recorded
     * in, and an account's balance sums its movements under the account's own currency — so
     * moving euros into a dollar account would not convert them, it would silently make that
     * balance a sum of two currencies (rule 2).
     *
     * The currency comes from the database rather than from a screen: a form carries the
     * device's currency until it has loaded the account's, and filtering on that can offer
     * the wrong accounts — or, when the two differ, none at all, which reads as "there is
     * nowhere to move these" when there plainly is.
     */
    private suspend fun reassignTargets(id: Long): List<Account> {
        val currency = repository.findById(id)?.currency ?: return emptyList()
        return repository
            .observeAccounts()
            .first()
            .filter { it.id != id && it.currency == currency }
    }
}
