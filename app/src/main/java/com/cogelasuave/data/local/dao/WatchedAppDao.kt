package com.cogelasuave.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.cogelasuave.data.local.entity.WatchedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedAppDao {

    @Query("SELECT * FROM watched_apps")
    fun observeAll(): Flow<List<WatchedAppEntity>>

    @Query("SELECT * FROM watched_apps WHERE isWatched = 1")
    fun observeWatched(): Flow<List<WatchedAppEntity>>

    @Query("SELECT * FROM watched_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun findByPackage(packageName: String): WatchedAppEntity?

    @Upsert
    suspend fun upsert(entity: WatchedAppEntity)

    /** Full snapshot for the backup export. */
    @Query("SELECT * FROM watched_apps")
    suspend fun getAll(): List<WatchedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WatchedAppEntity>)

    @Query("DELETE FROM watched_apps")
    suspend fun clear()
}
