package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.repository.WatchedAppRepository
import javax.inject.Inject

class SetAppCustomWaitUseCase @Inject constructor(
    private val repository: WatchedAppRepository,
) {
    /** Pass null to fall back to the global wait. */
    suspend operator fun invoke(packageName: String, customWaitSeconds: Int?) =
        repository.setCustomWait(packageName, customWaitSeconds)
}
