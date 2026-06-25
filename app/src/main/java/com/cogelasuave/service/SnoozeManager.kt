package com.cogelasuave.service

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight, framework-only store for the "watch snooze" feature.
 *
 * Deliberately backed by [SharedPreferences] (no Room, no Hilt) so it can be
 * instantiated directly from anywhere that has a [Context] — most importantly
 * from [InterceptAccessibilityService] (which runs outside the normal UI graph)
 * and from a [SnoozeTileService] (a `TileService`, which Hilt does not inject).
 *
 * Two pieces of state live here:
 *
 *  - **Snooze**: an absolute epoch-millis timestamp until which interception is
 *    paused. While "now" is before that timestamp the service must NOT intercept
 *    watched apps. A value of `0L` (or any past timestamp) means "not snoozed".
 *
 *  - **Strict mode**: a boolean flag. When ON, snoozing is forbidden — the whole
 *    point is to stop the user from trivially bypassing their own pause. Any
 *    attempt to start a snooze while strict mode is on is a no-op, and turning
 *    strict mode on immediately clears any active snooze.
 *
 * All reads are cheap and synchronous, which is exactly what the accessibility
 * event path needs.
 */
class SnoozeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Epoch millis at which the current snooze expires, or 0 if none. */
    val snoozeUntilMillis: Long
        get() = prefs.getLong(KEY_SNOOZE_UNTIL, 0L)

    /** Whether strict mode (no snoozing allowed) is currently enabled. */
    val isStrictMode: Boolean
        get() = prefs.getBoolean(KEY_STRICT_MODE, false)

    /**
     * True while a snooze is currently in effect. This is the single check the
     * service uses to decide whether to skip interception.
     */
    fun isSnoozeActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis < snoozeUntilMillis

    /**
     * Millis remaining on the active snooze, or 0 if none is active. Handy for
     * the UI/tile to show a countdown-ish label.
     */
    fun remainingMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (snoozeUntilMillis - nowMillis).coerceAtLeast(0L)

    /**
     * Starts (or extends) a snooze for [minutes] minutes from now.
     *
     * Returns `true` if the snooze was applied, or `false` if it was rejected
     * because strict mode is on. The caller can use the result to tell the user
     * why nothing happened.
     */
    fun snoozeFor(minutes: Int, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (isStrictMode) return false
        val until = nowMillis + minutes.toLong() * 60_000L
        prefs.edit().putLong(KEY_SNOOZE_UNTIL, until).apply()
        return true
    }

    /** Cancels any active snooze immediately. */
    fun clearSnooze() {
        prefs.edit().putLong(KEY_SNOOZE_UNTIL, 0L).apply()
    }

    /**
     * Toggles strict mode. Turning it ON also clears any active snooze so the
     * user cannot leave a pause running while "locking" the system.
     */
    fun setStrictMode(enabled: Boolean) {
        prefs.edit().apply {
            putBoolean(KEY_STRICT_MODE, enabled)
            if (enabled) putLong(KEY_SNOOZE_UNTIL, 0L)
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "cogelasuave_snooze"
        private const val KEY_SNOOZE_UNTIL = "snooze_until_millis"
        private const val KEY_STRICT_MODE = "strict_mode"

        /** Snooze durations offered in the UI and the Quick Settings tile. */
        val SNOOZE_OPTIONS_MINUTES = listOf(15, 30, 60)
    }
}
