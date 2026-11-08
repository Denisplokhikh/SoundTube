package com.example.localplayer.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsQueryTest {

    @Test
    fun `noise brackets are stripped but meaningful ones are kept`() {
        assertEquals("Song", LyricsQuery.stripNoise("Song (Official Music Video)"))
        assertEquals("Song", LyricsQuery.stripNoise("Song [4K Remaster]"))
        assertEquals("Song (Live)", LyricsQuery.stripNoise("Song (Live)"))
        assertEquals("Song (Remix)", LyricsQuery.stripNoise("Song (Remix)"))
    }

    @Test
    fun `artist title split takes the parsed pair first`() {
        val variants = LyricsQuery.buildQueryVariants("Artist - Song (Official Video)", "ChannelName")

        assertEquals("Song" to "Artist", variants.first())
    }

    @Test
    fun `junk artist from the channel tag is dropped`() {
        val variants = LyricsQuery.buildQueryVariants("Song", "Unknown Artist")

        assertTrue(variants.all { it.second == null })
    }

    @Test
    fun `feat suffix is removed from queries`() {
        val variants = LyricsQuery.buildQueryVariants("Song feat. Somebody", "Artist")

        assertEquals("Song" to "Artist", variants.first())
    }

    @Test
    fun `trailing plus part is trimmed as a separate variant`() {
        val variants = LyricsQuery.buildQueryVariants("SMARTCHAIN +ARLEKIN 40 000", null)

        // РџРѕР»РЅРѕРµ РЅР°Р·РІР°РЅРёРµ РѕСЃС‚Р°С‘С‚СЃСЏ РїРµСЂРІС‹Рј, РѕР±СЂРµР·Р°РЅРЅРѕРµ РґРѕР±Р°РІР»СЏРµС‚СЃСЏ СЃР»РµРґРѕРј.
        assertEquals("SMARTCHAIN", variants[1].first)
    }

    @Test
    fun `variants are limited to five`() {
        val variants = LyricsQuery.buildQueryVariants(
            "Artist - Song (Prod. by Someone) feat. Somebody +More",
            "Channel",
        )

        assertTrue(variants.isNotEmpty())
        assertTrue(variants.size <= 5)
    }

    private fun candidate(
        artist: String? = null,
        title: String? = null,
        duration: Double? = null,
        synced: String? = null,
        plain: String? = null,
        instrumental: Boolean = false,
    ) = LyricsQuery.Candidate(
        trackName = title,
        artistName = artist,
        durationSec = duration,
        syncedLyrics = synced,
        plainLyrics = plain,
        instrumental = instrumental,
    )

    @Test
    fun `candidate with foreign artist and different duration is rejected`() {
        val pool = listOf(
            candidate(artist = "Someone Else", title = "Song", duration = 200.0, plain = "text"),
        )

        assertNull(
            LyricsQuery.pickBestCandidate(
                pool,
                targetDurationSec = 180.0,
                targetArtist = "Artist",
                targetTitle = "Song",
            ),
        )
    }

    @Test
    fun `synced lyrics win over plain ones`() {
        val pool = listOf(
            candidate(artist = "Artist", title = "Song", duration = 180.0, plain = "plain"),
            candidate(artist = "Artist", title = "Song", duration = 181.0, synced = "[00:01.00]a"),
        )

        val best = LyricsQuery.pickBestCandidate(
            pool,
            targetDurationSec = 180.0,
            targetArtist = "Artist",
            targetTitle = "Song",
        )

        assertEquals("[00:01.00]a", best?.syncedLyrics)
    }

    @Test
    fun `exact title with close duration passes without artist match`() {
        val item = candidate(artist = null, title = "Song", duration = 181.0, plain = "text")

        assertTrue(LyricsQuery.isPlausible(item, "Song", "Artist", 180.0))
        assertFalse(LyricsQuery.isPlausible(item, "Song", "Artist", 120.0))
    }

    @Test
    fun `instrumental candidate is plausible and has lyrics flag`() {
        val item = candidate(artist = "Artist", instrumental = true)

        assertTrue(item.hasLyrics)
        assertEquals(
            item,
            LyricsQuery.pickBestCandidate(
                listOf(item),
                targetDurationSec = null,
                targetArtist = "Artist",
                targetTitle = "Song",
            ),
        )
    }
}
