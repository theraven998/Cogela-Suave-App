package com.cogelasuave.domain.model

/**
 * Roll-up of the last few days, plus the per-day breakdown for the mini bar chart.
 * [days] is oldest-first so it can be rendered left-to-right.
 */
data class WeeklySummary(
    val days: List<DayStats>,
    val totalAttempts: Int,
    val totalDismissed: Int,
    val totalOpened: Int,
    /** Estimated minutes saved over the window (dismissed sessions × session length). */
    val estimatedMinutesSaved: Int,
) {
    val isEmpty: Boolean get() = days.all { it.isEmpty }

    companion object {
        fun from(days: List<DayStats>, estimatedSessionMinutes: Int): WeeklySummary {
            val attempts = days.sumOf { it.attempts }
            val dismissed = days.sumOf { it.dismissed }
            val opened = days.sumOf { it.opened }
            return WeeklySummary(
                days = days,
                totalAttempts = attempts,
                totalDismissed = dismissed,
                totalOpened = opened,
                estimatedMinutesSaved = dismissed * estimatedSessionMinutes,
            )
        }
    }
}
