package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Backup
import io.github.anderescaray.cents.domain.model.Budget
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.DueReminder
import io.github.anderescaray.cents.domain.model.Group
import io.github.anderescaray.cents.domain.model.GroupSummary
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PendingCharge
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.Postponement
import io.github.anderescaray.cents.domain.model.RecurringRule
import io.github.anderescaray.cents.domain.model.Reminder
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionDetails
import io.github.anderescaray.cents.domain.model.TransactionFilter
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.model.Transfer
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * @suppress TooManyFunctions — an account's surface is wide because "delete" has three honest
 * meanings (D7) and two things can refuse it, movements and holdings. Splitting the interface
 * would put the counts on one side of a seam and the deletions they gate on the other.
 */
@Suppress("TooManyFunctions")
interface AccountRepository {
    fun observeAccounts(includeArchived: Boolean = false): Flow<List<Account>>

    fun observeAccountsWithBalances(includeArchived: Boolean = false): Flow<List<AccountWithBalance>>

    /** One account and its balance, for the screen that shows only that account. */
    fun observeAccountWithBalance(id: Long): Flow<AccountWithBalance?>

    suspend fun findById(id: Long): Account?

    suspend fun createAccount(account: Account): Long

    suspend fun updateAccount(account: Account)

    suspend fun setArchived(
        id: Long,
        archived: Boolean,
    )

    /**
     * Removes the account outright. Only legitimate while [countTransactions] is zero:
     * with movements in it, the two operations below are what "delete" can honestly mean.
     * Its balance corrections go with it, as its opening balance does.
     */
    suspend fun deleteAccount(id: Long)

    /**
     * Moves every movement into [targetId] and then deletes the account, in one database
     * transaction. Nothing is lost: the history survives under another account. Its balance
     * corrections and its opening balance move too, so [targetId] ends with both balances added
     * together and net worth does not change.
     *
     * [targetId] must be an account in the same currency. A movement keeps the currency it
     * was recorded in, and an account's balance is the sum of its movements labelled with
     * the account's own currency — so a cross-currency move would produce a balance that
     * adds euros to dollars without converting either.
     */
    suspend fun reassignAndDelete(
        id: Long,
        targetId: Long,
    )

    /**
     * Deletes the account **and every movement in it**, in one database transaction, with its
     * balance corrections.
     *
     * This is the one operation in the app that removes recorded history on purpose. It
     * exists because the owner asked for it (`DECISIONS.md` D7): an account added by
     * mistake, and used before the mistake was noticed, otherwise haunts every picker
     * forever. It is never reached without an explicit confirmation that says how many
     * movements go with it, and there is no undo behind it.
     */
    suspend fun deleteAccountWithTransactions(id: Long)

    /** Zero means the account can be deleted outright instead of archived. */
    suspend fun countTransactions(id: Long): Int

    /**
     * How many movements [deleteAccountWithTransactions] would actually remove.
     *
     * More than [countTransactions] when the account is one side of a transfer: the far leg
     * lives in another account and goes too, because half a transfer is money that arrived
     * from nowhere. Those rows change a balance the owner was not deleting, so the
     * confirmation counts them rather than naming only what is inside this account.
     */
    suspend fun countTransactionsRemovedWith(id: Long): Int

    /**
     * How many investments are held in this account.
     *
     * Non-zero means it cannot be deleted at all by this flow: a holding's account is
     * `RESTRICT` like a movement's, but none of the three removals above touches holdings, so
     * every one of them would be refused by the database. The count exists so the owner is
     * told that instead of being offered a delete that cannot work.
     */
    suspend fun countHoldings(id: Long): Int

    /**
     * Recurring rules recording into it, active or stopped.
     *
     * Same argument as [countHoldings]: `RESTRICT`, and no removal in this flow can detach a
     * rule from the account it records into.
     */
    suspend fun countRules(id: Long): Int

