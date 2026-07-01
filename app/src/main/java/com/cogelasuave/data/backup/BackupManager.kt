package com.cogelasuave.data.backup

import com.cogelasuave.data.local.TransactionRunner
import com.cogelasuave.data.local.dao.DailyStatDao
import com.cogelasuave.data.local.dao.ReasonStatDao
import com.cogelasuave.data.local.dao.SettingsDao
import com.cogelasuave.data.local.dao.WatchedAppDao
import com.cogelasuave.data.local.entity.DailyStatEntity
import com.cogelasuave.data.local.entity.ReasonStatEntity
import com.cogelasuave.data.local.entity.SettingsEntity
import com.cogelasuave.data.local.entity.WatchedAppEntity
import com.cogelasuave.data.system.LastOpenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a restore, for a human-readable confirmation. */
data class RestoreResult(
    val watchedApps: Int,
    val dailyStats: Int,
    val reasonStats: Int,
)

/**
 * Serializes every Room table to a single JSON document (and back) so the user
 * can keep their stats across reinstalls / destructive DB migrations. The JSON
 * lives wherever the user's file picker points (SAF), well outside app storage.
 */
@Singleton
class BackupManager @Inject constructor(
    private val watchedAppDao: WatchedAppDao,
    private val settingsDao: SettingsDao,
    private val dailyStatDao: DailyStatDao,
    private val reasonStatDao: ReasonStatDao,
    private val lastOpenStore: LastOpenStore,
    private val transaction: TransactionRunner,
) {

    suspend fun export(out: OutputStream) = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put(KEY_VERSION, FORMAT_VERSION)
            put(KEY_EXPORTED_AT, System.currentTimeMillis())

            settingsDao.get()?.let { s ->
                put(
                    KEY_SETTINGS,
                    JSONObject()
                        .put("globalWaitSeconds", s.globalWaitSeconds)
                        .put("estimatedSessionMinutes", s.estimatedSessionMinutes),
                )
            }

            put(KEY_WATCHED, JSONArray().apply {
                watchedAppDao.getAll().forEach { w ->
                    put(
                        JSONObject()
                            .put("packageName", w.packageName)
                            .put("label", w.label)
                            .put("isWatched", w.isWatched)
                            .put("customWaitSeconds", w.customWaitSeconds ?: JSONObject.NULL),
                    )
                }
            })

            put(KEY_DAILY, JSONArray().apply {
                dailyStatDao.getAll().forEach { d ->
                    put(
                        JSONObject()
                            .put("epochDay", d.epochDay)
                            .put("packageName", d.packageName)
                            .put("attempts", d.attempts)
                            .put("dismissed", d.dismissed)
                            .put("opened", d.opened),
                    )
                }
            })

            put(KEY_REASON, JSONArray().apply {
                reasonStatDao.getAll().forEach { r ->
                    put(
                        JSONObject()
                            .put("epochDay", r.epochDay)
                            .put("packageName", r.packageName)
                            .put("reason", r.reason)
                            .put("opens", r.opens)
                            .put("seconds", r.seconds),
                    )
                }
            })

            // "Time since last use" streaks (package → epoch millis).
            put(KEY_LAST_OPENS, JSONObject().apply {
                lastOpenStore.getAll().forEach { (pkg, millis) -> put(pkg, millis) }
            })
        }

        out.bufferedWriter().use { it.write(root.toString()) }
    }

    suspend fun import(input: InputStream): RestoreResult = withContext(Dispatchers.IO) {
        val text = input.bufferedReader().use { it.readText() }
        val root = JSONObject(text)

        val watched = root.optJSONArray(KEY_WATCHED).toWatchedApps()
        val daily = root.optJSONArray(KEY_DAILY).toDailyStats()
        val reason = root.optJSONArray(KEY_REASON).toReasonStats()
        val settings = root.optJSONObject(KEY_SETTINGS)?.let { s ->
            SettingsEntity(
                globalWaitSeconds = s.getInt("globalWaitSeconds"),
                estimatedSessionMinutes = s.getInt("estimatedSessionMinutes"),
            )
        }

        // Replace everything atomically so a half-read file can't corrupt state.
        transaction {
            watchedAppDao.clear()
            dailyStatDao.clear()
            reasonStatDao.clear()
            if (watched.isNotEmpty()) watchedAppDao.insertAll(watched)
            if (daily.isNotEmpty()) dailyStatDao.insertAll(daily)
            if (reason.isNotEmpty()) reasonStatDao.insertAll(reason)
            settings?.let { settingsDao.upsert(it) }
        }

        // Streaks live outside Room; merge them in (keep the most recent per app).
        root.optJSONObject(KEY_LAST_OPENS)?.let { obj ->
            val map = obj.keys().asSequence().associateWith { obj.getLong(it) }
            if (map.isNotEmpty()) lastOpenStore.restore(map)
        }

        RestoreResult(watched.size, daily.size, reason.size)
    }

    private fun JSONArray?.toWatchedApps(): List<WatchedAppEntity> =
        mapObjects { o ->
            WatchedAppEntity(
                packageName = o.getString("packageName"),
                label = o.getString("label"),
                isWatched = o.getBoolean("isWatched"),
                customWaitSeconds = if (o.isNull("customWaitSeconds")) null else o.getInt("customWaitSeconds"),
            )
        }

    private fun JSONArray?.toDailyStats(): List<DailyStatEntity> =
        mapObjects { o ->
            DailyStatEntity(
                epochDay = o.getLong("epochDay"),
                packageName = o.getString("packageName"),
                attempts = o.getInt("attempts"),
                dismissed = o.getInt("dismissed"),
                opened = o.getInt("opened"),
            )
        }

    private fun JSONArray?.toReasonStats(): List<ReasonStatEntity> =
        mapObjects { o ->
            ReasonStatEntity(
                epochDay = o.getLong("epochDay"),
                packageName = o.getString("packageName"),
                reason = o.getString("reason"),
                opens = o.getInt("opens"),
                seconds = o.getLong("seconds"),
            )
        }

    private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        val arr = this ?: return emptyList()
        return (0 until arr.length()).map { transform(arr.getJSONObject(it)) }
    }

    companion object {
        private const val FORMAT_VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_WATCHED = "watchedApps"
        private const val KEY_DAILY = "dailyStats"
        private const val KEY_REASON = "reasonStats"
        private const val KEY_LAST_OPENS = "lastOpens"

        /** Suggested filename for the export document. */
        const val DEFAULT_FILE_NAME = "cogela-suave-backup.json"
        const val MIME_TYPE = "application/json"
    }
}
