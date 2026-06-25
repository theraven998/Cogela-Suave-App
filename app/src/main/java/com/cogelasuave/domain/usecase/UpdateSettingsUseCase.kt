package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.model.AppSettings
import com.cogelasuave.domain.repository.SettingsRepository
import javax.inject.Inject

/** Small façade over the settings the user can tune from the UI. */
class UpdateSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend fun setGlobalWaitSeconds(seconds: Int) =
        repository.setGlobalWaitSeconds(
            seconds.coerceIn(AppSettings.MIN_WAIT_SECONDS, AppSettings.MAX_WAIT_SECONDS)
        )

    suspend fun setEstimatedSessionMinutes(minutes: Int) =
        repository.setEstimatedSessionMinutes(minutes.coerceIn(1, 120))
}
