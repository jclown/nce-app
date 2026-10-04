package com.example.nce.ui.lesson

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nce.data.db.Lesson
import com.example.nce.data.db.LessonDao
import com.example.nce.data.db.LessonProgress
import com.example.nce.data.db.Sentence
import com.example.nce.data.repo.LanguageMode
import com.example.nce.data.repo.ProgressRepository
import com.example.nce.data.repo.SettingsRepository
import com.example.nce.player.PlayState
import com.example.nce.player.SentencePlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LessonUiState(
    val lesson: Lesson? = null,
    val sentences: List<Sentence> = emptyList(),
    val playState: PlayState = PlayState(),
    val languageMode: LanguageMode = LanguageMode.EN_CN,
    val progress: LessonProgress? = null,
)

@HiltViewModel
class LessonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val lessonDao: LessonDao,
    private val player: SentencePlayer,
    private val progressRepo: ProgressRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val lessonId: Long = savedStateHandle.get<Long>("lessonId") ?: 0L

    private val autoPlayPref = kotlinx.coroutines.flow.MutableStateFlow(true)

    val uiState: StateFlow<LessonUiState> = combine(
        lessonDao.lessonById(lessonId),
        lessonDao.sentencesOfLesson(lessonId),
        player.state,
        settingsRepo.languageMode,
        progressRepo.progressOf(lessonId),
    ) { lesson, sentences, playState, mode, progress ->
        LessonUiState(lesson, sentences, playState, mode, progress)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LessonUiState())

    init {
        ensureLoaded()
    }

    /** 装载本课音频（播放器为本 ViewModel 私有实例，不受其他页面影响） */
    fun ensureLoaded() {
        player.setAutoPlay(autoPlayPref.value)
        player.onSentenceFinished = { index ->
            viewModelScope.launch { progressRepo.markSentenceListened(lessonId, index) }
        }
        viewModelScope.launch {
            val lesson = lessonDao.lessonByIdOnce(lessonId) ?: return@launch
            val sentences = lessonDao.sentencesOfLesson(lessonId).first()
            player.load(lesson.audioPath, sentences)
        }
    }

    fun toggleSentence(index: Int) {
        player.toggle(index)
        viewModelScope.launch { progressRepo.updateLastIndex(lessonId, index) }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch { settingsRepo.setLanguageMode(mode) }
    }

    fun setAutoPlay(on: Boolean) {
        autoPlayPref.value = on
        player.setAutoPlay(on)
    }

    fun completeLesson() {
        viewModelScope.launch { progressRepo.completeLesson(lessonId) }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
