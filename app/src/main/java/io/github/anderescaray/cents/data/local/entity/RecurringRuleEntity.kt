package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.anderescaray.cents.domain.model.Frequency
import io.github.anderescaray.cents.domain.model.RuleMode
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.Instant
import java.time.LocalDate

/**
 * A movement that repeats: a subscription, a salary, the rent.
 *
 * **Nothing points back from the movements.** That is rule 8 in the schema rather than in a
 * comment: a rule must never be able to take its history with it. The link runs the other way
 * — `transactions.ruleId` — and it is `SET NULL`, so a rule that is genuinely deleted leaves its
 * movements standing, orphaned from a template rather than removed with it.
 *
 * The two keys going out differ, and the difference is whether the rule survives losing them.
 * [accountId] is `RESTRICT`, because a rule with no account cannot record anything: the account
 * removal flow refuses while a rule points at one, the same refusal a holding earns. [categoryId]
 * is nullable and therefore `SET_NULL` — a rule whose category is deleted keeps working and its
 * movements arrive unclassified, which is the state an uncategorised movement is already in.
 *
 * A rule is only ever *deleted* when it generated nothing; one that did is **deactivated**,
 * which is the same bargain categories strike and the one PRD §4.4 insists on.
 */
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["active"]),
    ],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val accountId: Long,
    val categoryId: Long? = null,
    val kind: TransactionKind,
    /** Always positive, in the smallest unit of [currency] — as everywhere else (rule 1). */
    val amountMinor: Long,
    val currency: String,
    val frequency: Frequency,
    /** Only meaningful for [Frequency.EVERY_N_DAYS]. */
    val intervalDays: Int? = null,
    /** The anchor as well as the beginning: every occurrence is counted from here. */
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val mode: RuleMode,
    val active: Boolean = true,
    /** The last occurrence written, so a catch-up cannot write it twice. */
    val lastGenerated: LocalDate? = null,
    val createdAt: Instant,
)
