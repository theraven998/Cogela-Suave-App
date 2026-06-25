package com.cogelasuave.data.repository

import com.cogelasuave.data.local.dao.WatchedAppDao
import com.cogelasuave.data.local.entity.WatchedAppEntity
import com.cogelasuave.data.system.InstalledAppsProvider
import com.cogelasuave.domain.model.AppInfo
import com.cogelasuave.domain.model.WatchedApp
import com.cogelasuave.domain.repository.WatchedAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchedAppRepositoryImpl @Inject constructor(
    private val dao: WatchedAppDao,
    private val installedAppsProvider: InstalledAppsProvider,
) : WatchedAppRepository {

    override fun observeInstalledApps(): Flow<List<AppInfo>> {
        // Query the (slow) PackageManager once, then keep merging the live DB state on top.
        val installedFlow = flow { emit(installedAppsProvider.queryLaunchableApps()) }
        return combine(installedFlow, dao.observeAll()) { installed, persisted ->
            val byPackage = persisted.associateBy { it.packageName }
            installed.map { app ->
                val saved = byPackage[app.packageName]
                app.copy(
                    isWatched = saved?.isWatched == true,
                    customWaitSeconds = saved?.customWaitSeconds,
                )
            }
        }
    }

    override fun observeWatchedApps(): Flow<List<WatchedApp>> =
        dao.observeWatched().map { list ->
            list.map { WatchedApp(it.packageName, it.label, it.customWaitSeconds) }
        }

    override suspend fun setWatched(packageName: String, label: String, watched: Boolean) {
        val existing = dao.findByPackage(packageName)
        dao.upsert(
            existing?.copy(label = label, isWatched = watched)
                ?: WatchedAppEntity(
                    packageName = packageName,
                    label = label,
                    isWatched = watched,
                    customWaitSeconds = null,
                )
        )
    }

    override suspend fun setCustomWait(packageName: String, customWaitSeconds: Int?) {
        val existing = dao.findByPackage(packageName) ?: return
        dao.upsert(existing.copy(customWaitSeconds = customWaitSeconds))
    }
}
