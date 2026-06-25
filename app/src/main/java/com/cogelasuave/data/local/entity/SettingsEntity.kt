package com.cogelasuave.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cogelasuave.domain.model.AppSettings

/** Single-row table (id is always [SINGLETON_ID]) holding global settings. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val globalWaitSeconds: Int = AppSettings.DEFAULT_WAIT_SECONDS,
    val estimatedSessionMinutes: Int = AppSettings.DEFAULT_SESSION_MINUTES,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
