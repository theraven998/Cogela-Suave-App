package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.repository.WatchedAppRepository
import javax.inject.Inject

class SetAppWatchedUseCase @Inject constructor(
    private val repository: WatchedAppRepository,
) {
    suspend operator fun invoke(packageName: String, label: String, watched: Boolean) =
        repository.setWatched(packageName, label, watched)
}
