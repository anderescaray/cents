package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.dao.AccountDao
import io.github.anderescaray.cents.data.local.entity.AccountEntity
import io.github.anderescaray.cents.domain.model.AccountType
import java.time.Clock
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts one account in place the first time the app runs, because a transaction cannot be
 * saved without one and the entry form would otherwise be a dead end. It is an ordinary
 * row: it can be renamed, archived or deleted like any other.
 *
 * **No categories are seeded.** The app does not propose a taxonomy — every category is
 * one the owner created (PRD §4.1, design principle 3). A fresh install starts with an
 * empty list and a link to create the first one.
 */
@Singleton
class DefaultDataSeeder
    @Inject
    constructor(
        private val accountDao: AccountDao,
        private val clock: Clock,
    ) {
        suspend fun seedIfEmpty(locale: Locale = Locale.getDefault()) {
            if (accountDao.countAll() > 0) return

            val currency = runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("EUR")

            accountDao.insert(
                AccountEntity(
                    name = "Cash",
                    type = AccountType.CASH,
                    currency = currency,
                    openingBalanceMinor = 0,
                    colorArgb = COLOR_CASH,
                    sortOrder = 0,
                    createdAt = clock.instant(),
                ),
            )
        }

        private companion object {
            const val COLOR_CASH = 0xFF34C759.toInt()
        }
    }
