package com.cogelasuave.domain.util

/** Abstracts "what day is it" so daily counters are testable and easy to reason about. */
interface DateProvider {
    /** Local date as an epoch-day number; changes at local midnight. */
    fun todayEpochDay(): Long
}
