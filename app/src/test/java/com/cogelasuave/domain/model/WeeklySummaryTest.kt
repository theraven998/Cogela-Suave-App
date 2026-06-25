package com.cogelasuave.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklySummaryTest {

    @Test
    fun `from sums per-day counters across the window`() {
        val days = listOf(
            DayStats(epochDay = 1, attempts = 3, dismissed = 2, opened = 1),
            DayStats(epochDay = 2, attempts = 0, dismissed = 0, opened = 0),
            DayStats(epochDay = 3, attempts = 5, dismissed = 4, opened = 1),
        )

        val summary = WeeklySummary.from(days, estimatedSessionMinutes = 5)

        assertEquals(8, summary.totalAttempts)
        assertEquals(6, summary.totalDismissed)
        assertEquals(2, summary.totalOpened)
    }

    @Test
    fun `estimated minutes saved is dismissed sessions times session length`() {
        val days = listOf(
            DayStats(epochDay = 1, attempts = 4, dismissed = 3, opened = 1),
            DayStats(epochDay = 2, attempts = 2, dismissed = 1, opened = 1),
        )

        // 4 dismissed sessions, each worth the configured 5 minutes -> 20.
        val summary = WeeklySummary.from(days, estimatedSessionMinutes = 5)

        assertEquals(4, summary.totalDismissed)
        assertEquals(20, summary.estimatedMinutesSaved)
    }

    @Test
    fun `isEmpty is true only when every day is idle`() {
        val idle = listOf(
            DayStats(epochDay = 1, attempts = 0, dismissed = 0, opened = 0),
            DayStats(epochDay = 2, attempts = 0, dismissed = 0, opened = 0),
        )
        assertTrue(WeeklySummary.from(idle, estimatedSessionMinutes = 5).isEmpty)

        val active = idle + DayStats(epochDay = 3, attempts = 1, dismissed = 0, opened = 1)
        assertFalse(WeeklySummary.from(active, estimatedSessionMinutes = 5).isEmpty)
    }
}
