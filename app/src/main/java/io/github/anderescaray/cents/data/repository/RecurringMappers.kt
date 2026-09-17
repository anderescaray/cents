package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.entity.RecurringRuleEntity
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.RecurringRule
import java.time.Instant

/**
 * A rule crossing the boundary between Room and the domain.
 *
 * Its own file rather than another pair in `Mappers.kt`, which is at detekt's limit. The split
 * is by subject, the way the investment and backup mappers already are, so the file a rule's
 * columns live in is the one named after rules.
 */
fun RecurringRuleEntity.toDomain(): RecurringRule =
    RecurringRule(
        id = id,
        name = name,
        accountId = accountId,
        categoryId = categoryId,
        kind = kind,
        amount = Money(amountMinor, currency),
        frequency = frequency,
        intervalDays = intervalDays,
        startDate = startDate,
        endDate = endDate,
        mode = mode,
        active = active,
        lastGenerated = lastGenerated,
    )

fun RecurringRule.toEntity(createdAt: Instant): RecurringRuleEntity =
    RecurringRuleEntity(
        id = id,
        name = name,
        accountId = accountId,
        categoryId = categoryId,
        kind = kind,
        amountMinor = amount.minorUnits,
        currency = amount.currency,
        frequency = frequency,
        intervalDays = intervalDays,
        startDate = startDate,
        endDate = endDate,
        mode = mode,
        active = active,
        lastGenerated = lastGenerated,
        createdAt = createdAt,
    )
