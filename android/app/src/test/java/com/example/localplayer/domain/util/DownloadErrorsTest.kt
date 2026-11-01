package com.example.localplayer.domain.util

import com.example.localplayer.domain.util.DownloadErrors.Cause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorsTest {

    @Test
    fun `network failure is recognised`() {
        val stderr = """
            WARNING: [youtube] [Errno 7] No address associated with hostname. Retrying (1/3)...
            ERROR: dQw4w9WgXcQ: Unable to download webpage: [Errno 7] No address associated with hostname
        """.trimIndent()

        assertEquals(Cause.NETWORK, DownloadErrors.classify(stderr))
    }

    @Test
    fun `private SoundCloud track`() {
        val message = "ERROR: [soundcloud] https://soundcloud.com/x/y: This track is not available"

        assertEquals(Cause.UNAVAILABLE, DownloadErrors.classify(message))
    }

    @Test
    fun `unknown error keeps the last line without WARNING`() {
        val stderr = """
            WARNING: something went wrong
            ERROR: [soundcloud] extractor broke
        """.trimIndent()

        assertNull(DownloadErrors.classify(stderr))
        assertEquals("[soundcloud] extractor broke", DownloadErrors.detail(stderr))
    }

    @Test
    fun `empty error gives no detail`() {
        assertNull(DownloadErrors.classify(null))
        assertEquals("", DownloadErrors.detail(null))
        assertEquals("", DownloadErrors.detail("   "))
    }

    @Test
    fun `network errors are detected, logical ones are not`() {
        assertTrue(DownloadErrors.isNetworkError("unable to resolve host soundcloud.com"))
        assertTrue(DownloadErrors.isNetworkError("[Errno 103] Software caused connection abort"))
        assertTrue(
            DownloadErrors.isNetworkError(
                "ERROR: [generic] abc: Unable to download webpage: Remote end closed connection " +
                    "without response (caused by TransportError('Remote end closed connection'))",
            ),
        )

        assertFalse(DownloadErrors.isNetworkError("ERROR: Unsupported URL: https://example.com"))
        assertFalse(DownloadErrors.isNetworkError(null))
    }
}
