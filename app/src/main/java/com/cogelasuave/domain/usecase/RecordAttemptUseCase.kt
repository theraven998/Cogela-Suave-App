package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.repository.StatsRepository
import javax.inject.Inject

class RecordAttemptUseCase @Inject constructor(
    private val repository: StatsRepository,
) {
    /** Returns the number of attempts for this app today, including this one. */
    suspend operator fun invoke(packageName: String): Int =
        repository.recordAttempt(packageName)
}
