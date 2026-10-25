package io.github.anderescaray.cents.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One category the trend can be about: enough to name it and colour it, and nothing else. */
data class TrendChoice(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val emoji: String? = null,
)

/**
 * What one category came to over one bucket of the trend.
 *
 * [whole] is the bucket — the month or the week — and is what the axis is labelled with.
 * [counted] is the part of it that was actually summed, which differs only for a period that
 * has not finished: see [CategoryTrend.partial]. A tap opens [counted], because a point that
 * added up three days must not open thirty-one.
 */
data class TrendBucket(
    val whole: ReportPeriod,
    val counted: ReportPeriod,
    val total: Money,
)

/**
 * Is "Restaurants" creeping up: one category, followed across the last several periods.
 *
 * **This is the one view on the tab that reads outside the selected period, and it has to.**
 * "Creeping up" is a question about several periods; a single month cannot answer it, and a
 * line with one point on it is not a trend. The rule the tab keeps instead is that the period
 * still governs the view — it decides the **bucket**. Select a month and the line is the last
 * twelve months; select a week and it is the last twelve weeks. Nothing here compares a month
 * against a quarter, which is what the one-period rule exists to prevent.
 *
 * [selected] is null only when there is nothing to be about: no category has a movement in the
 * whole span. [choices] is every category that does, largest first, which is also where the
 * default comes from — the thing most worth watching is usually the thing most is spent on.
 */
data class CategoryTrend(
    val scope: ReportScope,
    val selected: TrendChoice?,
    val choices: List<TrendChoice>,
    val buckets: List<TrendBucket>,
    /**
     * True when the selected period has not finished, in which case **every** bucket is cut to
     * the same number of elapsed days.
     *
     * The alternative is the mistake the period-comparison view was fixed for, one screen up:
     * three days of September against twelve whole months reads as a collapse in spending, and
     * would read that way for most of every month. Cutting the earlier buckets to match makes
     * the line lower than the months really were, which is why the section says so out loud
     * rather than leaving it to be noticed.
     */
    val partial: Boolean = false,
) {
    val isEmpty: Boolean get() = selected == null || buckets.all { it.total.isZero }

    /** The bucket the selected period is, which is the last one. */
    val latest: TrendBucket? get() = buckets.lastOrNull()

    /**
     * The average of every bucket *before* the last one, which is what "creeping up" is
     * measured against.
     *
     * Against the single previous bucket, one quiet month makes the next look like a spike.
     * Against the average of the rest, a run of rising months reads as rising and a one-off
     * reads as a one-off — which is the question being asked.
     */
    val averageBefore: Money?
        get() {
            val earlier = buckets.dropLast(1)
            if (earlier.isEmpty()) return null
            val currency = earlier.first().total.currency
            val total = earlier.sumOf { it.total.minorUnits }
            // Rounded to the nearest cent rather than truncated. An average is a derived
            // statistic and cannot always be exact, but truncating integer division rounds
            // toward zero, which is one way for positive buckets and the other way for a
            // category that netted out negative — a bias, not just a rounding.
            val half = earlier.size / 2
            val rounded = (total + if (total >= 0) half else -half) / earlier.size
            return Money(rounded, currency)
        }

    /** How the latest bucket compares with that average. */
    val change: Money?
        get() {
            val latestTotal = latest?.total ?: return null
            val average = averageBefore ?: return null
            return latestTotal - average
        }

    /**
     * The movements behind one point of the line.
     *
     * The bucket's own period, not the selected one — a point three months back has to open
     * that month — plus the category, plus whatever account the tab is narrowed to.
     */
    fun filterFor(bucket: TrendBucket): TransactionFilter? {
        val category = selected ?: return null
        return scope
            .copy(period = bucket.counted)
            .asFilter()
            .copy(category = CategoryFilter.One(category.id))
    }
}

/**
 * This period and the [count] - 1 periods before it, oldest first.
 *
 * Built by walking [previous] backwards rather than by subtracting a fixed length, so every
 * bucket keeps the calendar shape of the one that was selected: twelve months are twelve
 * months, of 28 to 31 days each, and not twelve lots of thirty days sliding out of alignment.
 */
fun ReportPeriod.lastBuckets(count: Int): List<ReportPeriod> =
    generateSequence(this) { it.previous() }
        .take(count)
        .toList()
        .reversed()

/**
 * How many days of this period have happened by [today], or null if it has finished.
 *
 * Today counts as elapsed, the same way [comparedWith] counts it: the day is not over, but a
 * movement recorded this morning belongs to the stretch being asked about.
 */
fun ReportPeriod.elapsedDaysBy(today: LocalDate): Long? {
    if (today < from || today > to) return null
    return ChronoUnit.DAYS.between(from, today) + 1
}

/** The first [days] days of this period, or all of it if it is shorter than that. */
fun ReportPeriod.firstDays(days: Long): ReportPeriod = ReportPeriod.Custom(from, minOf(to, from.plusDays(days - 1)))
