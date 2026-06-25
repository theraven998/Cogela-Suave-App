package com.cogelasuave.domain.usecase

import com.cogelasuave.domain.model.InterceptionDecision
import com.cogelasuave.domain.repository.StatsRepository
import javax.inject.Inject

class RecordDecisionUseCase @Inject constructor(
    private val repository: StatsRepository,
) {
    suspend operator fun invoke(packageName: String, decision: InterceptionDecision) =
        repository.recordDecision(packageName, decision)
}
