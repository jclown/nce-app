package com.example.nce.domain

object StreakCalculator {
    /**
     * @param datesDesc check_in 去重后的日期（epochDay），降序
     * @param today LocalDate.now().toEpochDay()
     */
    fun calc(datesDesc: List<Long>, today: Long): Int {
        if (datesDesc.isEmpty()) return 0
        val distinct = datesDesc.distinct()
        var cursor = when (distinct.first()) {
            today -> today
            today - 1 -> today - 1
            else -> return 0
        }
        var streak = 0
        for (d in distinct) {
            when {
                d == cursor -> { streak++; cursor-- }
                d < cursor -> break
            }
        }
        return streak
    }
}
