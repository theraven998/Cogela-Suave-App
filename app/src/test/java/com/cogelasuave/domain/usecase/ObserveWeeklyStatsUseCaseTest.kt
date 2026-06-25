package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.model.DayStats
import com.cogelasuave.domain.model.InterceptionDecision
import com.cogelasuave.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveWeeklyStatsUseCaseTest {

    private class StubStatsRepository(
        private val emitted: List<DayStats>,
    ) : StatsRepository {
        var requestedDays: Int = -1

        override fun observeTodayStats() = flowOf(emptyList<com.cogelasuave.domain.model.DailyStat>())

        override fun observeWeeklyStats(days: Int): Flow<List<DayStats>> {
            requestedDays = days
            return flowOf(emitted)
        }

        override suspend fun recordAttempt(packageName: String): Int = 0
        override suspend fun recordDecision(packageName: String, decision: InterceptionDecision) = Unit
    }

    @Test
    fun `default invocation requests the 7-day window`() = runTest {
        val repo = StubStatsRepository(emptyList())
        val useCase = ObserveWeeklyStatsUseCase(repo)

        useCase().first()

        assertEquals(StatsRepository.DEFAULT_HISTORY_DAYS, repo.requestedDays)
    }

    @Test
    fun `forwards repository emissions unchanged`() = runTest {
        val days = listOf(
            DayStats(epochDay = 10, attempts = 1, dismissed = 0, opened = 1),
            DayStats(epochDay = 11, attempts = 4, dismissed = 3, opened = 1),
        )
        val useCase = ObserveWeeklyStatsUseCase(StubStatsRepository(days))

        assertEquals(days, useCase().first())
    }
}
