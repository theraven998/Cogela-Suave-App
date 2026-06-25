package com.cogelasuave.domain.repository

import com.cogelasuave.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    fun observeSettings(): Flow<AppSettings>

    suspend fun getSettings(): AppSettings

    suspend fun setGlobalWaitSeconds(seconds: Int)

    suspend fun setEstimatedSessionMinutes(minutes: Int)
}
