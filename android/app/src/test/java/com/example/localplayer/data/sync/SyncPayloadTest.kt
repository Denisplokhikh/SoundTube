package com.example.localplayer.data.sync

import com.example.localplayer.domain.util.TrackIds
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Контракт с десктопным экспортом: ссылки и плейлисты, без идентификаторов. */
class SyncPayloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses desktop export and derives track ids from urls`() {
        val text = """
            {
              "version": 1,
              "exportedAt": "2026-11-02T12:00:00+00:00",
              "tracks": [
                {"url": "https://youtu.be/dQw4w9WgXcQ", "title": "One", "artist": "Artist", "platform": "youtube"},
                {"url": "https://soundcloud.com/artist/two", "title": "Two", "artist": "Artist", "platform": "soundcloud"}
              ],
              "playlists": [
                {"name": "Любимое", "tracks": ["https://youtu.be/dQw4w9WgXcQ"]}
              ]
            }
        """.trimIndent()

        val payload = json.decodeFromString<SyncPayload>(text)

        assertEquals(1, payload.version)
        assertEquals(2, payload.tracks.size)
        assertEquals("yt:dQw4w9WgXcQ", TrackIds.forUrl(payload.tracks[0].url))
        assertEquals("sc:artist/two", TrackIds.forUrl(payload.tracks[1].url))
        assertEquals("Любимое", payload.playlists.single().name)
    }

    @Test
    fun `optional fields have defaults`() {
        val payload = json.decodeFromString<SyncPayload>(
            """{"version":1,"tracks":[{"url":"https://youtu.be/dQw4w9WgXcQ"}]}""",
        )

        assertEquals("", payload.tracks.single().title)
        assertEquals("", payload.tracks.single().artist)
        assertEquals(null, payload.tracks.single().platform)
        assertEquals(emptyList<SyncPlaylist>(), payload.playlists)
    }
}
