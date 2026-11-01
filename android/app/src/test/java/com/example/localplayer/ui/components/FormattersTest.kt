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
        assertEquals("~2:10", 130L.formatEta())
        assertEquals("~45 с", 45L.formatEta())
        assertNull(null.formatEta())
        assertNull(0L.formatEta())
    }

    @Test
    fun tracksWord() {
        assertEquals("треков", 0.tracksWord())
        assertEquals("трек", 1.tracksWord())
        assertEquals("трека", 2.tracksWord())
        assertEquals("треков", 5.tracksWord())
        assertEquals("треков", 11.tracksWord())
        assertEquals("трек", 21.tracksWord())
        assertEquals("трека", 22.tracksWord())
        assertEquals("треков", 111.tracksWord())
    }

    @Test
    fun playlistsWord() {
        assertEquals("плейлистов", 0.playlistsWord())
        assertEquals("плейлист", 1.playlistsWord())
        assertEquals("плейлиста", 3.playlistsWord())
        assertEquals("плейлистов", 12.playlistsWord())
        assertEquals("плейлист", 31.playlistsWord())
    }
}
