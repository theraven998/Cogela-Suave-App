package com.cogelasuave.domain.repository

import com.cogelasuave.domain.model.DailyStat
import com.cogelasuave.domain.model.DayStats
import com.cogelasuave.domain.model.InterceptionDecision
import com.cogelasuave.domain.model.ReasonStat
import kotlinx.coroutines.flow.Flow

interface StatsRepository {

    /** Today's per-app counters, sorted by attempts desc. Resets naturally at midnight. */
    fun observeTodayStats(): Flow<List<DailyStat>>

    /**
     * Per-day totals for the last [days] days (inclusive of today), oldest first.
     * Every day in the window is present, with zeroed counters for idle days, and
     * the window slides at local midnight via the [DateProvider].
     */
    fun observeWeeklyStats(days: Int = DEFAULT_HISTORY_DAYS): Flow<List<DayStats>>

    /**
     * Records that the intercept screen was shown for [packageName] today and
     * returns how many attempts that makes today (including this one).
     */
    suspend fun recordAttempt(packageName: String): Int

    /** Records the user's final choice for the current interception. */
    suspend fun recordDecision(packageName: String, decision: InterceptionDecision)

    /**
     * Per-reason totals (time + opens) over the last [days] days, busiest first.
     * Powers the reason breakdown with its día/semana toggle.
     */
    fun observeReasonStats(days: Int): Flow<List<ReasonStat>>

    /**
     * Records that [packageName] was opened today under [reason]: bumps the daily
     * "opened" counter and the reason's open count.
     */
    suspend fun recordOpen(packageName: String, reason: String)

    /**
     * Adds [seconds] spent inside [packageName] to the [reason] bucket for the day
     * the session started ([epochDay]).
     */
    suspend fun addReasonTime(packageName: String, reason: String, epochDay: Long, seconds: Long)

    companion object {
        /** Default size of the history window ("Últimos 7 días"). */
        const val DEFAULT_HISTORY_DAYS = 7
    }
}
