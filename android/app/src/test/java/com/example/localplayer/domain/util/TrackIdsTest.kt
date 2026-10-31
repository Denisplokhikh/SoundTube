package com.example.localplayer.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackIdsTest {

    @Test
    fun `youtube watch url maps to video id`() {
        assertEquals(
            "yt:dQw4w9WgXcQ",
            TrackIds.forUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ"),
        )
    }

    @Test
    fun `youtube short and music urls map to the same id`() {
        val expected = "yt:dQw4w9WgXcQ"
        assertEquals(expected, TrackIds.forUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(expected, TrackIds.forUrl("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(expected, TrackIds.forUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test
    fun `soundcloud urls use artist and slug`() {
        assertEquals(
            "sc:artist/track-name",
            TrackIds.forUrl("https://soundcloud.com/Artist/Track-Name?si=abc123"),
        )
        assertEquals(
            "sc:artist/track-name",
            TrackIds.forUrl("https://m.soundcloud.com/artist/track-name/"),
        )
    }

    @Test
    fun `unknown hosts fall back to canonical url hash`() {
        val first = TrackIds.forUrl("https://example.com/a?utm_source=x")
        val second = TrackIds.forUrl("https://www.example.com/a/")
        assertTrue(first.startsWith("sha256:"))
        assertEquals(first, second)
    }

    @Test
    fun `canonicalize drops tracking parameters and trailing slash`() {
        assertEquals(
            "https://example.com/audio?id=7",
            TrackIds.canonicalize("https://www.Example.com/audio/?id=7&utm_medium=feed&si=zzz"),
        )
    }

    @Test
    fun `short soundcloud links are not mistaken for slugs`() {
        assertTrue(TrackIds.forUrl("https://on.soundcloud.com/abcdEF").startsWith("sha256:"))
    }
}
