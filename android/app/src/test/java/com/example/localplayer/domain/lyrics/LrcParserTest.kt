package com.example.localplayer.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `synced lines are parsed and sorted by time`() {
        val parsed = LrcParser.parse(
            """
            [00:12.00]Second line
            [00:05.50]First line
            [01:02]Third line
            """.trimIndent(),
        )

        assertEquals(listOf("First line", "Second line", "Third line"), parsed.lines.map { it.text })
        assertEquals(5_500L, parsed.lines[0].timeMs)
        assertEquals(12_000L, parsed.lines[1].timeMs)
        assertEquals(62_000L, parsed.lines[2].timeMs)
    }

    @Test
    fun `fraction without leading zeros is scaled`() {
        val parsed = LrcParser.parse("[00:05.4]Line")

        assertEquals(5_400L, parsed.lines.single().timeMs)
    }

    @Test
    fun `offset tag shifts timestamps and cannot go below zero`() {
        val parsed = LrcParser.parse(
            """
            [offset:+500]
            [00:05.00]Shifted
            [offset:-2000]
            [00:01.00]Clamped
            """.trimIndent(),
        )

        // После сортировки строки идут по времени, а не по порядку в файле.
        assertEquals("Clamped", parsed.lines[0].text)
        assertEquals(3_000L, parsed.lines[0].timeMs)
        assertEquals("Shifted", parsed.lines[1].text)
        assertEquals(4_500L, parsed.lines[1].timeMs)
    }

    @Test
    fun `several timestamps on one line produce several entries`() {
        val parsed = LrcParser.parse("[00:10.00][00:50.00]Chorus")

        assertEquals(2, parsed.lines.size)
        assertEquals("Chorus", parsed.lines[0].text)
        assertEquals("Chorus", parsed.lines[1].text)
        assertEquals(10_000L, parsed.lines[0].timeMs)
        assertEquals(50_000L, parsed.lines[1].timeMs)
    }

    @Test
    fun `meta tags are skipped but plain text is kept`() {
        val parsed = LrcParser.parse(
            """
            [ar:Artist]
            [ti:Title]
            [00:01.00]Line one
            just a note
            """.trimIndent(),
        )

        assertEquals(1, parsed.lines.size)
        assertEquals("Line one\njust a note", parsed.plain)
    }

    @Test
    fun `plain text without timestamps stays unsynced`() {
        val parsed = LrcParser.parse("First verse\nSecond verse")

        assertTrue(parsed.lines.isEmpty())
        assertEquals("First verse\nSecond verse", parsed.plain)
    }

    @Test
    fun `plain fallback is built from synced lines when needed`() {
        val parsed = LrcParser.parse("[00:01.00]One\n[00:02.00]\n[00:03.00]Three")

        assertEquals("One\nThree", parsed.plain)
    }
}
