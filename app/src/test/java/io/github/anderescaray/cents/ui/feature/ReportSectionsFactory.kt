package io.github.anderescaray.cents.ui.feature

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.repository.reports.RoomBalanceOverTimeRepository
import io.github.anderescaray.cents.data.repository.reports.RoomMonthlyFlowRepository
import io.github.anderescaray.cents.data.repository.reports.RoomPeriodComparisonRepository
import io.github.anderescaray.cents.data.repository.reports.RoomSpendingByCategoryRepository
import io.github.anderescaray.cents.ui.feature.reports.ReportSections
import io.github.anderescaray.cents.ui.feature.reports.section.BalanceOverTimeSectionSource
import io.github.anderescaray.cents.ui.feature.reports.section.MonthlyFlowSectionSource
import io.github.anderescaray.cents.ui.feature.reports.section.PeriodComparisonSectionSource
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingSectionSource
import java.time.Clock

/**
 * The tab's sections wired to a real database, which is what Hilt does on a phone.
 *
 * One place rather than one per test class, so a new view is added to the tests the same way
 * it is added to the app: one line here.
 *
 * The clock is required rather than defaulted: a view that compares against "the period before
 * this one" behaves differently depending on what day it is, and a test that did not say which
 * day it was standing on would pass or fail by the calendar.
 */
fun reportSections(
    database: CentsDatabase,
    clock: Clock,
): ReportSections {
    val dao = database.transactionDao()
    return ReportSections(
        SpendingSectionSource(RoomSpendingByCategoryRepository(dao)),
        MonthlyFlowSectionSource(RoomMonthlyFlowRepository(dao)),
        BalanceOverTimeSectionSource(RoomBalanceOverTimeRepository(dao)),
        PeriodComparisonSectionSource(RoomPeriodComparisonRepository(dao, clock)),
    )
}
