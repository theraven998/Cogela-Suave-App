package com.cogelasuave.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cogelasuave.data.local.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM settings WHERE id = :id LIMIT 1")
    fun observe(id: Int = SettingsEntity.SINGLETON_ID): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = :id LIMIT 1")
    suspend fun get(id: Int = SettingsEntity.SINGLETON_ID): SettingsEntity?

    @Upsert
    suspend fun upsert(entity: SettingsEntity)
}
