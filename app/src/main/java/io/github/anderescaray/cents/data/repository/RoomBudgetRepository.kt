package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.dao.BudgetDao
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.data.local.entity.BudgetEntity
import io.github.anderescaray.cents.domain.model.Budget
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBudgetRepository
    @Inject
    constructor(
        private val budgetDao: BudgetDao,
        private val transactionDao: TransactionDao,
        private val clock: Clock,
    ) : BudgetRepository {
        override fun observeBudgets(): Flow<List<Budget>> =
            budgetDao.observeAll().map { rows ->
                rows.map { it.toDomain() }
            }

        /** The donut's own query, all accounts: a budget is about a category, wherever it was paid from. */
        override fun observeSpentByCategory(
            from: LocalDate,
            to: LocalDate,
            currency: String,
        ): Flow<Map<Long, Money>> =
            transactionDao
                .observeTotalsByCategory(TransactionKind.EXPENSE, from, to, accountId = null)
                .map { totals ->
                    totals
                        .mapNotNull { total -> total.categoryId?.let { it to Money(total.totalMinor, currency) } }
                        .toMap()
                }

        override suspend fun findById(id: Long): Budget? = budgetDao.findById(id)?.toDomain()

        override suspend fun saveBudget(budget: Budget): Long {
            require(budget.limit.minorUnits > 0) { "A budget is a positive limit" }
            return if (budget.id == Budget.NEW) {
                budgetDao.insert(budget.toEntity())
            } else {
                val existing = requireNotNull(budgetDao.findById(budget.id)) { "No budget ${budget.id}" }
                budgetDao.update(budget.toEntity().copy(createdAt = existing.createdAt))
                budget.id
            }
        }

        override suspend fun deleteBudget(id: Long) = budgetDao.deleteById(id)

        private fun Budget.toEntity() =
            BudgetEntity(
                id = id,
                categoryId = categoryId,
                amountMinor = limit.minorUnits,
                currency = limit.currency,
                createdAt = clock.instant(),
            )
    }

private fun BudgetEntity.toDomain() = Budget(id = id, categoryId = categoryId, limit = Money(amountMinor, currency))
