package io.github.anderescaray.cents.data.repository.reports

import io.github.anderescaray.cents.data.local.dao.TransactionDao
import io.github.anderescaray.cents.domain.model.CategoryTrend
import io.github.anderescaray.cents.domain.model.Money
import io.github.anderescaray.cents.domain.model.ReportScope
import io.github.anderescaray.cents.domain.model.TrendBucket
import io.github.anderescaray.cents.domain.model.TrendChoice
import io.github.anderescaray.cents.domain.model.elapsedDaysBy
import io.github.anderescaray.cents.domain.model.firstDays
import io.github.anderescaray.cents.domain.model.lastBuckets
import io.github.anderescaray.cents.domain.repository.reports.CategoryTrendRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The trend line, aggregated by SQLite.
 *
 * Two queries. The first is the list of categories with anything in the span, which is both
 * what the picker offers and where the default comes from. The second is the chosen category's
 * daily totals, folded into buckets here — one pass over the days of the span rather than over
 * the movements in it, which is the rule every report on this tab follows (`CLAUDE.md` §5).
 *
 * The span is worked out from the buckets rather than from the scope: the scope's period is the
 * *last* bucket, and the line starts eleven periods before it.
 */
@Singleton
class RoomCategoryTrendRepository
    @Inject
    constructor(
        private val transactionDao: TransactionDao,
        private val clock: Clock,
    ) : CategoryTrendRepository {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeCategoryTrend(
            scope: ReportScope,
            categoryId: Long?,
        ): Flow<CategoryTrend> {
            val whole = scope.period.lastBuckets(BUCKET_COUNT)
            // A period that has not finished cuts every bucket to the same number of days, so
            // the line compares like with like — the same rule the period comparison follows,
            // argued on CategoryTrend.partial.
            val elapsed = scope.period.elapsedDaysBy(LocalDate.now(clock))
            val buckets = whole.map { bucket -> bucket to (elapsed?.let(bucket::firstDays) ?: bucket) }
            val spanFrom = buckets.first().second.from
            val spanTo = buckets.last().second.to

            return transactionDao
                .observeCategoriesWithMovements(spanFrom, spanTo, scope.accountId)
                .flatMapLatest { rows ->
                    val choices =
                        rows.mapNotNull { row ->
                            TrendChoice(
                                id = row.categoryId ?: return@mapNotNull null,
                                name = row.categoryName ?: return@mapNotNull null,
                                colorArgb = row.categoryColorArgb ?: return@mapNotNull null,
                            )
                        }
                    // A chosen category that has nothing in this span is not an error and not
                    // a reason to show someone else's line: it falls back to the busiest one,
                    // the same as opening the view for the first time.
                    val selected = choices.firstOrNull { it.id == categoryId } ?: choices.firstOrNull()

                    if (selected == null) {
                        flowOf(CategoryTrend(scope, null, choices, emptyList(), elapsed != null))
                    } else {
                        transactionDao
                            .observeCategoryDailyTotals(selected.id, spanFrom, spanTo, scope.accountId)
                            .map { days ->
                                val byDate = days.associate { it.date to it.totalMinor }
                                CategoryTrend(
                                    scope = scope,
                                    selected = selected,
                                    choices = choices,
                                    buckets =
                                        buckets.map { (whole, counted) ->
                                            // Empty buckets are zeros rather than missing
                                            // points: a gap in a line reads as data that was
                                            // not collected, and a zero reads as a quiet month.
                                            val total =
                                                byDate.entries
                                                    .filter { it.key >= counted.from && it.key <= counted.to }
                                                    .sumOf { it.value }
                                            TrendBucket(whole, counted, Money(total, scope.currency))
                                        },
                                    partial = elapsed != null,
                                )
                            }
                    }
                }
        }

        private companion object {
            /**
             * Twelve buckets: a year of months, a quarter of weeks. Enough for a trend to be
             * visible and few enough that the labels still fit across a phone.
             */
            const val BUCKET_COUNT = 12
        }
    }
