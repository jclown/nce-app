package com.example.nce.data.repo

import com.example.nce.data.db.CheckIn
import com.example.nce.data.db.CheckInDao
import com.example.nce.domain.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

data class StreakInfo(val streak: Int, val todayCount: Int)

@Singleton
class CheckInRepository @Inject constructor(
    private val checkInDao: CheckInDao,
) {
    /** 全量打卡记录（响应式） */
    fun observeAll(): Flow<List<CheckIn>> = checkInDao.observeAll()

    fun streakInfoOf(all: List<CheckIn>): StreakInfo {
        val today = LocalDate.now().toEpochDay()
        val datesDesc = all.map { it.date }.distinct().sortedDescending()
        return StreakInfo(
            streak = StreakCalculator.calc(datesDesc, today),
            todayCount = all.count { it.date == today },
        )
    }

    fun monthMarksOf(all: List<CheckIn>, ym: YearMonth): Map<Long, Int> {
        val from = ym.atDay(1).toEpochDay()
        val to = ym.atEndOfMonth().toEpochDay()
        return all.filter { it.date in from..to }
            .groupingBy { it.date }
            .eachCount()
    }
}
