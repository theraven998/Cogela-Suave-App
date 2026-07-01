package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.repository.StatsRepository
import javax.inject.Inject

/**
 * Adds [seconds] spent inside [packageName] to the [reason] bucket for the day the
 * session started ([epochDay]). Called when the user leaves the app.
 */
class AddReasonTimeUseCase @Inject constructor(
    private val repository: StatsRepository,
) {
    suspend operator fun invoke(
        packageName: String,
        reason: String,
        epochDay: Long,
        seconds: Long,
    ) = repository.addReasonTime(packageName, reason, epochDay, seconds)
}
