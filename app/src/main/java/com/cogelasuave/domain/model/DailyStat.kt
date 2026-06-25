package com.cogelasuave.domain.model

/** Aggregated counters for one app on one day. */
data class DailyStat(
    val packageName: String,
    val label: String,
    /** Times the intercept screen was shown today. */
    val attempts: Int,
    /** Times the user chose "Mejor no". */
    val dismissed: Int,
    /** Times the user chose "Abrir". */
    val opened: Int,
)
