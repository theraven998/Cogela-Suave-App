package com.cogelasuave.testutil

import com.cogelasuave.data.local.TransactionRunner

/** Runs the block inline; the fake DAOs are already single-threaded and atomic enough for tests. */
class FakeTransactionRunner : TransactionRunner {
    override suspend fun <R> invoke(block: suspend () -> R): R = block()
}
