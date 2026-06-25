package com.cogelasuave.domain.repository

import com.cogelasuave.domain.model.AppInfo
import com.cogelasuave.domain.model.WatchedApp
import kotlinx.coroutines.flow.Flow

interface WatchedAppRepository {

    /** All launchable apps on the device merged with their watched state. */
    fun observeInstalledApps(): Flow<List<AppInfo>>

    /** Just the apps currently being watched (what the service cares about). */
    fun observeWatchedApps(): Flow<List<WatchedApp>>

    suspend fun setWatched(packageName: String, label: String, watched: Boolean)

    suspend fun setCustomWait(packageName: String, customWaitSeconds: Int?)
}
