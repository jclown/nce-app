package com.example.nce.data.repo

import com.example.nce.data.db.CheckIn
import com.example.nce.data.db.CheckInDao
import com.example.nce.data.db.LessonDao
import com.example.nce.data.db.LessonProgress
import com.example.nce.data.db.ProgressDao
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressRepository @Inject constructor(
    private val lessonDao: LessonDao,
    private val progressDao: ProgressDao,
    private val checkInDao: CheckInDao,
) {
    fun progressOf(lessonId: Long): Flow<LessonProgress?> = progressDao.progressOf(lessonId)

    /** 一句完整播完时调用；全部句子播完则自动完成并打卡 */
    suspend fun markSentenceListened(lessonId: Long, index: Int) {
        val lesson = lessonDao.lessonByIdOnce(lessonId) ?: return
        val p = progressDao.progressOfOnce(lessonId) ?: LessonProgress(lessonId = lessonId)
        val listened = p.listenedIndices.toIndexSet()
        if (!listened.add(index)) return // 已记录过
        val completed = !p.completed && listened.size >= lesson.sentenceCount
        progressDao.upsert(
            p.copy(
                lastSentenceIndex = index,
                listenedIndices = listened.joinToString(","),
                completed = p.completed || completed,
                completedAt = if (completed && p.completedAt == null) System.currentTimeMillis() else p.completedAt,
            ),
        )
        if (completed) checkInToday(lessonId)
    }

    suspend fun updateLastIndex(lessonId: Long, index: Int) {
        val p = progressDao.progressOfOnce(lessonId) ?: LessonProgress(lessonId = lessonId)
        if (p.lastSentenceIndex == index) return
        progressDao.upsert(p.copy(lastSentenceIndex = index))
    }

    /** 手动标记完成本课 */
    suspend fun completeLesson(lessonId: Long) {
        val p = progressDao.progressOfOnce(lessonId) ?: LessonProgress(lessonId = lessonId)
        if (!p.completed) {
            progressDao.upsert(p.copy(completed = true, completedAt = System.currentTimeMillis()))
        }
        checkInToday(lessonId)
    }

    /** 复习全部录完 */
    suspend fun markReviewPassed(lessonId: Long) {
        val p = progressDao.progressOfOnce(lessonId) ?: LessonProgress(lessonId = lessonId)
        if (!p.reviewPassed) {
            progressDao.upsert(p.copy(reviewPassed = true))
        }
    }

    private suspend fun checkInToday(lessonId: Long) {
        checkInDao.insert(
            CheckIn(
                date = LocalDate.now().toEpochDay(),
                lessonId = lessonId,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun String.toIndexSet(): MutableSet<Int> =
        if (isBlank()) mutableSetOf() else split(",").mapNotNull { it.toIntOrNull() }.toMutableSet()
}
