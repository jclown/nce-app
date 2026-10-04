package com.example.nce.di

import com.example.nce.data.db.BookDao
import com.example.nce.data.db.CheckInDao
import com.example.nce.data.db.LessonDao
import com.example.nce.data.db.NceDatabase
import com.example.nce.data.db.ProgressDao
import com.example.nce.data.db.RecordingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {
    @Provides fun provideBookDao(db: NceDatabase): BookDao = db.bookDao()
    @Provides fun provideLessonDao(db: NceDatabase): LessonDao = db.lessonDao()
    @Provides fun provideProgressDao(db: NceDatabase): ProgressDao = db.progressDao()
    @Provides fun provideCheckInDao(db: NceDatabase): CheckInDao = db.checkInDao()
    @Provides fun provideRecordingDao(db: NceDatabase): RecordingDao = db.recordingDao()
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
