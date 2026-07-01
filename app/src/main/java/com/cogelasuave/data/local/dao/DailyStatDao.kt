package com.cogelasuave.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cogelasuave.data.local.entity.DailyStatEntity
import com.cogelasuave.data.local.entity.DailyTotals
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatDao {

    /** Full snapshot for the backup export. */
    @Query("SELECT * FROM daily_stats")
    suspend fun getAll(): List<DailyStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DailyStatEntity>)

    @Query("DELETE FROM daily_stats")
    suspend fun clear()

    @Query("SELECT * FROM daily_stats WHERE epochDay = :epochDay ORDER BY attempts DESC")
    fun observeForDay(epochDay: Long): Flow<List<DailyStatEntity>>

    /**
     * Per-day totals (all apps collapsed) over the inclusive [startDay]..[endDay] window.
     * Days with no activity are simply absent and must be filled in by the caller.
     */
    @Query(
        "SELECT epochDay AS epochDay, " +
            "SUM(attempts) AS attempts, " +
            "SUM(dismissed) AS dismissed, " +
            "SUM(opened) AS opened " +
            "FROM daily_stats " +
            "WHERE epochDay BETWEEN :startDay AND :endDay " +
            "GROUP BY epochDay " +
            "ORDER BY epochDay ASC"
    )
    fun observeDailyTotalsBetween(startDay: Long, endDay: Long): Flow<List<DailyTotals>>

    @Query("SELECT attempts FROM daily_stats WHERE epochDay = :epochDay AND packageName = :pkg")
    suspend fun getAttempts(epochDay: Long, pkg: String): Int?

    /** Creates today's row for the app if it does not exist yet. */
    @Query(
        "INSERT OR IGNORE INTO daily_stats (epochDay, packageName, attempts, dismissed, opened) " +
            "VALUES (:epochDay, :pkg, 0, 0, 0)"
    )
    suspend fun ensureRow(epochDay: Long, pkg: String)

    @Query("UPDATE daily_stats SET attempts = attempts + 1 WHERE epochDay = :epochDay AND packageName = :pkg")
    suspend fun incrementAttempts(epochDay: Long, pkg: String)

    @Query("UPDATE daily_stats SET dismissed = dismissed + 1 WHERE epochDay = :epochDay AND packageName = :pkg")
    suspend fun incrementDismissed(epochDay: Long, pkg: String)

    @Query("UPDATE daily_stats SET opened = opened + 1 WHERE epochDay = :epochDay AND packageName = :pkg")
    suspend fun incrementOpened(epochDay: Long, pkg: String)
}
