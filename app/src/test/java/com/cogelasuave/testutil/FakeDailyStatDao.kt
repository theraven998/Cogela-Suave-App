package com.cogelasuave.testutil

import com.cogelasuave.data.local.dao.DailyStatDao
import com.cogelasuave.data.local.entity.DailyStatEntity
import com.cogelasuave.data.local.entity.DailyTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [DailyStatDao] that mirrors the SQL semantics of the real DAO so the
 * repository can be unit-tested on the JVM without Room. Keyed by (epochDay, packageName).
 */
class FakeDailyStatDao : DailyStatDao {

    private data class Key(val epochDay: Long, val pkg: String)

    private val rows = MutableStateFlow<Map<Key, DailyStatEntity>>(emptyMap())
    private var nextId = 1L

    override fun observeForDay(epochDay: Long): Flow<List<DailyStatEntity>> =
        rows.map { map ->
            map.values
                .filter { it.epochDay == epochDay }
                .sortedByDescending { it.attempts }
        }

    override fun observeDailyTotalsBetween(startDay: Long, endDay: Long): Flow<List<DailyTotals>> =
        rows.map { map ->
            map.values
                .filter { it.epochDay in startDay..endDay }
                .groupBy { it.epochDay }
                .map { (day, list) ->
                    DailyTotals(
                        epochDay = day,
                        attempts = list.sumOf { it.attempts },
                        dismissed = list.sumOf { it.dismissed },
                        opened = list.sumOf { it.opened },
                    )
                }
                .sortedBy { it.epochDay }
        }

    override suspend fun getAttempts(epochDay: Long, pkg: String): Int? =
        rows.value[Key(epochDay, pkg)]?.attempts

    override suspend fun ensureRow(epochDay: Long, pkg: String) {
        val key = Key(epochDay, pkg)
        if (rows.value[key] == null) {
            rows.value = rows.value + (key to DailyStatEntity(
                id = nextId++,
                epochDay = epochDay,
                packageName = pkg,
            ))
        }
    }

    override suspend fun incrementAttempts(epochDay: Long, pkg: String) =
        mutate(epochDay, pkg) { it.copy(attempts = it.attempts + 1) }

    override suspend fun incrementDismissed(epochDay: Long, pkg: String) =
        mutate(epochDay, pkg) { it.copy(dismissed = it.dismissed + 1) }

    override suspend fun incrementOpened(epochDay: Long, pkg: String) =
        mutate(epochDay, pkg) { it.copy(opened = it.opened + 1) }

    private inline fun mutate(epochDay: Long, pkg: String, block: (DailyStatEntity) -> DailyStatEntity) {
        val key = Key(epochDay, pkg)
        val current = rows.value[key] ?: return
        rows.value = rows.value + (key to block(current))
    }
}