    /**
     * Transfers between [id] and [otherId]. Reassigning one into the other leaves each with both
     * legs in the same account: harmless to every balance and report, but worth naming first.
     */
    suspend fun countTransfersBetween(
        id: Long,
        otherId: Long,
    ): Int
}

interface CategoryRepository {
    fun observeCategories(kind: CategoryKind): Flow<List<Category>>

    fun observeAllCategories(): Flow<List<Category>>

    suspend fun findById(id: Long): Category?

    suspend fun createCategory(category: Category): Long

    suspend fun updateCategory(category: Category)

    suspend fun setArchived(
        id: Long,
        archived: Boolean,
    )

    /**
     * Removes the category outright. Only legitimate while [countTransactions] is zero:
     * with transactions attached, archiving is the operation that keeps history intact.
     */
    suspend fun deleteCategory(id: Long)

    /** Stores [orderedIds] as the order categories are listed and offered in. */
    suspend fun reorder(orderedIds: List<Long>)

    /** Zero means the category can be deleted outright instead of archived. */
    suspend fun countTransactions(id: Long): Int
}

@Suppress("TooManyFunctions")
interface TransactionRepository {
    /**
     * The list, narrowed by [filter] — and [TransactionFilter.None], the default, is every
     * movement. One method rather than two, so "the list" and "the filtered list" cannot
     * drift into disagreeing about what a movement's row looks like.
     */
    fun observeTransactions(filter: TransactionFilter = TransactionFilter.None): Flow<List<TransactionDetails>>

    /** How many movements wait in the uncategorized inbox (PRD §4.6). */
    fun observeUncategorizedCount(): Flow<Int>

    /**
     * Gives one movement a category, from the inbox, without going through the form. Returns
     * false when nothing was filed: the movement is gone, or is a transfer leg or a trade's cash.
     */
    suspend fun categorize(
        id: Long,
        categoryId: Long,
    ): Boolean

    fun observeTransactionsForAccount(accountId: Long): Flow<List<Transaction>>

    /** One account's movements, carrying the names its rows have to show. */
    fun observeAccountTransactionDetails(accountId: Long): Flow<List<TransactionDetails>>

    /** Income and expense totals for a period, summed by the database. */
    fun observeSummary(
        from: LocalDate,
        to: LocalDate,
        currency: String,
    ): Flow<PeriodSummary>

    fun observeAccountSummary(
        accountId: Long,
        from: LocalDate,
        to: LocalDate,
        currency: String,
    ): Flow<PeriodSummary>

    suspend fun findById(id: Long): Transaction?

    suspend fun addTransaction(transaction: Transaction): Long

    suspend fun updateTransaction(transaction: Transaction)

    suspend fun deleteTransaction(id: Long)

    /** Both legs of one transfer, for [DeletedTransactionBuffer] when a transfer is deleted. */
    suspend fun findTransferLegs(transferId: Long): List<Transaction>

    /**
     * Restores one or more deleted movements, whole: all rows in one database transaction,
     * keeping their original ids, amounts, accounts, dates and pairing.
     *
     * Used by Undo on the transactions list. When restoring a transfer, both legs must be
     * written together: half a transfer restored is money arriving from nowhere, so a write
     * that fails partway leaves neither leg behind.
     */
    suspend fun restore(transactions: List<Transaction>)

    suspend fun restore(transaction: Transaction) = restore(listOf(transaction))
}

/**
 * Moving money between the owner's own accounts.
 *
 * Its own interface rather than four more methods on [TransactionRepository], because every
 * operation here writes **two rows or none**: a transfer is the pair, and a caller that could
 * reach one leg on its own is a caller that can leave money arriving from nowhere. Nothing in
 * this interface hands out a single leg.
 */
interface TransferRepository {
    /** The transfer behind either of its legs, assembled from both. Null if it is gone. */
    suspend fun findTransfer(transferId: Long): Transfer?

