package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.ReportLayout
import kotlinx.coroutines.flow.Flow

/** Where the Reports tab's arrangement is kept: on the phone, like the lock and capture settings. */
interface ReportLayoutRepository {
    fun observe(): Flow<ReportLayout>

    suspend fun save(layout: ReportLayout)
}
