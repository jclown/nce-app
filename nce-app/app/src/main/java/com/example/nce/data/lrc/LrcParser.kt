package com.example.nce.data.lrc

/** 解析出的一行歌词（endMs 由调用方在拿到全部行后填充） */
data class LrcLine(
    val startMs: Long,
    var endMs: Long,
    val en: String,
    val cn: String?,
)

object LrcParser {

    private val Timestamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)""")
    private val LessonHeader = Regex("""^Lesson\s+\d+\s*$""", RegexOption.IGNORE_CASE)

    /**
     * 解析 lrc 文本。
     * - 跳过 [al:] [ar:] [ti:] [by:] [offset:] 等元信息行
     * - 跳过 "Lesson 1 | 第1课" 之类的课号标题行
     * - 每句格式 "English | Chinese"，中文缺失时为 null
     * 注：不按 [ti:] 过滤标题行——NCE1 首句（如 "Excuse me!"）与标题相同但确为正文句子，
     * 误删代价大于收益；标题句作为第一句播放可接受。
     */
    fun parse(text: String): List<LrcLine> {
        val raw = mutableListOf<Triple<Long, String, String?>>() // startMs, en, cn

        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val m = Timestamp.matchEntire(trimmed)
            if (m == null) continue // 元信息行

            val (minStr, secStr, fracStr, contentRaw) = m.destructured
            val content = contentRaw.trim()
            if (content.isEmpty()) continue

            val parts = content.split("|", limit = 2).map { it.trim() }
            val en = parts[0]
            val cn = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
            if (en.isEmpty()) continue

            // 过滤课号标题行
            if (LessonHeader.matches(en)) continue

            val startMs = toMs(minStr, secStr, fracStr)
            raw.add(Triple(startMs, en, cn))
        }

        raw.sortBy { it.first }
        return raw.mapIndexed { i, (start, en, cn) ->
            LrcLine(
                startMs = start,
                endMs = raw.getOrNull(i + 1)?.first ?: Long.MAX_VALUE,
                en = en,
                cn = cn,
            )
        }
    }

    private fun toMs(min: String, sec: String, frac: String?): Long {
        val m = min.toLong()
        val s = sec.toLong()
        val ms = when (frac?.length) {
            null, 0 -> 0L
            1 -> frac.toLong() * 100L
            2 -> frac.toLong() * 10L
            else -> frac.take(3).toLong()
        }
        return ((m * 60) + s) * 1000 + ms
    }
}
