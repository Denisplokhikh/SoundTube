package com.example.localplayer.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadErrorsTest {

    @Test
    fun `обрыв сети переводится на русский`() {
        val stderr = """
            WARNING: [youtube] [Errno 7] No address associated with hostname. Retrying (1/3)...
            ERROR: dQw4w9WgXcQ: Unable to download webpage: [Errno 7] No address associated with hostname
        """.trimIndent()

        assertEquals(
            "нет связи с интернетом — проверь сеть и попробуй ещё раз",
            DownloadErrors.humanize(stderr),
        )
    }

    @Test
    fun `приватный трек SoundCloud`() {
        val message = "ERROR: [soundcloud] https://soundcloud.com/x/y: This track is not available"

        assertEquals(
            "трек недоступен — возможно, он удалён или скрыт",
            DownloadErrors.humanize(message),
        )
    }

    @Test
    fun `неизвестная ошибка — последняя строка без WARNING`() {
        val stderr = """
            WARNING: что-то пошло не так
            ERROR: [soundcloud] extractor сломался
        """.trimIndent()

        assertEquals("[soundcloud] extractor сломался", DownloadErrors.humanize(stderr))
    }

    @Test
    fun `пустая ошибка не даёт пустую строку`() {
        assertEquals("не удалось скачать трек", DownloadErrors.humanize(null))
        assertEquals("не удалось скачать трек", DownloadErrors.humanize("   "))
    }

    @Test
    fun `сетевые ошибки распознаются, логические — нет`() {
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
