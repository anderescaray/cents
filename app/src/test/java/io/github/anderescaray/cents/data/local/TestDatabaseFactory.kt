package io.github.anderescaray.cents.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.anderescaray.cents.data.local.entity.AccountEntity
import io.github.anderescaray.cents.data.local.entity.CategoryEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.CategoryKind
import io.github.anderescaray.cents.domain.model.TransactionKind
import java.time.Instant
import java.time.LocalDate

/** An in-memory database, discarded when the test ends. */
fun createTestDatabase(): CentsDatabase =
    Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CentsDatabase::class.java,
        ).build()

private val FIXED_INSTANT: Instant = Instant.parse("2026-08-18T10:00:00Z")

fun account(
    name: String = "Checking",
    currency: String = "EUR",
    openingBalanceMinor: Long = 0,
    type: AccountType = AccountType.BANK,
    archived: Boolean = false,
) = AccountEntity(
    name = name,
    type = type,
    currency = currency,
    openingBalanceMinor = openingBalanceMinor,
    colorArgb = 0xFF00796B.toInt(),
    archived = archived,
    createdAt = FIXED_INSTANT,
)

fun category(
    name: String = "Groceries",
    kind: CategoryKind = CategoryKind.EXPENSE,
    parentId: Long? = null,
) = CategoryEntity(
    name = name,
    kind = kind,
    colorArgb = 0xFFEF6C00.toInt(),
    parentId = parentId,
    createdAt = FIXED_INSTANT,
)

fun transaction(
    accountId: Long,
    categoryId: Long? = null,
    kind: TransactionKind = TransactionKind.EXPENSE,
    amountMinor: Long = 1_000,
    currency: String = "EUR",
    date: LocalDate = LocalDate.of(2026, 8, 18),
) = TransactionEntity(
    accountId = accountId,
    categoryId = categoryId,
    kind = kind,
    amountMinor = amountMinor,
    currency = currency,
    date = date,
    createdAt = FIXED_INSTANT,
    updatedAt = FIXED_INSTANT,
)
