package com.example.nce.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    version = 1,
    exportSchema = false,
    entities = [
        Book::class,
        Lesson::class,
        Sentence::class,
        LessonProgress::class,
        CheckIn::class,
        Recording::class,
    ],
)
abstract class NceDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun lessonDao(): LessonDao
    abstract fun progressDao(): ProgressDao
    abstract fun checkInDao(): CheckInDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        const val NAME = "nce.db"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDb(@ApplicationContext ctx: Context): NceDatabase =
        Room.databaseBuilder(ctx, NceDatabase::class.java, NceDatabase.NAME).build()
}
