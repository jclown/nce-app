package com.example.nce.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RecordState(
    val sentenceIndex: Int = -1,
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0,
)

@Singleton
class SentenceRecorder @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    private var outputFile: File? = null
    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())

    private val _state = MutableStateFlow(RecordState())
    val state: StateFlow<RecordState> = _state.asStateFlow()

    /** 30 秒上限自动停止 */
    fun start(lessonId: Long, sentenceIndex: Int): Result<File> {
        stopRecording()
        val dir = File(appContext.filesDir, "recordings/$lessonId").apply { mkdirs() }
        val out = File(dir, "s$sentenceIndex.m4a")
        return runCatching {
            val r = buildRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(16_000)
                setMaxDuration(MAX_MS.toInt())
                setOutputFile(out.absolutePath)
                prepare()
                start()
            }
            recorder = r
            outputFile = out
            startedAt = System.currentTimeMillis()
            _state.value = RecordState(sentenceIndex = sentenceIndex, isRecording = true)
            ticker.postDelayed(tick, 200L)
            out
        }
    }

    /** @return 成功返回 (sentenceIndex, 文件)；失败返回 null（文件已删除） */
    fun stopRecording(): Pair<Int, File>? {
        val r = recorder ?: return null
        val idx = _state.value.sentenceIndex
        val out = outputFile
        ticker.removeCallbacks(tick)
        val stopped = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        outputFile = null
        _state.value = RecordState()
        if (!stopped || out == null || !out.exists() || out.length() == 0L) {
            runCatching { out?.delete() }
            return null
        }
        return Pair(idx, out)
    }

    fun cancel() {
        val r = recorder ?: return
        val out = outputFile
        ticker.removeCallbacks(tick)
        runCatching { r.stop() }
        r.release()
        recorder = null
        runCatching { out?.delete() }
        outputFile = null
        _state.value = RecordState()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!_state.value.isRecording) return
            _state.value = _state.value.copy(elapsedMs = System.currentTimeMillis() - startedAt)
            ticker.postDelayed(this, 200L)
        }
    }

    private fun buildRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else MediaRecorder()

    companion object {
        const val MAX_MS = 30_000L
    }
}
