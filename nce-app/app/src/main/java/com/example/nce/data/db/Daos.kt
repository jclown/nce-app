package com.example.nce.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 课文卡片视图（lesson + 完成状态） */
data class LessonCard(
    val id: Long,
    val bookId: Int,
    val lessonNoLabel: String,
    val title: String,
    val sentenceCount: Int,
    val completed: Boolean,
    val reviewPassed: Boolean,
)

@Dao
interface BookDao {
    @Upsert
    suspend fun upsert(book: Book)

    @Query("SELECT * FROM book ORDER BY id ASC")
    fun all(): Flow<List<Book>>
}

@Dao
interface LessonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLesson(lesson: Lesson): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSentences(sentences: List<Sentence>)

    @Query(
        "SELECT l.id, l.bookId, l.lessonNoLabel, l.title, l.sentenceCount, " +
            "IFNULL(p.completed, 0) AS completed, IFNULL(p.reviewPassed, 0) AS reviewPassed " +
            "FROM lesson l LEFT JOIN lesson_progress p ON p.lessonId = l.id " +
            "WHERE l.bookId = :bookId ORDER BY l.firstLessonNo ASC",
    )
    fun cardsOfBook(bookId: Int): Flow<List<LessonCard>>

    @Query("SELECT * FROM lesson WHERE id = :id")
    fun lessonById(id: Long): Flow<Lesson?>

    @Query("SELECT * FROM lesson WHERE id = :id")
    suspend fun lessonByIdOnce(id: Long): Lesson?

    @Query("SELECT * FROM sentence WHERE lessonId = :lessonId ORDER BY `index` ASC")
    fun sentencesOfLesson(lessonId: Long): Flow<List<Sentence>>

    @Query("SELECT COUNT(*) FROM lesson")
    suspend fun lessonCount(): Int

    @Query("SELECT COUNT(*) FROM book")
    suspend fun bookCount(): Int
}

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: LessonProgress)

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId")
    fun progressOf(lessonId: Long): Flow<LessonProgress?>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId")
    suspend fun progressOfOnce(lessonId: Long): LessonProgress?
}

@Dao
interface CheckInDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(checkIn: CheckIn)

    @Query("SELECT * FROM check_in")
    fun observeAll(): Flow<List<CheckIn>>

    @Query("SELECT DISTINCT date FROM check_in ORDER BY date DESC LIMIT 500")
    suspend fun distinctDatesDesc(): List<Long>

    @Query("SELECT date, COUNT(*) AS cnt FROM check_in WHERE date BETWEEN :from AND :to GROUP BY date")
    suspend fun countByDateRange(from: Long, to: Long): List<DateCount>

    @Query("SELECT date FROM check_in WHERE date = :date")
    suspend fun lessonsOfDate(date: Long): List<Long>
}

data class DateCount(val date: Long, val cnt: Int)

@Dao
interface RecordingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recording: Recording)

    @Query("SELECT * FROM recording WHERE lessonId = :lessonId")
    fun recordingsOfLesson(lessonId: Long): Flow<List<Recording>>

    @Query("SELECT * FROM recording WHERE lessonId = :lessonId AND sentenceIndex = :index")
    suspend fun recordingAt(lessonId: Long, index: Int): Recording?

    @Query("SELECT COUNT(*) FROM recording WHERE lessonId = :lessonId")
    suspend fun countOfLesson(lessonId: Long): Int

    @Query("DELETE FROM recording WHERE lessonId = :lessonId AND sentenceIndex = :index")
    suspend fun deleteAt(lessonId: Long, index: Int)
}
