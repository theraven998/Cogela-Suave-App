package com.cogelasuave.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cogelasuave.data.local.dao.DailyStatDao
import com.cogelasuave.data.local.dao.ReasonStatDao
import com.cogelasuave.data.local.dao.SettingsDao
import com.cogelasuave.data.local.dao.WatchedAppDao
import com.cogelasuave.data.local.entity.DailyStatEntity
import com.cogelasuave.data.local.entity.ReasonStatEntity
import com.cogelasuave.data.local.entity.SettingsEntity
import com.cogelasuave.data.local.entity.WatchedAppEntity

@Database(
    entities = [
        WatchedAppEntity::class,
        SettingsEntity::class,
        DailyStatEntity::class,
        ReasonStatEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchedAppDao(): WatchedAppDao
    abstract fun settingsDao(): SettingsDao
    abstract fun dailyStatDao(): DailyStatDao
    abstract fun reasonStatDao(): ReasonStatDao

    companion object {
        const val NAME = "cogelasuave.db"
    }
}
