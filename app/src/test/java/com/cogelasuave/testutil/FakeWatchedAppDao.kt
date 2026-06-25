package com.cogelasuave.testutil

import com.cogelasuave.data.local.dao.WatchedAppDao
import com.cogelasuave.data.local.entity.WatchedAppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [WatchedAppDao] used to supply app labels to the stats repository. */
class FakeWatchedAppDao(initial: List<WatchedAppEntity> = emptyList()) : WatchedAppDao {

    private val apps = MutableStateFlow(initial.associateBy { it.packageName })

    override fun observeAll(): Flow<List<WatchedAppEntity>> = apps.map { it.values.toList() }

    override fun observeWatched(): Flow<List<WatchedAppEntity>> =
        apps.map { map -> map.values.filter { it.isWatched } }

    override suspend fun findByPackage(packageName: String): WatchedAppEntity? = apps.value[packageName]

    override suspend fun upsert(entity: WatchedAppEntity) {
        apps.value = apps.value + (entity.packageName to entity)
    }
}
