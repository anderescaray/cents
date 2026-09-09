package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.anderescaray.cents.domain.model.QuoteSource
import java.time.Instant

/**
 * The last known price of an instrument, cached.
 *
 * Keyed by **symbol rather than by holding**, which is the whole point of requiring a symbol:
 * the same instrument held in two accounts has one price, and two rows would let the two
 * accounts disagree about what it is worth.
 *
 * [asOf] is when the price was true, not when it was written, so a screen can say how old it
 * is. A stale price shown with its age is the honest answer when a provider is unreachable;
 * inventing a fresh-looking one is the thing `CLAUDE.md` 9 forbids.
 */
@Entity(tableName = "price_quotes")
data class PriceQuoteEntity(
    @PrimaryKey
    val symbol: String,
    val priceMinor: Long,
    val currency: String,
    val asOf: Instant,
    val source: QuoteSource,
)
