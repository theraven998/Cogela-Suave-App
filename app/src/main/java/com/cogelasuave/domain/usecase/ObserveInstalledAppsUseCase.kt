package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.model.AppInfo
import com.cogelasuave.domain.repository.WatchedAppRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveInstalledAppsUseCase @Inject constructor(
    private val repository: WatchedAppRepository,
) {
    operator fun invoke(): Flow<List<AppInfo>> = repository.observeInstalledApps()
}
