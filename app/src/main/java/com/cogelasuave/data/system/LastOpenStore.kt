package com.cogelasuave.data.system

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers, per package, the wall-clock time the user last opened a watched app.
 * Kept in SharedPreferences (not Room) so the "time since last use" streak needs
 * no DB schema change. Survives process death; cleared only on uninstall/clear-data.
 */
@Singleton
class LastOpenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Epoch millis of the last open, or null if never recorded. */
    fun getLastOpen(packageName: String): Long? =
        prefs.getLong(packageName, 0L).takeIf { it > 0L }

    fun setLastOpen(packageName: String, epochMillis: Long) {
        prefs.edit().putLong(packageName, epochMillis).apply()
    }

    /** Full snapshot (package → epoch millis) for the backup export. */
    fun getAll(): Map<String, Long> =
        prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    /** Merges restored entries, keeping the most recent open per package. */
    fun restore(entries: Map<String, Long>) {
        prefs.edit().apply {
            entries.forEach { (pkg, millis) ->
                val keep = maxOf(millis, prefs.getLong(pkg, 0L))
                putLong(pkg, keep)
            }
        }.apply()
    }

    private companion object {
        const val PREFS = "last_open_store"
    }
}
