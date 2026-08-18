package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.dao.AccountDao
import io.github.anderescaray.cents.data.local.dao.CategoryDao
import io.github.anderescaray.cents.data.local.entity.AccountEntity
import io.github.anderescaray.cents.data.local.entity.CategoryEntity
import io.github.anderescaray.cents.domain.model.AccountType
import io.github.anderescaray.cents.domain.model.CategoryKind
import java.time.Clock
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts one account and a handful of categories in place the first time the app runs.
 *
 * Without this the first screen the user meets is an expense form with nothing to pick,
 * which is a dead end. These are ordinary rows: they can be renamed, archived or deleted
 * like any other, and nothing in the app treats them as special.
 */
@Singleton
class DefaultDataSeeder
    @Inject
    constructor(
        private val accountDao: AccountDao,
        private val categoryDao: CategoryDao,
        private val clock: Clock,
    ) {
        suspend fun seedIfEmpty(locale: Locale = Locale.getDefault()) {
            if (accountDao.countAll() > 0) return

            val currency = runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("EUR")
            val now = clock.instant()

            accountDao.insert(
                AccountEntity(
                    name = "Cash",
                    type = AccountType.CASH,
                    currency = currency,
                    openingBalanceMinor = 0,
                    colorArgb = COLOR_CASH,
                    sortOrder = 0,
                    createdAt = now,
                ),
            )

            DEFAULT_EXPENSE_CATEGORIES.forEachIndexed { index, (name, color) ->
                categoryDao.insert(
                    CategoryEntity(
                        name = name,
                        kind = CategoryKind.EXPENSE,
                        colorArgb = color,
                        sortOrder = index,
                        createdAt = now,
                    ),
                )
            }
            DEFAULT_INCOME_CATEGORIES.forEachIndexed { index, (name, color) ->
                categoryDao.insert(
                    CategoryEntity(
                        name = name,
                        kind = CategoryKind.INCOME,
                        colorArgb = color,
                        sortOrder = index,
                        createdAt = now,
                    ),
                )
            }
        }

        private companion object {
            const val COLOR_CASH = 0xFF34C759.toInt()

            val DEFAULT_EXPENSE_CATEGORIES =
                listOf(
                    "Groceries" to 0xFFFF9500.toInt(),
                    "Restaurants" to 0xFFFF375F.toInt(),
                    "Transport" to 0xFF0A84FF.toInt(),
                    "Home" to 0xFF5E5CE6.toInt(),
                    "Health" to 0xFF30D158.toInt(),
                    "Leisure" to 0xFFBF5AF2.toInt(),
                    "Shopping" to 0xFF64D2FF.toInt(),
                    "Other" to 0xFF8E8E93.toInt(),
                )

            val DEFAULT_INCOME_CATEGORIES =
                listOf(
                    "Salary" to 0xFF34C759.toInt(),
                    "Refunds" to 0xFF64D2FF.toInt(),
                    "Other income" to 0xFF8E8E93.toInt(),
                )
        }
    }
