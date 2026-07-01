package com.cogelasuave.data.repository

import com.cogelasuave.data.local.TransactionRunner
import com.cogelasuave.data.local.dao.DailyStatDao
import com.cogelasuave.data.local.dao.ReasonStatDao
import com.cogelasuave.data.local.dao.WatchedAppDao
import com.cogelasuave.domain.model.DailyStat
import com.cogelasuave.domain.model.DayStats
import com.cogelasuave.domain.model.InterceptionDecision
import com.cogelasuave.domain.model.ReasonStat
import com.cogelasuave.domain.repository.StatsRepository
import com.cogelasuave.domain.util.DateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val dailyStatDao: DailyStatDao,
    private val reasonStatDao: ReasonStatDao,
    private val watchedAppDao: WatchedAppDao,
    private val dateProvider: DateProvider,
) : StatsRepository {

    override fun observeTodayStats(): Flow<List<DailyStat>> {
        val today = dateProvider.todayEpochDay()
        return combine(
            dailyStatDao.observeForDay(today),
            watchedAppDao.observeAll(),
        ) { stats, apps ->
            val labels = apps.associate { it.packageName to it.label }
            stats.map { row ->
                DailyStat(
                    packageName = row.packageName,
                    label = labels[row.packageName] ?: row.packageName,
                    attempts = row.attempts,
                    dismissed = row.dismissed,
                    opened = row.opened,
                )
            }
        }
    }

    override fun observeWeeklyStats(days: Int): Flow<List<DayStats>> {
        val today = dateProvider.todayEpochDay()
        // Window is the last [days] days ending today (inclusive), clamped to >= 1.
        val span = days.coerceAtLeast(1)
        val startDay = today - (span - 1)
        return dailyStatDao.observeDailyTotalsBetween(startDay, today).map { totals ->
            val byDay = totals.associateBy { it.epochDay }
            // Fill every day in the window, oldest first, zeroing idle days.
            (startDay..today).map { day ->
                val row = byDay[day]
                DayStats(
                    epochDay = day,
                    attempts = row?.attempts ?: 0,
                    dismissed = row?.dismissed ?: 0,
                    opened = row?.opened ?: 0,
                )
            }
        }
    }

    override suspend fun recordAttempt(packageName: String): Int {
        val today = dateProvider.todayEpochDay()
        return transactionRunner {
            dailyStatDao.ensureRow(today, packageName)
            dailyStatDao.incrementAttempts(today, packageName)
            dailyStatDao.getAttempts(today, packageName) ?: 1
        }
    }

    override suspend fun recordDecision(packageName: String, decision: InterceptionDecision) {
        val today = dateProvider.todayEpochDay()
        transactionRunner {
            dailyStatDao.ensureRow(today, packageName)
            when (decision) {
                InterceptionDecision.OPENED -> dailyStatDao.incrementOpened(today, packageName)
                InterceptionDecision.DISMISSED -> dailyStatDao.incrementDismissed(today, packageName)
            }
        }
    }

    override fun observeReasonStats(days: Int): Flow<List<ReasonStat>> {
        val today = dateProvider.todayEpochDay()
        val startDay = today - (days.coerceAtLeast(1) - 1)
        return reasonStatDao.observeReasonTotalsBetween(startDay, today).map { totals ->
            totals.map { ReasonStat(reason = it.reason, seconds = it.seconds, opens = it.opens) }
        }
    }

    override suspend fun recordOpen(packageName: String, reason: String) {
        val today = dateProvider.todayEpochDay()
        transactionRunner {
            dailyStatDao.ensureRow(today, packageName)
            dailyStatDao.incrementOpened(today, packageName)
            reasonStatDao.ensureRow(today, packageName, reason)
            reasonStatDao.incrementOpens(today, packageName, reason)
        }
    }

    override suspend fun addReasonTime(
        packageName: String,
        reason: String,
        epochDay: Long,
        seconds: Long,
    ) {
        if (seconds <= 0) return
        transactionRunner {
            reasonStatDao.ensureRow(epochDay, packageName, reason)
            reasonStatDao.addSeconds(epochDay, packageName, reason, seconds)
        }
    }
}
