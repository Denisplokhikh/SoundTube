package com.example.localplayer.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun `replaces characters illegal on desktop filesystems`() {
        assertEquals(
            "AC DC - Back In Black",
            FileNameSanitizer.sanitize("AC/DC - Back:In*Black"),
        )
        assertEquals(
            "what - ever",
            FileNameSanitizer.sanitize("what \" - \" ever"),
        )
    }

    @Test
    fun `trims dots and surrounding spaces`() {
        assertEquals("track", FileNameSanitizer.sanitize("  ..track..  "))
    }

    @Test
    fun `falls back when nothing is left`() {
        assertEquals("Без названия", FileNameSanitizer.sanitize("   "))
        assertEquals("Неизвестный исполнитель", FileNameSanitizer.sanitize("///", fallback = "Неизвестный исполнитель"))
    }

    @Test
    fun `escapes reserved windows names`() {
        assertEquals("con_", FileNameSanitizer.sanitize("con"))
        assertEquals("NUL_", FileNameSanitizer.sanitize("NUL"))
    }

    @Test
    fun `limits length`() {
        val long = FileNameSanitizer.sanitize("a".repeat(500))
        assertTrue(long.length <= 80)
    }
}
