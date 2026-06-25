package com.cogelasuave.data.repository

import com.cogelasuave.data.local.entity.WatchedAppEntity
import com.cogelasuave.domain.model.InterceptionDecision
import com.cogelasuave.testutil.FakeDailyStatDao
import com.cogelasuave.testutil.FakeDateProvider
import com.cogelasuave.testutil.FakeTransactionRunner
import com.cogelasuave.testutil.FakeWatchedAppDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PKG = "com.example.timewaster"

class StatsRepositoryImplTest {

    private fun repository(
        date: FakeDateProvider,
        dailyDao: FakeDailyStatDao = FakeDailyStatDao(),
        watchedDao: FakeWatchedAppDao = FakeWatchedAppDao(
            listOf(
                WatchedAppEntity(
                    packageName = PKG,
                    label = "TimeWaster",
                    isWatched = true,
                    customWaitSeconds = null,
                ),
            ),
        ),
    ): StatsRepositoryImpl = StatsRepositoryImpl(
        transactionRunner = FakeTransactionRunner(),
        dailyStatDao = dailyDao,
        watchedAppDao = watchedDao,
        dateProvider = date,
    )

    @Test
    fun `recordAttempt increments and returns running count for today`() = runTest {
        val date = FakeDateProvider(today = 20_000)
        val repo = repository(date)

        assertEquals(1, repo.recordAttempt(PKG))
        assertEquals(2, repo.recordAttempt(PKG))
        assertEquals(3, repo.recordAttempt(PKG))

        val today = repo.observeTodayStats().first()
        assertEquals(1, today.size)
        assertEquals(3, today.first().attempts)
        assertEquals("TimeWaster", today.first().label)
    }

    @Test
    fun `recordDecision tallies opened and dismissed separately`() = runTest {
        val date = FakeDateProvider(today = 20_000)
        val repo = repository(date)

        repo.recordAttempt(PKG)
        repo.recordDecision(PKG, InterceptionDecision.DISMISSED)
        repo.recordAttempt(PKG)
        repo.recordDecision(PKG, InterceptionDecision.OPENED)

        val stat = repo.observeTodayStats().first().first()
        assertEquals(2, stat.attempts)
        assertEquals(1, stat.dismissed)
        assertEquals(1, stat.opened)
    }

    @Test
    fun `today stats reset at midnight when the date provider advances`() = runTest {
        val date = FakeDateProvider(today = 20_000)
        val repo = repository(date)

        repo.recordAttempt(PKG)
        repo.recordAttempt(PKG)
        assertEquals(2, repo.observeTodayStats().first().first().attempts)

        // New day: yesterday's row is no longer "today".
        date.advanceDays(1)
        assertTrue(repo.observeTodayStats().first().isEmpty())

        // A fresh attempt starts the new day's counter from one.
        assertEquals(1, repo.recordAttempt(PKG))
        assertEquals(1, repo.observeTodayStats().first().first().attempts)
    }

    @Test
    fun `weekly stats span 7 days oldest-first with idle days zeroed`() = runTest {
        val date = FakeDateProvider(today = 20_000)
        val repo = repository(date)

        // Day -2: one attempt, one dismissal.
        date.today = 19_998
        repo.recordAttempt(PKG)
        repo.recordDecision(PKG, InterceptionDecision.DISMISSED)
        // Today: two attempts.
        date.today = 20_000
        repo.recordAttempt(PKG)
        repo.recordAttempt(PKG)

        val week = repo.observeWeeklyStats(days = 7).first()
        assertEquals(7, week.size)
        // Oldest first, contiguous days.
        assertEquals(19_994, week.first().epochDay)
        assertEquals(20_000, week.last().epochDay)

        val twoDaysAgo = week.first { it.epochDay == 19_998L }
        assertEquals(1, twoDaysAgo.attempts)
        assertEquals(1, twoDaysAgo.dismissed)

        val today = week.first { it.epochDay == 20_000L }
        assertEquals(2, today.attempts)

        // An untouched day is present and empty.
        val idle = week.first { it.epochDay == 19_995L }
        assertTrue(idle.isEmpty)
    }

    @Test
    fun `weekly window excludes activity older than the requested span`() = runTest {
        val date = FakeDateProvider(today = 20_000)
        val repo = repository(date)

        // Activity 8 days ago must fall outside a 7-day window.
        date.today = 19_992
        repo.recordAttempt(PKG)
        date.today = 20_000

        val week = repo.observeWeeklyStats(days = 7).first()
        assertEquals(7, week.size)
        assertTrue(week.all { it.isEmpty })
    }
}
