package io.github.anderescaray.cents.ui.feature

import io.github.anderescaray.cents.domain.model.MoneyFigure
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.domain.repository.MoneyFiguresRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The money figures chosen, in memory: what DataStore keeps, without the file. */
class FakeMoneyFigures(
    initial: MoneyFigures = MoneyFigures(),
) : MoneyFiguresRepository {
    val figures = MutableStateFlow(initial)

    override fun observe(): Flow<MoneyFigures> = figures

    override suspend fun set(
        figure: MoneyFigure,
        on: Boolean,
    ) {
        figures.value =
            when (figure) {
                MoneyFigure.NORMAL -> figures.value.copy(normal = on)
                MoneyFigure.INVESTED -> figures.value.copy(invested = on)
                MoneyFigure.TOTAL -> figures.value.copy(total = on)
            }
    }
}
