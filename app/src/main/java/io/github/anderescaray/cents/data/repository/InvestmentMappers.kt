package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.entity.HoldingEntity
import io.github.anderescaray.cents.data.local.entity.HoldingValueEntity
import io.github.anderescaray.cents.data.local.entity.TradeEntity
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.HoldingValue
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.Trade
import java.time.Instant

/** Translation between the storage shape and the domain shape, for assets, trades and values. */

fun HoldingEntity.toDomain(): Holding =
    Holding(
        id = id,
        accountId = accountId,
        assetClass = assetClass,
        name = name,
        currency = currency,
        note = note,
    )

fun Holding.toEntity(
    createdAt: Instant,
    updatedAt: Instant,
): HoldingEntity =
    HoldingEntity(
        id = id,
        accountId = accountId,
        assetClass = assetClass,
        name = name,
        currency = currency,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun TradeEntity.toDomain(): Trade =
    Trade(
        id = id,
        holdingId = holdingId,
        side = side,
        amount = Money(amountMinor, currency),
        date = date,
        note = note,
        sellsAll = sellsAll,
    )

fun Trade.toEntity(createdAt: Instant): TradeEntity =
    TradeEntity(
        id = id,
        holdingId = holdingId,
        side = side,
        amountMinor = amount.minorUnits,
        sellsAll = sellsAll,
        currency = amount.currency,
        date = date,
        note = note,
        createdAt = createdAt,
    )

fun HoldingValueEntity.toDomain(): HoldingValue =
    HoldingValue(
        id = id,
        holdingId = holdingId,
        date = date,
        value = Money(valueMinor, currency),
    )

fun HoldingValue.toEntity(createdAt: Instant): HoldingValueEntity =
    HoldingValueEntity(
        id = id,
        holdingId = holdingId,
        date = date,
        valueMinor = value.minorUnits,
        currency = value.currency,
        createdAt = createdAt,
    )
