package io.github.anderescaray.cents.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.anderescaray.cents.domain.model.AssetClass
import java.time.Instant

/**
 * Something held inside an account: a stock, an ETF, an index fund or a crypto asset.
 *
 * The quantity is deliberately **not** a column here, for the same reason an account's balance
 * is not one: it is the sum of the trades behind it, and a stored copy drifts away from them
 * the first time a trade is edited.
 *
 * The unique index on `(accountId, symbol)` is what stops the same instrument being held twice
 * in one account. Two rows for NVDA would split one position in half, and every average, total
 * and profit on the screen would be right about each half and wrong about the holding.
 */
@Entity(
    tableName = "holdings",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["symbol"]),
        Index(value = ["accountId", "symbol"], unique = true),
    ],
)
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val assetClass: AssetClass,
    /** Ticker, ISIN or crypto symbol, upper-cased. What a price is stored against. */
    val symbol: String,
    val name: String,
    /** ISO-4217 code of the currency the instrument is priced in. */
    val currency: String,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
