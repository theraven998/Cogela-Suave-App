package com.cogelasuave.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cogelasuave.data.local.dao.DailyStatDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val PKG = "com.example.app"

@RunWith(AndroidJUnit4::class)
class DailyStatDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DailyStatDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dailyStatDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun ensureRowAndIncrement_buildUpTodayCounters() = runTest {
        dao.ensureRow(100, PKG)
        dao.incrementAttempts(100, PKG)
        dao.incrementAttempts(100, PKG)
        dao.incrementDismissed(100, PKG)

        assertEquals(2, dao.getAttempts(100, PKG))
        val day = dao.observeForDay(100).first()
        assertEquals(1, day.size)
        assertEquals(2, day.first().attempts)
        assertEquals(1, day.first().dismissed)
    }

    @Test
    fun dailyTotalsBetween_aggregatesByDayAndIgnoresOutsideRange() = runTest {
        // Day 98 (outside), day 99 and day 100 (inside the 99..100 window).
        dao.ensureRow(98, PKG)
        dao.incrementAttempts(98, PKG)
        dao.ensureRow(99, PKG)
        dao.incrementAttempts(99, PKG)
        dao.incrementDismissed(99, PKG)
        dao.ensureRow(100, "$PKG.other")
        dao.incrementAttempts(100, "$PKG.other")
        dao.incrementOpened(100, "$PKG.other")

        val totals = dao.observeDailyTotalsBetween(99, 100).first()
        assertEquals(2, totals.size)
        assertTrue(totals.none { it.epochDay == 98L })

        val d99 = totals.first { it.epochDay == 99L }
        assertEquals(1, d99.attempts)
        assertEquals(1, d99.dismissed)

        val d100 = totals.first { it.epochDay == 100L }
        assertEquals(1, d100.attempts)
        assertEquals(1, d100.opened)
    }
}
