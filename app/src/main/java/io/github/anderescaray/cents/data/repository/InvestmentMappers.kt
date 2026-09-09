package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.entity.HoldingEntity
import io.github.anderescaray.cents.data.local.entity.PriceQuoteEntity
import io.github.anderescaray.cents.data.local.entity.TradeEntity
import io.github.anderescaray.cents.domain.model.Holding
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PriceQuote
import io.github.anderescaray.cents.domain.model.Quantity
import io.github.anderescaray.cents.domain.model.Trade
import java.time.Instant

/**
 * Translation between the storage shape and the domain shape, for holdings, trades and prices.
 *
 * The one thing worth noticing: a trade's currency is a single column, and it becomes the
 * currency of *both* the price and the fee. They cannot differ — a fee charged in another
 * currency would need a rate, which is the subject of PRD 4.5 — and one column is how that
 * stays true rather than being a rule somebody has to remember.
 */

fun HoldingEntity.toDomain(): Holding =
    Holding(
        id = id,
        accountId = accountId,
        assetClass = assetClass,
        symbol = symbol,
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
        symbol = symbol,
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
        quantity = Quantity(quantityScaled),
        pricePerUnit = Money(pricePerUnitMinor, currency),
        fee = Money(feeMinor, currency),
        date = date,
        note = note,
    )

fun Trade.toEntity(createdAt: Instant): TradeEntity =
    TradeEntity(
        id = id,
        holdingId = holdingId,
        side = side,
        quantityScaled = quantity.scaled,
        pricePerUnitMinor = pricePerUnit.minorUnits,
        feeMinor = fee.minorUnits,
        currency = pricePerUnit.currency,
        date = date,
        note = note,
        createdAt = createdAt,
    )

fun PriceQuoteEntity.toDomain(): PriceQuote =
    PriceQuote(
        symbol = symbol,
        price = Money(priceMinor, currency),
        asOf = asOf,
        source = source,
    )
