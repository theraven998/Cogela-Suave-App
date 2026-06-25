package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Resolves the obligatory wait for an interception: the app's own override if set,
 * otherwise the global default.
 */
class ResolveWaitSecondsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(customWaitSeconds: Int?): Int =
        customWaitSeconds ?: settingsRepository.getSettings().globalWaitSeconds
}
