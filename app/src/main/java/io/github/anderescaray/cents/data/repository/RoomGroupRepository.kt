package io.github.anderescaray.cents.data.repository

import io.github.anderescaray.cents.data.local.dao.GroupDao
import io.github.anderescaray.cents.data.local.dao.GroupTotals
import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.data.local.entity.GroupEntity
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.Group
import io.github.anderescaray.cents.domain.model.GroupSummary
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomGroupRepository
    @Inject
    constructor(
        private val groupDao: GroupDao,
        private val transactionDao: TransactionDao,
        private val clock: Clock,
    ) : GroupRepository {
        override fun observeGroups(): Flow<List<Group>> =
            groupDao.observeAll().map { rows ->
                rows.map { it.toDomain() }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }

        override fun observeSummaries(currency: String): Flow<List<GroupSummary>> =
            groupDao.observeTotals().map { rows ->
                rows
                    .sortedWith(compareByDescending<GroupTotals> { it.recency() }.thenByDescending { it.id })
                    .map { it.toSummary(currency) }
            }

        override fun observeSummary(
            id: Long,
            currency: String,
        ): Flow<GroupSummary?> = groupDao.observeTotalsFor(id).map { it?.toSummary(currency) }

        override fun observeByCategory(
            groupId: Long,
            kind: TransactionKind,
            currency: String,
        ): Flow<List<CategorySpending>> =
            transactionDao.observeGroupTotalsByCategory(groupId, kind).map { rows ->
                val totalMinor = rows.sumOf { it.totalMinor }
                rows.map { row ->
                    CategorySpending(
                        categoryId = row.categoryId,
                        name = row.categoryName,
                        colorArgb = row.categoryColorArgb,
                        total = Money(row.totalMinor, currency),
                        transactionCount = row.transactionCount,
                        // A zero total means there are no rows, so no slice is divided by it.
                        share = if (totalMinor == 0L) 0f else row.totalMinor.toFloat() / totalMinor,
                    )
                }
            }

        override suspend fun findById(id: Long): Group? = groupDao.findById(id)?.toDomain()

        override suspend fun saveGroup(group: Group): Long {
            val name = group.name.trim()
            require(name.isNotEmpty()) { "A group has a name" }
            val start = group.startDate
            val end = group.endDate
            require(start == null || end == null || start <= end) { "A group cannot end before it starts" }
            return if (group.id == Group.NEW) {
                groupDao.insert(
                    GroupEntity(name = name, startDate = start, endDate = end, createdAt = clock.instant()),
                )
            } else {
                val existing = requireNotNull(groupDao.findById(group.id)) { "No group ${group.id}" }
                groupDao.update(existing.copy(name = name, startDate = start, endDate = end))
                group.id
            }
        }

        override suspend fun deleteGroup(id: Long) = groupDao.deleteById(id)

        /**
         * When a group last mattered: its last movement, else the end or start the owner gave it,
         * else the day it was made. A new, empty group therefore sorts as today's.
         */
        private fun GroupTotals.recency(): LocalDate =
            lastDate ?: endDate ?: startDate ?: createdAt.atZone(clock.zone).toLocalDate()
    }

private fun GroupEntity.toDomain() = Group(id = id, name = name, startDate = startDate, endDate = endDate)

private fun GroupTotals.toSummary(currency: String) =
    GroupSummary(
        group = Group(id = id, name = name, startDate = startDate, endDate = endDate),
        income = Money(incomeMinor, currency),
        expenses = Money(expenseMinor, currency),
        movementCount = movementCount,
        firstDate = firstDate,
        lastDate = lastDate,
    )
