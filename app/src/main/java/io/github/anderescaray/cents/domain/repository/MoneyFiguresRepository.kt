package io.github.anderescaray.cents.domain.repository

import io.github.anderescaray.cents.domain.model.MoneyFigure
import io.github.anderescaray.cents.domain.model.MoneyFigures
import kotlinx.coroutines.flow.Flow

/** Where the choice of money figures is kept: on the phone, like the Reports arrangement. */
interface MoneyFiguresRepository {
    fun observe(): Flow<MoneyFigures>

    /**
     * Switches one figure, leaving the others as they are stored. One at a time, so two quick
     * taps cannot each write back a copy of the choice the other has not seen yet.
     */
    suspend fun set(
        figure: MoneyFigure,
        on: Boolean,
    )
}
