package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.domain.model.CategorySpending
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportPeriod
import io.github.anderescaray.cents.domain.model.SpendingBreakdown
import io.github.anderescaray.cents.domain.model.TransactionKind
import io.github.anderescaray.cents.domain.repository.reports.SpendingByCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The donut, aggregated by SQLite.
 *
 * There is no loop over transactions here. What Kotlin does is turn totals into [Money] and
 * work out each slice's share of one total it already has — bounded by the number of
 * categories, not by how many movements the app has ever recorded.
 */
@Singleton
class RoomSpendingByCategoryRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
    ) : SpendingByCategoryRepository {
        override fun observeSpendingByCategory(
            period: ReportPeriod,
            currency: String,
            kind: TransactionKind,
        ): Flow<SpendingBreakdown> =
            transactionDao
                .observeTotalsByCategory(kind, period.from, period.to)
                .map { rows ->
                    val totalMinor = rows.sumOf { it.totalMinor }
                    SpendingBreakdown(
                        period = period,
                        slices =
                            rows.map { row ->
                                CategorySpending(
                                    categoryId = row.categoryId,
                                    name = row.categoryName,
                                    colorArgb = row.categoryColorArgb,
                                    total = Money(row.totalMinor, currency),
                                    transactionCount = row.transactionCount,
                                    // A zero total means there are no rows at all, so this
                                    // branch never divides an existing slice by zero.
                                    share = if (totalMinor == 0L) 0f else row.totalMinor.toFloat() / totalMinor,
                                )
                            },
                        total = Money(totalMinor, currency),
                        kind = kind,
                    )
                }
    }
