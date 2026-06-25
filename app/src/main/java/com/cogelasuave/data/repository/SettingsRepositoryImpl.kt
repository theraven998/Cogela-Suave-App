package com.cogelasuave.data.repository

import com.cogelasuave.data.local.dao.SettingsDao
import com.cogelasuave.data.local.entity.SettingsEntity
import com.cogelasuave.domain.model.AppSettings
import com.cogelasuave.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dao: SettingsDao,
) : SettingsRepository {

    override fun observeSettings(): Flow<AppSettings> =
        dao.observe().map { it.toDomain() }

    override suspend fun getSettings(): AppSettings = dao.get().toDomain()

    override suspend fun setGlobalWaitSeconds(seconds: Int) {
        val current = dao.get() ?: SettingsEntity()
        dao.upsert(current.copy(globalWaitSeconds = seconds))
    }

    override suspend fun setEstimatedSessionMinutes(minutes: Int) {
        val current = dao.get() ?: SettingsEntity()
        dao.upsert(current.copy(estimatedSessionMinutes = minutes))
    }

    private fun SettingsEntity?.toDomain(): AppSettings =
        this?.let {
            AppSettings(
                globalWaitSeconds = it.globalWaitSeconds,
                estimatedSessionMinutes = it.estimatedSessionMinutes,
            )
        } ?: AppSettings()
}
