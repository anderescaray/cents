package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.dao.AccountBalance
import io.github.anderescaray.cents.data.local.dao.PeriodTotals
import io.github.anderescaray.cents.data.local.dao.TransactionWithNames
import io.github.anderescaray.cents.data.local.entity.AccountEntity
import io.github.anderescaray.cents.data.local.entity.CategoryEntity
import io.github.anderescaray.cents.data.local.entity.TransactionEntity
import io.github.anderescaray.cents.domain.model.Account
import io.github.anderescaray.cents.domain.model.AccountWithBalance
import io.github.anderescaray.cents.domain.model.Category
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.PeriodSummary
import io.github.anderescaray.cents.domain.model.Transaction
import io.github.anderescaray.cents.domain.model.TransactionDetails
import java.time.Instant

/**
 * Translation between the storage shape and the domain shape.
 *
 * Kept in one file per area on purpose: when the schema changes, every place that needs
 * updating is here rather than scattered across repositories. The investments ones are in
 * [InvestmentMappers.kt][io.github.anderescaray.cents.data.repository.toDomain], for the same
 * reason their converters and their Hilt module are their own: a feature should add a file
 * rather than edit one every other feature also edits.
 */

fun AccountEntity.toDomain(): Account =
    Account(
        id = id,
        name = name,
        type = type,
        currency = currency,
        openingBalance = Money(openingBalanceMinor, currency),
        colorArgb = colorArgb,
        archived = archived,
        sortOrder = sortOrder,
    )

fun Account.toEntity(createdAt: Instant): AccountEntity =
    AccountEntity(
        id = id,
        name = name,
        type = type,
        currency = currency,
        openingBalanceMinor = openingBalance.minorUnits,
        colorArgb = colorArgb,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )

fun AccountBalance.toMoney(): Money = Money(balanceMinor, currency)

fun Account.withBalance(balance: Money?): AccountWithBalance =
    AccountWithBalance(
        account = this,
        balance = balance ?: openingBalance,
    )

fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        kind = kind,
        colorArgb = colorArgb,
        parentId = parentId,
        archived = archived,
        sortOrder = sortOrder,
    )

fun Category.toEntity(createdAt: Instant): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        kind = kind,
        colorArgb = colorArgb,
        parentId = parentId,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )

fun TransactionEntity.toDomain(): Transaction =
    Transaction(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        kind = kind,
        amount = Money(amountMinor, currency),
        date = date,
        note = note,
        transferId = transferId,
        tradeId = tradeId,
        ruleId = ruleId,
        groupId = groupId,
    )

fun Transaction.toEntity(
    createdAt: Instant,
    updatedAt: Instant,
): TransactionEntity =
    TransactionEntity(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        kind = kind,
        amountMinor = amount.minorUnits,
        currency = amount.currency,
        date = date,
        note = note,
        transferId = transferId,
        tradeId = tradeId,
        ruleId = ruleId,
        groupId = groupId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun TransactionWithNames.toDomain(): TransactionDetails =
    TransactionDetails(
        transaction =
            Transaction(
                id = id,
                accountId = accountId,
                categoryId = categoryId,
                kind = kind,
                amount = Money(amountMinor, currency),
                date = date,
                note = note,
                transferId = transferId,
                tradeId = tradeId,
                groupId = groupId,
            ),
        accountName = accountName,
        categoryName = categoryName,
        categoryColorArgb = categoryColorArgb,
        counterpartAccountName = counterpartAccountName,
        tradeSymbol = tradeSymbol,
        tradeHoldingId = tradeHoldingId,
        groupName = groupName,
    )

fun PeriodTotals.toSummary(currency: String): PeriodSummary =
    PeriodSummary(
        income = Money(incomeMinor, currency),
        expenses = Money(expenseMinor, currency),
    )
