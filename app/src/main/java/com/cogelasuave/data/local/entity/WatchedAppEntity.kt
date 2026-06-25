package com.cogelasuave.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watched_apps")
data class WatchedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val isWatched: Boolean,
    /** null = use the global wait. */
    val customWaitSeconds: Int?,
)
