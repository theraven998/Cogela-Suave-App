package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.model.ReasonStat
import com.cogelasuave.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Emits per-reason time/open totals over the last [days] days (today included). */
class ObserveReasonStatsUseCase @Inject constructor(
    private val repository: StatsRepository,
) {
    operator fun invoke(days: Int): Flow<List<ReasonStat>> =
        repository.observeReasonStats(days)
}
