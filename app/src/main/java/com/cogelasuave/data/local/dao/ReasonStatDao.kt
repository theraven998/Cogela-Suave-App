package com.cogelasuave.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cogelasuave.data.local.entity.ReasonStatEntity
import com.cogelasuave.data.local.entity.ReasonTotals
import kotlinx.coroutines.flow.Flow

@Dao
interface ReasonStatDao {

    /** Full snapshot for the backup export. */
    @Query("SELECT * FROM reason_stats")
    suspend fun getAll(): List<ReasonStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ReasonStatEntity>)

    @Query("DELETE FROM reason_stats")
    suspend fun clear()

    /**
     * Per-reason totals (all apps collapsed) over the inclusive [startDay]..[endDay]
     * window, busiest first. Reasons with no activity are simply absent.
     */
    @Query(
        "SELECT reason AS reason, " +
            "SUM(seconds) AS seconds, " +
            "SUM(opens) AS opens " +
            "FROM reason_stats " +
            "WHERE epochDay BETWEEN :startDay AND :endDay " +
            "GROUP BY reason " +
            "ORDER BY seconds DESC, opens DESC"
    )
    fun observeReasonTotalsBetween(startDay: Long, endDay: Long): Flow<List<ReasonTotals>>

    /** Creates the (day, app, reason) row if it does not exist yet. */
    @Query(
        "INSERT OR IGNORE INTO reason_stats (epochDay, packageName, reason, opens, seconds) " +
            "VALUES (:epochDay, :pkg, :reason, 0, 0)"
    )
    suspend fun ensureRow(epochDay: Long, pkg: String, reason: String)

    @Query(
        "UPDATE reason_stats SET opens = opens + 1 " +
            "WHERE epochDay = :epochDay AND packageName = :pkg AND reason = :reason"
    )
    suspend fun incrementOpens(epochDay: Long, pkg: String, reason: String)

    @Query(
        "UPDATE reason_stats SET seconds = seconds + :seconds " +
            "WHERE epochDay = :epochDay AND packageName = :pkg AND reason = :reason"
    )
    suspend fun addSeconds(epochDay: Long, pkg: String, reason: String, seconds: Long)
}
