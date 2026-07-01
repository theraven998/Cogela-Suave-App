package com.cogelasuave.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cogelasuave.domain.model.DailyStat
import com.cogelasuave.domain.model.ReasonStat
import com.cogelasuave.domain.model.WeeklySummary
import com.cogelasuave.domain.repository.StatsRepository
import com.cogelasuave.domain.usecase.ObserveReasonStatsUseCase
import com.cogelasuave.domain.usecase.ObserveSettingsUseCase
import com.cogelasuave.domain.usecase.ObserveTodayStatsUseCase
import com.cogelasuave.domain.usecase.ObserveWeeklyStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Window for the reason breakdown toggle. */
enum class ReasonRange { DAY, WEEK }

data class StatsUiState(
    /** True until the first emission of real data arrives. */
    val loading: Boolean = true,
    val perApp: List<DailyStat> = emptyList(),
    val totalAttempts: Int = 0,
    val totalDismissed: Int = 0,
    val totalOpened: Int = 0,
    val estimatedMinutesSaved: Int = 0,
    /** Per-day roll-up for the "Últimos 7 días" section. */
    val weekly: WeeklySummary = WeeklySummary(
        days = emptyList(),
        totalAttempts = 0,
        totalDismissed = 0,
        totalOpened = 0,
        estimatedMinutesSaved = 0,
    ),
    /** Selected window for the reason breakdown. */
    val reasonRange: ReasonRange = ReasonRange.DAY,
    /** Per-reason time/opens for the selected window, busiest first. */
    val reasons: List<ReasonStat> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    observeTodayStats: ObserveTodayStatsUseCase,
    observeWeeklyStats: ObserveWeeklyStatsUseCase,
    observeSettings: ObserveSettingsUseCase,
    observeReasonStats: ObserveReasonStatsUseCase,
) : ViewModel() {

    private val reasonRange = MutableStateFlow(ReasonRange.DAY)

    fun setReasonRange(range: ReasonRange) {
        reasonRange.value = range
    }

    private val reasonsBlock = combine(
        observeReasonStats(1),
        observeReasonStats(StatsRepository.DEFAULT_HISTORY_DAYS),
        reasonRange,
    ) { day, week, range ->
        range to if (range == ReasonRange.DAY) day else week
    }

    val uiState: StateFlow<StatsUiState> =
        combine(
            observeTodayStats(),
            observeWeeklyStats(),
            observeSettings(),
            reasonsBlock,
        ) { stats, weekDays, settings, (range, reasons) ->
            val attempts = stats.sumOf { it.attempts }
            val dismissed = stats.sumOf { it.dismissed }
            val opened = stats.sumOf { it.opened }
            StatsUiState(
                loading = false,
                perApp = stats,
                totalAttempts = attempts,
                totalDismissed = dismissed,
                totalOpened = opened,
                // Each time you backed out, you saved roughly one session.
                estimatedMinutesSaved = dismissed * settings.estimatedSessionMinutes,
                weekly = WeeklySummary.from(weekDays, settings.estimatedSessionMinutes),
                reasonRange = range,
                reasons = reasons,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState(),
        )
}
