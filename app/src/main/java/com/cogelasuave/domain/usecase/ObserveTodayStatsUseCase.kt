package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.model.DailyStat
import com.cogelasuave.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTodayStatsUseCase @Inject constructor(
    private val repository: StatsRepository,
) {
    operator fun invoke(): Flow<List<DailyStat>> = repository.observeTodayStats()
}
