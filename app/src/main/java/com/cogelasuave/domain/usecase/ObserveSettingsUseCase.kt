package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.model.AppSettings
import com.cogelasuave.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<AppSettings> = repository.observeSettings()
}
