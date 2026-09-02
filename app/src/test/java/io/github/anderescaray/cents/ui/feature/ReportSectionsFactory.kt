package io.github.anderescaray.cents.ui.feature

import io.github.anderescaray.cents.data.local.CentsDatabase
import io.github.anderescaray.cents.data.repository.reports.RoomBalanceOverTimeRepository
import io.github.anderescaray.cents.data.repository.reports.RoomMonthlyFlowRepository
import io.github.anderescaray.cents.data.repository.reports.RoomSpendingByCategoryRepository
import io.github.anderescaray.cents.ui.feature.reports.ReportSections
import io.github.anderescaray.cents.ui.feature.reports.section.BalanceOverTimeSectionSource
import io.github.anderescaray.cents.ui.feature.reports.section.MonthlyFlowSectionSource
import io.github.anderescaray.cents.ui.feature.reports.section.SpendingSectionSource

/**
 * The tab's sections wired to a real database, which is what Hilt does on a phone.
 *
 * One place rather than one per test class, so a new view is added to the tests the same way
 * it is added to the app: one line here.
 */
fun reportSections(database: CentsDatabase): ReportSections {
    val dao = database.transactionDao()
    return ReportSections(
        SpendingSectionSource(RoomSpendingByCategoryRepository(dao)),
        MonthlyFlowSectionSource(RoomMonthlyFlowRepository(dao)),
        BalanceOverTimeSectionSource(RoomBalanceOverTimeRepository(dao)),
    )
}
