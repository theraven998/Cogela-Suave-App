package com.cogelasuave.testutil

import com.cogelasuave.domain.util.DateProvider

/**
 * Test [DateProvider] whose "today" can be moved freely to exercise the midnight
 * roll-over of the daily counters.
 */
class FakeDateProvider(var today: Long = 0L) : DateProvider {
    override fun todayEpochDay(): Long = today

    /** Advance the clock by [days] days (negative goes back in time). */
    fun advanceDays(days: Long) {
        today += days
    }
}
