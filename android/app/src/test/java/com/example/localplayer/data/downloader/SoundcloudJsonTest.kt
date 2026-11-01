package com.example.localplayer.data.downloader

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundcloudJsonTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses title artist duration and artwork from api batch`() {
        val body = """
            [
              {
                "id": 13158665,
                "title": "  My   Track ",
                "duration": 18109,
                "artwork_url": "https://i1.sndcdn.com/artworks-1-large.jpg",
                "user": { "username": "Artist" },
                "publisher_metadata": { "artist": "Label Artist" }
              },
              {
                "id": 2,
                "track": "Old field name",
                "duration": 1000,
                "artist": "Fallback Artist"
              }
            ]
        """.trimIndent()

        val tracks = SoundcloudJson.parse(json, body).associateBy { it.id }

        assertEquals("My Track", tracks.getValue("13158665").title)
        // Как в десктопной версии: username важнее publisher_metadata и artist.
        assertEquals("Artist", tracks.getValue("13158665").artist)
        assertEquals(18109L, tracks.getValue("13158665").durationMs)
        assertEquals("https://i1.sndcdn.com/artworks-1-large.jpg", tracks.getValue("13158665").thumbnail)
        assertEquals("Old field name", tracks.getValue("2").title)
        assertEquals("Fallback Artist", tracks.getValue("2").artist)
    }

    @Test
    fun `skips empty and broken entries instead of failing the batch`() {
        val body = """
            [
              { "id": 1, "title": "   " },
              { "id": 2, "track": "Ok" },
              { "title": "No id" },
              null
            ]
        """.trimIndent()

        assertEquals(listOf("2"), SoundcloudJson.parse(json, body).map { it.id })
    }

    @Test
    fun `invalid response does not throw`() {
        assertTrue(SoundcloudJson.parse(json, "<html>not json</html>").isEmpty())
    }
}
