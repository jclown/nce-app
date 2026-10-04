package com.example.nce.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.nce.data.db.Sentence
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject

data class PlayState(
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val autoPlay: Boolean = true,
    val ready: Boolean = false,
)

/**
 * 逐句播放器：Media3 ExoPlayer + ClippingConfiguration。
 * 未加 @Singleton，Hilt 对每个注入点新建实例，故课文页与复习页各持一份，
 * 互不干扰（修复共享单例导致的"返回后无法播放"竞态）。
 * - 所有 ExoPlayer 操作在专用 HandlerThread 上执行，不阻塞主线程转场。
 * - ExoPlayer 延迟到首次播放才创建，进页面不再承担播放器初始化成本。
 * - stop() 会 clearMediaItems 释放解码器；release() 额外退出工作线程。
 */
class SentencePlayer @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val workerThread = HandlerThread("SentencePlayer").apply { start() }
    private val worker = Handler(workerThread.looper)

    // 以下字段仅在 worker 线程访问
    private var player: ExoPlayer? = null
    private var sentences: List<Sentence> = emptyList()
    private var audioUri: Uri? = null
    private var loadedUri: Uri? = null

    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state.asStateFlow()

    /** 某句完整播完时回调（UI 侧用于记录进度/打卡），在 worker 线程调用 */
    @Volatile
    var onSentenceFinished: ((Int) -> Unit)? = null

    /** 仅记录音频与句子列表；播放器在首次播放时才创建 */
    fun load(audioPath: String, list: List<Sentence>) = worker.post {
        val uri = Uri.fromFile(File(audioPath))
        if (loadedUri == uri) return@post
        // 切换课文：立即释放旧播放器（连同解码器）
        releasePlayerInternal()
        sentences = list
        audioUri = uri
        loadedUri = uri
        _state.value = PlayState(autoPlay = _state.value.autoPlay)
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(appContext).build()
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> onClipFinished()
                    Player.STATE_READY -> _state.value = _state.value.copy(ready = true)
                    Player.STATE_BUFFERING -> _state.value = _state.value.copy(ready = false)
                    else -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }
        })
        p.playWhenReady = false
        player = p
        return p
    }

    /** 点击某句：同句则暂停/继续；否则从该句开始 */
    fun toggle(index: Int) = worker.post {
        if (index == _state.value.currentIndex && index >= 0) {
            val p = player
            when {
                p == null -> playInternal(index)
                p.isPlaying -> p.pause()
                p.playbackState == Player.STATE_ENDED || p.currentMediaItem == null -> playInternal(index)
                else -> p.play()
            }
            return@post
        }
        playInternal(index)
    }

    fun play(index: Int) = worker.post { playInternal(index) }

    private fun playInternal(index: Int) {
        val s = sentences.getOrNull(index) ?: return
        val uri = audioUri ?: return
        val p = ensurePlayer()
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(s.startMs)
            .setEndPositionMs(if (s.endMs >= Long.MAX_VALUE / 2) C.TIME_UNSET else s.endMs)
            .build()
        p.setMediaItem(MediaItem.fromUri(uri).buildUpon().setClippingConfiguration(clip).build())
        p.prepare()
        p.play()
        _state.value = _state.value.copy(currentIndex = index, isPlaying = true)
    }

    private fun onClipFinished() {
        val idx = _state.value.currentIndex
        if (idx < 0) return
        onSentenceFinished?.invoke(idx)
        val next = idx + 1
        if (_state.value.autoPlay && next < sentences.size) {
            playInternal(next)
        } else {
            player?.pause()
            _state.value = _state.value.copy(currentIndex = -1, isPlaying = false)
        }
    }

    fun pause() = worker.post { player?.pause() }

    /** 停止并释放解码器（保留实例，供同课文续播） */
    fun stop() = worker.post {
        player?.let {
            it.pause()
            it.clearMediaItems()
        }
        _state.value = _state.value.copy(currentIndex = -1, isPlaying = false, ready = false)
    }

    fun setAutoPlay(on: Boolean) {
        _state.value = _state.value.copy(autoPlay = on)
    }

    private fun releasePlayerInternal() {
        player?.release()
        player = null
    }

    fun release() = worker.post {
        releasePlayerInternal()
        sentences = emptyList()
        loadedUri = null
        _state.value = PlayState(autoPlay = _state.value.autoPlay)
        workerThread.quitSafely()
    }
}
