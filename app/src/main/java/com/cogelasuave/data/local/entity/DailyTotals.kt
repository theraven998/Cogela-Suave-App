package com.cogelasuave.data.local.entity

/**
 * Room projection: the per-day totals (across all apps) used by the history view.
 * Returned by [com.cogelasuave.data.local.dao.DailyStatDao.observeDailyTotalsBetween].
 */
data class DailyTotals(
    val epochDay: Long,
    val attempts: Int,
    val dismissed: Int,
    val opened: Int,
)
