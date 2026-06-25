package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.model.DayStats
import com.cogelasuave.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Emits the per-day totals for the last [StatsRepository.DEFAULT_HISTORY_DAYS] days
 * (today included), oldest first. Powers the "Últimos 7 días" history section.
 */
class ObserveWeeklyStatsUseCase @Inject constructor(
    private val repository: StatsRepository,
) {
    operator fun invoke(days: Int = StatsRepository.DEFAULT_HISTORY_DAYS): Flow<List<DayStats>> =
        repository.observeWeeklyStats(days)
}
