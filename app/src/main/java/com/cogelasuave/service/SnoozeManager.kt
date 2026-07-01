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
 *  - **Strict mode**: a *timed lock*. When enabled the user picks a duration; a
 *    master password (generated and shown once, meant to be written on paper) is
 *    hashed and stored. While the lock is active snoozing is forbidden AND the
 *    lock cannot be turned off — the only early exit is typing the master
 *    password. When the chosen duration elapses the lock releases on its own.
 *    Enabling strict mode immediately clears any active snooze.
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

    /** Epoch millis at which the strict lock auto-releases, or 0 if never set. */
    val strictUntilMillis: Long
        get() = prefs.getLong(KEY_STRICT_UNTIL, 0L)

    /**
     * Whether the strict lock is currently in force. True while "now" is before
     * [strictUntilMillis]; once the chosen duration elapses it reads false (and
     * lazily clears the stored hash on the next mutating call).
     */
    val isStrictMode: Boolean
        get() = System.currentTimeMillis() < strictUntilMillis

    /** Millis remaining on the strict lock, or 0 if not active. */
    fun strictRemainingMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (strictUntilMillis - nowMillis).coerceAtLeast(0L)

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
     * Enables the strict lock for [durationMinutes], storing the hash of the
     * master password. Also clears any active snooze so the user cannot leave a
     * pause running while "locking" the system. No-op if a lock is already active.
     */
    fun enableStrictMode(
        durationMinutes: Int,
        passwordHash: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (isStrictMode) return
        val until = nowMillis + durationMinutes.toLong() * 60_000L
        prefs.edit().apply {
            putLong(KEY_STRICT_UNTIL, until)
            putString(KEY_STRICT_HASH, passwordHash)
            putLong(KEY_SNOOZE_UNTIL, 0L)
            apply()
        }
    }

    /**
     * Attempts to release the strict lock early with [password]. Returns `true`
     * if the lock is no longer active afterwards: either it was already inactive,
     * or the password matched the stored hash. Returns `false` on a wrong password
     * while the lock is still active.
     */
    fun tryDisableStrictMode(password: String): Boolean {
        if (!isStrictMode) {
            clearStrictMode()
            return true
        }
        val stored = prefs.getString(KEY_STRICT_HASH, null) ?: return false
        if (StrictModeSecurity.hash(StrictModeSecurity.normalizeInput(password)) != stored) {
            return false
        }
        clearStrictMode()
        return true
    }

    /** Wipes strict-lock state (used after release or expiry). */
    private fun clearStrictMode() {
        prefs.edit().apply {
            remove(KEY_STRICT_UNTIL)
            remove(KEY_STRICT_HASH)
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "cogelasuave_snooze"
        private const val KEY_SNOOZE_UNTIL = "snooze_until_millis"
        private const val KEY_STRICT_UNTIL = "strict_until_millis"
        private const val KEY_STRICT_HASH = "strict_password_hash"

        /** Snooze durations offered in the UI and the Quick Settings tile. */
        val SNOOZE_OPTIONS_MINUTES = listOf(15, 30, 60)

        /** Strict-lock durations offered in the UI, in minutes. */
        val STRICT_OPTIONS_MINUTES = listOf(30, 60, 120, 240, 480, 1440)
    }
}
