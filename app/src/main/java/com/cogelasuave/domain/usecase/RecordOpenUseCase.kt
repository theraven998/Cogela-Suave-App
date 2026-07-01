package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.repository.StatsRepository
import javax.inject.Inject

/** Records that the user opened [packageName] today under [reason]. */
class RecordOpenUseCase @Inject constructor(
    private val repository: StatsRepository,
) {
    suspend operator fun invoke(packageName: String, reason: String) =
        repository.recordOpen(packageName, reason)
}