    /**
     * Writes a transfer, whole: both legs in one database transaction, and the pair id
     * returned.
     *
     * Saving an existing one rewrites both legs rather than patching whichever changed — the
     * accounts can have swapped, and a partial update is how a transfer ends up with two
     * outgoing halves.
     */
    suspend fun saveTransfer(transfer: Transfer): Long

    /** Removes both legs. There is no version of this that removes one. */
    suspend fun deleteTransfer(transferId: Long)
}

/**
 * Reading the whole app out, for a file the owner keeps.
 *
 * One method, and deliberately no second one: **there is no import here** (`DECISIONS.md` D15).
 * Restoring asks what happens to what is already on the phone — merge, replace, refuse — and
 * that question belongs to `feat/backup`, where it can be answered on purpose rather than
 * discovered by whoever taps the button first.
 */
interface BackupRepository {
    /** Everything the app holds, read at one moment. */
    suspend fun snapshot(): Backup

    /**
     * Puts a backup back, **replacing everything currently recorded** (`DECISIONS.md` D21).
     *
     * All of it or none of it: one database transaction, so a restore that fails halfway leaves
     * the app as it was rather than as a mixture of two ledgers, which is the one outcome worse
     * than either. Rows keep the ids they had in the file, because a backup is a graph — a
     * movement whose account id was renumbered would point at somebody else's account.
     *
     * It is the only operation in the app that destroys everything, so it is reached through a
     * confirmation that first says what is about to go.
     */
    suspend fun restore(backup: Backup)
}

/**
 * Rules that repeat, and the movements they make.
 *
 * The one method that is not obvious is [generateDue], and it is the one everything rests on:
 * it runs whenever the app opens, so it has to be safe to run again. What it must never do is
 * write a charge twice.
 *
 * Over detekt's limit, and deliberately one interface: the three pending methods are the other
 * half of [generateDue] — what it refuses to guess ends up there — and splitting them off would
 * put one subject behind two names without making either shorter to read.
 */
@Suppress("TooManyFunctions")
interface RecurringRuleRepository {
    fun observeRules(): Flow<List<RecurringRule>>

    fun observeRule(id: Long): Flow<RecurringRule?>

    suspend fun findById(id: Long): RecurringRule?

    suspend fun createRule(rule: RecurringRule): Long

    suspend fun updateRule(rule: RecurringRule)

    /**
     * Stops a rule generating, or starts it again.
     *
     * This is what "delete a subscription" means once it has produced anything (PRD §4.4): it
     * leaves the active list and makes nothing more, and every charge it already made stays
     * exactly where it is. Rule 8, as an operation.
     */
    suspend fun setActive(
        id: Long,
        active: Boolean,
    )

    /**
     * Removes the rule outright. Only legitimate while [countGenerated] is zero — with history
     * behind it, [setActive] is the honest operation.
     */
    suspend fun deleteRule(id: Long)

    /** How many movements it has produced. Zero is what makes a real delete legitimate. */
    suspend fun countGenerated(id: Long): Int

    /**
     * Writes every charge that has fallen due and not yet been written, and says how many.
     *
     * **Safe to call repeatedly**, because it will be: on every launch, and again from whatever
     * schedules it later.
     */
    suspend fun generateDue(): Int

    /**
     * Occurrences of variable-amount rules waiting for a figure, oldest first.
     *
     * The other half of [generateDue]: what it deliberately skips ends up here instead of being
     * guessed at. One per rule — see [PendingCharge].
     */
    fun observePending(): Flow<List<PendingCharge>>

    /**
     * Records a waiting occurrence at the amount the owner has read off the bill.
     *
     * Returns false when [dueDate] is no longer that rule's oldest waiting occurrence, which is
     * what a screen left open while something else answered it looks like. Nothing is written in
     * that case: the alternative is a movement on a date the rule has already moved past, which
     * no later run would ever notice.
     */
    suspend fun recordPending(
        ruleId: Long,
        dueDate: LocalDate,
        amount: Money,
    ): Boolean

