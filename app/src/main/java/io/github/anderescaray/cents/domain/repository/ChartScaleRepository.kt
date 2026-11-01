package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.ChartScale
import kotlinx.coroutines.flow.Flow

/**
 * The scale each investment chart was last left on, so it reopens there (`DECISIONS.md` D40).
 * [key] names the chart: [TOTAL_CHART], [accountChart] or [holdingChart].
 */
interface ChartScaleRepository {
    fun observe(key: String): Flow<ChartScale>

    suspend fun set(
        key: String,
        scale: ChartScale,
    )

    companion object {
        const val TOTAL_CHART = "total"

        fun accountChart(accountId: Long): String = "account-$accountId"

        fun holdingChart(holdingId: Long): String = "holding-$holdingId"
    }
}
