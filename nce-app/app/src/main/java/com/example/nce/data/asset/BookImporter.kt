package com.example.nce.data.asset

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.nce.data.db.Book
import com.example.nce.data.db.BookDao
import com.example.nce.data.db.Lesson
import com.example.nce.data.db.LessonDao
import com.example.nce.data.db.Sentence
import com.example.nce.data.lrc.LrcParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportState {
    data object Idle : ImportState
    data object Ready : ImportState
    data class Running(val bookShort: String, val done: Int, val total: Int) : ImportState
    data class Failed(val bookShort: String, val message: String) : ImportState
}

data class LessonFile(val lessonNos: List<Int>, val title: String)

/** 解析 "001&002.Excuse Me" / "01.A Private Conversation"（不含扩展名） */
fun parseLessonFileName(base: String): LessonFile? {
    val m = Regex("""^(\d{1,3})(?:&(\d{1,3}))?\.(.+)$""").matchEntire(base.trim()) ?: return null
    val (a, b, title) = m.destructured
    val nos = listOfNotNull(a.toIntOrNull(), b.toIntOrNull())
    if (nos.isEmpty() || title.isBlank()) return null
    return LessonFile(nos, title.trim())
}

@Singleton
class BookImporter @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val lessonDao: LessonDao,
    private val bookDao: BookDao,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    val books = listOf(
        Book(1, "新概念英语第一册", "NCE1", 72),
        Book(2, "新概念英语第二册", "NCE2", 96),
        Book(3, "新概念英语第三册", "NCE3", 60),
        Book(4, "新概念英语第四册", "NCE4", 48),
    )

    /** 若数据库未初始化则导入全部四册 */
    fun ensureImported() {
        scope.launch(Dispatchers.IO) {
            if (lessonDao.bookCount() > 0 && lessonDao.lessonCount() > 0) {
                _state.value = ImportState.Ready
                return@launch
            }
            for (book in books) {
                runCatching { importBook(book) }
                    .onFailure { _state.value = ImportState.Failed(book.shortName, it.message ?: "unknown") }
            }
            if (_state.value !is ImportState.Failed) _state.value = ImportState.Ready
        }
    }

    private suspend fun importBook(book: Book) {
        val zipName = "books/${book.shortName.lowercase()}.zip"
        val audioDir = File(appContext.filesDir, "audio/${book.shortName}").apply { mkdirs() }

        // Pass 1: 流式把 mp3 落盘（避免整册音频驻留内存）
        val mp3Bases = mutableSetOf<String>()
        forEachEntry(zipName) { base, isMp3, zis ->
            if (!isMp3) return@forEachEntry
            mp3Bases += base
            val out = File(audioDir, "$base.mp3")
            if (!out.exists() || out.length() == 0L) {
                out.outputStream().use { zis.copyTo(it, 64 * 1024) }
            }
        }

        // Pass 2: 收集 lrc 名单
        val lrcBases = mutableSetOf<String>()
        forEachEntry(zipName) { base, isMp3, _ ->
            if (!isMp3) lrcBases += base
        }
        val bases = (mp3Bases intersect lrcBases).sorted()
        val total = bases.size
        var done = 0
        _state.value = ImportState.Running(book.shortName, 0, total.coerceAtLeast(1))

        // Pass 3: 逐个解析 lrc 并入库
        forEachEntry(zipName) { base, isMp3, zis ->
            if (isMp3 || base !in bases) return@forEachEntry
            val parsed = parseLessonFileName(base) ?: return@forEachEntry
            val text = zis.readBytes().toString(Charsets.UTF_8)
            val audioFile = File(audioDir, "$base.mp3")

            val durationMs = readDuration(audioFile)
            val lines = LrcParser.parse(text)
            if (lines.isEmpty()) return@forEachEntry

            val lastEnd = if (durationMs > 0) durationMs else lines.last().startMs + 5_000
            val sentences = lines.mapIndexed { i, l ->
                Sentence(
                    lessonId = 0,
                    index = i,
                    startMs = l.startMs,
                    endMs = if (i == lines.lastIndex) lastEnd else minOf(l.endMs, lastEnd),
                    en = l.en,
                    cn = l.cn,
                )
            }

            val lesson = Lesson(
                bookId = book.id,
                assetKey = "${book.shortName}/$base",
                lessonNoLabel = "第" + parsed.lessonNos.joinToString("&") + "课",
                firstLessonNo = parsed.lessonNos.min(),
                title = parsed.title,
                audioPath = audioFile.absolutePath,
                durationMs = lastEnd,
                sentenceCount = sentences.size,
            )
            val lessonId = lessonDao.upsertLesson(lesson)
            lessonDao.upsertSentences(sentences.map { it.copy(lessonId = lessonId) })
            done++
            _state.value = ImportState.Running(book.shortName, done, total.coerceAtLeast(1))
        }

        bookDao.upsert(book.copy(lessonCount = done))
    }

    /** 遍历 zip 条目；回调里不要关闭流 */
    private suspend inline fun forEachEntry(
        zipName: String,
        crossinline onEntry: suspend (base: String, isMp3: Boolean, zis: ZipInputStream) -> Unit,
    ) {
        appContext.assets.open(zipName).buffered(64 * 1024).use { raw ->
            ZipInputStream(raw).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = File(e.name).name
                    val isMp3 = name.endsWith(".mp3")
                    val base = name.removeSuffix(if (isMp3) ".mp3" else ".lrc")
                    if (base == name) continue // 既非 mp3 也非 lrc
                    onEntry(base, isMp3, zis)
                }
            }
        }
    }

    private fun readDuration(f: File): Long = runCatching {
        val r = MediaMetadataRetriever()
        r.setDataSource(f.absolutePath)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    }.getOrDefault(0L)
}
