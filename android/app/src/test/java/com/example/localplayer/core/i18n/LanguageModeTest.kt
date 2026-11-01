package com.example.localplayer.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageModeTest {

    @Test
    fun `defaults to English when nothing is stored`() {
        assertEquals(LanguageMode.ENGLISH, LanguageMode.fromStored(null))
        assertEquals(LanguageMode.ENGLISH, LanguageMode.fromStored(""))
        assertEquals(LanguageMode.ENGLISH, LanguageMode.fromStored("klingon"))
    }

    @Test
    fun `restores a stored choice`() {
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromStored("SYSTEM"))
        assertEquals(LanguageMode.ENGLISH, LanguageMode.fromStored("ENGLISH"))
        assertEquals(LanguageMode.RUSSIAN, LanguageMode.fromStored("RUSSIAN"))
    }

    @Test
    fun `explicit languages map to resource tags`() {
        assertEquals("en", LanguageMode.ENGLISH.tag)
        assertEquals("ru", LanguageMode.RUSSIAN.tag)
        assertEquals(null, LanguageMode.SYSTEM.tag)
    }
}
