package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.model.WatchedApp
import com.cogelasuave.domain.repository.WatchedAppRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveWatchedAppsUseCase @Inject constructor(
    private val repository: WatchedAppRepository,
) {
    operator fun invoke(): Flow<List<WatchedApp>> = repository.observeWatchedApps()
}
