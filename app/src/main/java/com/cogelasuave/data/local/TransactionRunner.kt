package com.cogelasuave.data.local

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny seam over Room's [withTransaction] so repositories can run atomic blocks
 * without depending on the concrete [AppDatabase] directly. This keeps the
 * record-and-read logic unit-testable on the JVM (see the fake in test sources).
 */
interface TransactionRunner {
    suspend operator fun <R> invoke(block: suspend () -> R): R
}

@Singleton
class RoomTransactionRunner @Inject constructor(
    private val database: AppDatabase,
) : TransactionRunner {
    override suspend fun <R> invoke(block: suspend () -> R): R =
        database.withTransaction { block() }
}
