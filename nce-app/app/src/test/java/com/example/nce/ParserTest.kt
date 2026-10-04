package com.example.nce

import com.example.nce.data.asset.parseLessonFileName
import com.example.nce.data.lrc.LrcParser
import com.example.nce.domain.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parsesNce1Format() {
        val lrc = """
            [al:新概念英语第一册]
            [ar:山海作品推荐]
            [ti:Excuse Me!]
            [by:山海作品推荐]
            [00:00.61]Lesson 1 | 第1课
            [00:02.71]Excuse me! | 打扰一下！
            [00:05.61]Listen to the tape then answer this question. | 听录音，然后回答这个问题。
            [00:10.80]Whose handbag is it? | 这是谁的手提包？
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        // "Lesson 1 | 第1课" 应被过滤
        assertEquals(3, lines.size)
        assertEquals("Excuse me!", lines[0].en)
        assertEquals("打扰一下！", lines[0].cn)
        assertEquals(2710L, lines[0].startMs)
        assertEquals(5610L, lines[1].startMs)
        assertEquals(Long.MAX_VALUE, lines.last().endMs)
    }

    @Test
    fun keepsTitleLineAsSentence() {
        val lrc = """
            [ti:A puma at large]
            [00:00.68]Lesson 1 | 第1课
            [00:02.36]A puma at large | 豹子出逃
            [00:09.81]Where must the puma have come from? | 这头美洲狮是从哪里来的呢？
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        // 标题句保留，课号行过滤
        assertEquals(2, lines.size)
        assertEquals("A puma at large", lines[0].en)
        assertEquals("Where must the puma have come from?", lines[1].en)
    }

    @Test
    fun handlesMissingChinese() {
        val lrc = "[00:01.00]Only english sentence"
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals("Only english sentence", lines[0].en)
        assertNull(lines[0].cn)
    }

    @Test
    fun normalizesFractionDigits() {
        // 两位 = 百分秒，三位 = 毫秒
        assertEquals(610L, LrcParser.parse("[00:00.61]a | b")[0].startMs)
        assertEquals(612L, LrcParser.parse("[00:00.612]a | b")[0].startMs)
        assertEquals(100L, LrcParser.parse("[00:00.1]a | b")[0].startMs)
    }
}

class FileNameTest {
    @Test
    fun parsesNce1MergedLesson() {
        val f = parseLessonFileName("001&002.Excuse Me")!!
        assertEquals(listOf(1, 2), f.lessonNos)
        assertEquals("Excuse Me", f.title)
    }

    @Test
    fun parsesSingleLesson() {
        val f = parseLessonFileName("01.A Private Conversation")!!
        assertEquals(listOf(1), f.lessonNos)
        assertEquals("A Private Conversation", f.title)
    }

    @Test
    fun rejectsGarbage() {
        assertTrue(parseLessonFileName("no-number.Name") == null)
    }
}

class StreakTest {
    @Test
    fun todayIncluded() {
        assertEquals(3, StreakCalculator.calc(listOf(100, 99, 98, 90), 100))
    }

    @Test
    fun startsFromYesterday() {
        assertEquals(2, StreakCalculator.calc(listOf(99, 98, 90), 100))
    }

    @Test
    fun brokenStreak() {
        assertEquals(0, StreakCalculator.calc(listOf(97, 96), 100))
    }

    @Test
    fun empty() {
        assertEquals(0, StreakCalculator.calc(emptyList(), 100))
    }

    @Test
    fun duplicatesIgnored() {
        assertEquals(2, StreakCalculator.calc(listOf(100, 100, 99, 99), 100))
    }
}
