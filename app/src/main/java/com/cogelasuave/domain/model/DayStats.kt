package com.cogelasuave.domain.model

/**
 * Totals (across all apps) for a single day, used by the "Últimos 7 días" history.
 * Every day in the requested window is present, even those with no activity.
 */
data class DayStats(
    /** Day as [java.time.LocalDate.toEpochDay]. */
    val epochDay: Long,
    /** Times the pause was shown that day. */
    val attempts: Int,
    /** Times the user chose "Mejor no" that day. */
    val dismissed: Int,
    /** Times the user chose "Abrir" that day. */
    val opened: Int,
) {
    /** True when nothing happened that day (used for empty/idle styling). */
    val isEmpty: Boolean get() = attempts == 0 && dismissed == 0 && opened == 0
}
