package com.cogelasuave.domain.model

/** Time spent (and open count) for one reason, aggregated over a window. */
data class ReasonStat(
    val reason: String,
    /** Total seconds spent in watched apps opened under this reason. */
    val seconds: Long,
    /** Number of opens attributed to this reason. */
    val opens: Int,
) {
    val minutes: Int get() = (seconds / 60).toInt()
}
