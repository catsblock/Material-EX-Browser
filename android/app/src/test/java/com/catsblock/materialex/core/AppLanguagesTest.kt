package com.catsblock.materialex.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguagesTest {
    @Test
    fun supportsTwentyOneUniqueLanguages() {
        assertEquals(21, AppLanguages.all.size)
        assertEquals(21, AppLanguages.all.map { it.tag }.toSet().size)
    }

    @Test
    fun exactRegionalMatchWins() {
        assertEquals("pt-BR", AppLanguages.resolve(listOf("pt-BR")).tag)
        assertEquals("en-GB", AppLanguages.resolve(listOf("en-GB")).tag)
    }

    @Test
    fun unknownRegionFallsBackToLanguage() {
        assertEquals("en", AppLanguages.resolve(listOf("en-US")).tag)
        assertEquals("pt", AppLanguages.resolve(listOf("pt-PT")).tag)
        assertEquals("es", AppLanguages.resolve(listOf("es-MX")).tag)
    }

    @Test
    fun unsupportedLanguageFallsBackToEnglish() {
        assertEquals("en", AppLanguages.resolve(listOf("zh-CN")).tag)
        assertEquals("en", AppLanguages.resolve(emptyList()).tag)
    }

    @Test
    fun firstSupportedDeviceLocaleIsUsed() {
        assertEquals("de", AppLanguages.resolve(listOf("zh-CN", "de-AT", "fr")).tag)
    }

    @Test
    fun onlyArabicIsRtl() {
        assertTrue(AppLanguages.isRtl("ar"))
        assertFalse(AppLanguages.isRtl("en"))
        assertNull(AppLanguages.fromTag("xx"))
    }
}
