package com.example.localplayer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    @Test
    fun duration() {
        assertEquals("3:07", 187_000L.formatDuration())
        assertEquals("1:02:45", 3_765_000L.formatDuration())
        assertEquals("--:--", 0L.formatDuration())
    }

    @Test
    fun eta() {
        assertEquals("~2:10", 130L.formatEta("s"))
        assertEquals("~45 s", 45L.formatEta("s"))
        assertNull(null.formatEta("s"))
        assertNull(0L.formatEta("s"))
    }
}
