package com.cogelasuave.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per (day, app). [epochDay] is [java.time.LocalDate.toEpochDay], so "today"
 * queries naturally roll over at local midnight.
 */
@Entity(
    tableName = "daily_stats",
    indices = [Index(value = ["epochDay", "packageName"], unique = true)],
)
data class DailyStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val packageName: String,
    val attempts: Int = 0,
    val dismissed: Int = 0,
    val opened: Int = 0,
)
