package com.cogelasuave.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per (day, app, reason). Accumulates how many times the app was opened
 * under that reason and how many seconds were spent inside afterwards.
 * [epochDay] is [java.time.LocalDate.toEpochDay] of the session start.
 */
@Entity(
    tableName = "reason_stats",
    indices = [Index(value = ["epochDay", "packageName", "reason"], unique = true)],
)
data class ReasonStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val packageName: String,
    val reason: String,
    val opens: Int = 0,
    val seconds: Long = 0,
)
