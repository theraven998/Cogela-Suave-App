package com.cogelasuave.domain.model

/** Global, user-tunable behaviour of the pause. */
data class AppSettings(
    /** Default obligatory wait before the "Abrir" button unlocks. */
    val globalWaitSeconds: Int = DEFAULT_WAIT_SECONDS,
    /** Used to estimate "time saved" for each app the user backs out of. */
    val estimatedSessionMinutes: Int = DEFAULT_SESSION_MINUTES,
) {
    companion object {
        const val DEFAULT_WAIT_SECONDS = 10
        const val MIN_WAIT_SECONDS = 3
        const val MAX_WAIT_SECONDS = 60
        const val DEFAULT_SESSION_MINUTES = 5
    }
}
