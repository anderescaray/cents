package io.github.anderescaray.cents.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.anderescaray.cents.domain.model.MoneyFigure
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.domain.repository.MoneyFiguresRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings' *Wallet and Home* switches: which money figures those two screens show (D34). */
@HiltViewModel
class MoneyFiguresViewModel
    @Inject
    constructor(
        private val repository: MoneyFiguresRepository,
    ) : ViewModel() {
        val figures: StateFlow<MoneyFigures> =
            repository.observe().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = MoneyFigures(),
            )

        fun set(
            figure: MoneyFigure,
            on: Boolean,
        ) {
            viewModelScope.launch { repository.set(figure, on) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
