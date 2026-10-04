package com.example.nce.ui.review

import android.content.Context
import android.media.MediaPlayer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nce.data.db.LessonDao
import com.example.nce.data.db.RecordingDao
import com.example.nce.data.db.Sentence
import com.example.nce.data.repo.ProgressRepository
import com.example.nce.player.PlayState
import com.example.nce.player.SentencePlayer
import com.example.nce.recorder.SentenceRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReviewUiState(
    val sentences: List<Sentence> = emptyList(),
    /** sentenceIndex -> 录音文件 */
    val recordings: Map<Int, String> = emptyMap(),
    val recordingIndex: Int = -1,
    val elapsedMs: Long = 0,
    val playingIndex: Int = -1,
    val reviewPassed: Boolean = false,
    /** 正在播放原音的句子（-1 表示无） */
    val originalIndex: Int = -1,
    val originalPlaying: Boolean = false,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val lessonDao: LessonDao,
    private val recordingDao: RecordingDao,
    private val progressRepo: ProgressRepository,
    private val recorder: SentenceRecorder,
    private val player: SentencePlayer,
) : ViewModel() {

    val lessonId: Long = savedStateHandle.get<Long>("lessonId") ?: 0L

    private val sentencesFlow = lessonDao.sentencesOfLesson(lessonId)
    private val recordingsFlow = recordingDao.recordingsOfLesson(lessonId)
    private val playingIndexFlow = MutableStateFlow(-1)

    private val baseState = combine(
        sentencesFlow,
        recordingsFlow,
        recorder.state,
        playingIndexFlow,
        progressRepo.progressOf(lessonId),
    ) { sentences, recordings, recState, playing, progress ->
        Quint(sentences, recordings, recState, playing, progress)
    }

    private data class Quint(
        val sentences: List<Sentence>,
        val recordings: List<com.example.nce.data.db.Recording>,
        val recState: com.example.nce.recorder.RecordState,
        val playing: Int,
        val progress: com.example.nce.data.db.LessonProgress?,
    )

    val uiState: StateFlow<ReviewUiState> = combine(
        baseState,
        player.state,
    ) { base, playState ->
        ReviewUiState(
            sentences = base.sentences,
            recordings = base.recordings.associate { it.sentenceIndex to it.filePath },
            recordingIndex = if (base.recState.isRecording) base.recState.sentenceIndex else -1,
            elapsedMs = base.recState.elapsedMs,
            playingIndex = base.playing,
            reviewPassed = base.progress?.reviewPassed == true,
            originalIndex = playState.currentIndex,
            originalPlaying = playState.isPlaying,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReviewUiState())

    init {
        viewModelScope.launch {
            // 复习页原音：逐句播放，关闭自动连播
            player.stop()
            player.setAutoPlay(false)
            player.onSentenceFinished = null
            val lesson = lessonDao.lessonByIdOnce(lessonId) ?: return@launch
            val sentences = lessonDao.sentencesOfLesson(lessonId).first()
            player.load(lesson.audioPath, sentences)
        }
        viewModelScope.launch {
            // 录音超时（30s）自动停止
            recorder.state.collect { st ->
                if (st.isRecording && st.elapsedMs >= SentenceRecorder.MAX_MS) stopRecording()
            }
        }
    }

    /** 点击句子播放/暂停原音 */
    fun toggleOriginal(index: Int) {
        stopPlayback() // 与回放自己的录音互斥
        player.toggle(index)
    }

    fun startRecording(index: Int) {
        stopPlayback()
        player.pause() // 录音前先停掉原音，避免麦克风录入外放
        recorder.start(lessonId, index)
    }

    fun stopRecording() {
        val elapsed = recorder.state.value.elapsedMs
        val result = recorder.stopRecording() ?: return
        val (index, file) = result
        if (index < 0) return
        viewModelScope.launch {
            recordingDao.upsert(
                com.example.nce.data.db.Recording(
                    lessonId = lessonId,
                    sentenceIndex = index,
                    filePath = file.absolutePath,
                    durationMs = elapsed.coerceAtLeast(readDuration(file)),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            checkAllRecorded()
        }
    }

    private fun readDuration(f: File): Long = runCatching {
        val r = android.media.MediaMetadataRetriever()
        r.setDataSource(f.absolutePath)
        val d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    }.getOrDefault(0L)

    fun deleteRecording(index: Int) {
        val path = uiState.value.recordings[index]
        viewModelScope.launch {
            recordingDao.deleteAt(lessonId, index)
            path?.let { runCatching { File(it).delete() } }
        }
    }

    private var playback: MediaPlayer? = null

    fun playRecording(index: Int) {
        val path = uiState.value.recordings[index] ?: return
        stopPlayback()
        player.pause() // 回放自己的录音时停掉原音
        playback = MediaPlayer().apply {
            runCatching {
                setDataSource(path)
                setOnCompletionListener { playingIndexFlow.value = -1; release() }
                prepare()
                start()
            }
        }
        playingIndexFlow.value = index
    }

    fun stopPlayback() {
        runCatching { playback?.stop() }
        playback?.release()
        playback = null
        playingIndexFlow.value = -1
    }

    private suspend fun checkAllRecorded() {
        val lesson = lessonDao.lessonByIdOnce(lessonId) ?: return
        val done = recordingDao.countOfLesson(lessonId)
        if (lesson.sentenceCount > 0 && done >= lesson.sentenceCount) {
            progressRepo.markReviewPassed(lessonId)
        }
    }

    override fun onCleared() {
        recorder.cancel()
        stopPlayback()
        player.release()
        super.onCleared()
    }
}