    /**
     * Moves past a waiting occurrence without recording anything — the month there was no bill.
     *
     * Same guard and same return as [recordPending].
     */
    suspend fun skipPending(
        ruleId: Long,
        dueDate: LocalDate,
    ): Boolean

    /** Every occurrence currently moved to a later day. */
    fun observePostponements(): Flow<List<Postponement>>

    /**
     * Moves one occurrence to a later day, or moves it again.
     *
     * Returns false, and changes nothing, when `canPostpone` says no: an occurrence already
     * written, a day not later than it, or one that reaches the next occurrence.
     */
    suspend fun postpone(
        ruleId: Long,
        occurrence: LocalDate,
        to: LocalDate,
    ): Boolean

    /** Puts a postponed occurrence back on its own day. */
    suspend fun cancelPostponement(
        ruleId: Long,
        occurrence: LocalDate,
    )

    /** The nudges this rule carries, soonest first. */
    suspend fun remindersFor(ruleId: Long): List<Reminder>

    /**
     * Replaces the rule's reminders with exactly these offsets.
     *
     * Replace rather than add-and-remove, because the editor holds the whole list anyway and
     * reconciling two lists is a way to end up with a reminder nobody asked for. Duplicates and
     * anything already there are collapsed, since the same reminder twice is the same
     * notification twice.
     */
    suspend fun setReminders(
        ruleId: Long,
        offsets: List<Int>,
    )

    /**
     * Every reminder that lands on [today], with the occurrence each one is about.
     *
     * Asked once a day by the worker. A rule that is stopped is silent, and a variable one that
     * has **already been answered** for that occurrence is silent too — being chased about a bill
     * you have just entered is how somebody turns notifications off.
     */
    suspend fun dueReminders(today: LocalDate): List<DueReminder>
}

/** Monthly limits per expense category, and what each category has spent (`DECISIONS.md` D26). */
interface BudgetRepository {
    fun observeBudgets(): Flow<List<Budget>>

    /**
     * What each expense category took between [from] and [to], by category id.
     *
     * The same exclusions every report makes: a transfer leg and a trade's cash are not spending,
     * and have no category anyway. Uncategorized spending has no budget to count against.
     */
    fun observeSpentByCategory(
        from: LocalDate,
        to: LocalDate,
        currency: String,
    ): Flow<Map<Long, Money>>

    suspend fun findById(id: Long): Budget?

    /** Creates it when its id is [Budget.NEW], and replaces the limit otherwise. Returns the id. */
    suspend fun saveBudget(budget: Budget): Long

    suspend fun deleteBudget(id: Long)
}

/** Occasions that cut across categories, and what each one came to (PRD §4.9, `DECISIONS.md` D27). */
interface GroupRepository {
    /** Every group, by name: what the form's picker offers. */
    fun observeGroups(): Flow<List<Group>>

    /**
     * Every group with its totals, **most recent first**: by the last movement in it, and for a
     * group that holds nothing yet by its dates or, failing those, by when it was made — so the
     * trip created this morning is at the top rather than under last year's.
     */
    fun observeSummaries(currency: String): Flow<List<GroupSummary>>

    /** One group's totals, or null once it has been deleted. */
    fun observeSummary(
        id: Long,
        currency: String,
    ): Flow<GroupSummary?>

    /**
     * What one group's movements of [kind] came to per category, largest first, with each one's
     * share precomputed — the ring on the group's screen.
     */
    fun observeByCategory(
        groupId: Long,
        kind: TransactionKind,
        currency: String,
    ): Flow<List<CategorySpending>>

    suspend fun findById(id: Long): Group?

    /**
     * Creates it when its id is [Group.NEW] and replaces it otherwise. Returns the id.
     *
     * The name is trimmed and has to be non-blank; the dates may be absent, but not backwards.
     */
    suspend fun saveGroup(group: Group): Long

    /** Deletes the group and leaves every movement in it standing, without a group. */
    suspend fun deleteGroup(id: Long)
}
