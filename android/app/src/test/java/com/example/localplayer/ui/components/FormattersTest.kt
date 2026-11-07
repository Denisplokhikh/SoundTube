package com.example.localplayer.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun duration() {
        assertEquals("3:07", 187_000L.formatDuration())
        assertEquals("1:02:45", 3_765_000L.formatDuration())
        assertEquals("--:--", 0L.formatDuration())
    }
}
