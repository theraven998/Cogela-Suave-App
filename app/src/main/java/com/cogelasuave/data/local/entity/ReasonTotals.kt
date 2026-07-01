package com.cogelasuave.data.local.entity

/**
 * Room projection: per-reason totals (across all apps) over a day window.
 * Returned by [com.cogelasuave.data.local.dao.ReasonStatDao.observeReasonTotalsBetween].
 */
data class ReasonTotals(
    val reason: String,
    val seconds: Long,
    val opens: Int,
)
