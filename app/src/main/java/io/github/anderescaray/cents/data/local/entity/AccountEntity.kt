package io.github.anderescaray.cents.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.anderescaray.cents.domain.model.AccountType
import java.time.Instant

/**
 * A container of money: a bank account, cash, a card or a broker account.
 *
 * The balance is deliberately **not** a column here. It is derived in SQL from
 * [openingBalanceMinor] plus the movements, because a stored balance drifts out of sync
 * the first time an old transaction is edited.
 */
@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["archived"]),
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    /** ISO-4217 code, e.g. "EUR". Every amount in this account is in this currency. */
    val currency: String,
    /** Balance before the first recorded transaction, in the smallest unit of [currency]. */
    @ColumnInfo(name = "openingBalanceMinor")
    val openingBalanceMinor: Long,
    val colorArgb: Int,
    val iconKey: String? = null,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant,
)
