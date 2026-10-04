package com.example.nce.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "book")
data class Book(
    @PrimaryKey val id: Int,
    val name: String,
    val shortName: String,
    val lessonCount: Int,
)

@Entity(
    tableName = "lesson",
    indices = [Index("bookId"), Index(value = ["assetKey"], unique = true)],
)
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int,
    /** 例 "NCE1/001&002.Excuse Me"（不含扩展名），同时作为解压文件名前缀 */
    val assetKey: String,
    /** "第1&2课" / "第5课" */
    val lessonNoLabel: String,
    val firstLessonNo: Int,
    val title: String,
    val audioPath: String,
    val durationMs: Long,
    val sentenceCount: Int,
)

@Entity(
    tableName = "sentence",
    indices = [Index("lessonId")],
    primaryKeys = ["lessonId", "index"],
)
data class Sentence(
    val lessonId: Long,
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val en: String,
    val cn: String?,
)

@Entity(
    tableName = "lesson_progress",
    indices = [Index(value = ["lessonId"], unique = true)],
)
data class LessonProgress(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val lastSentenceIndex: Int = 0,
    /** 已完整播放过的句子索引，逗号分隔 */
    val listenedIndices: String = "",
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val reviewPassed: Boolean = false,
)

@Entity(
    tableName = "check_in",
    indices = [Index("date"), Index("lessonId"), Index(value = ["date", "lessonId"], unique = true)],
)
data class CheckIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LocalDate.toEpochDay() */
    val date: Long,
    val lessonId: Long,
    val createdAt: Long,
)

@Entity(tableName = "recording", indices = [Index(value = ["lessonId", "sentenceIndex"], unique = true)])
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val sentenceIndex: Int,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
)
