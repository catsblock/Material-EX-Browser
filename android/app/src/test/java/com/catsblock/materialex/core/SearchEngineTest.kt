package com.catsblock.materialex.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    @Test
    fun defaultEngineIsGoogle() {
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromId(null))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromId("unknown"))
    }

    @Test
    fun builtInTemplatesAreValid() {
        SearchEngine.entries.mapNotNull { it.template }.forEach { assertTrue(it, SearchTemplates.isValid(it)) }
    }

    @Test
    fun queryIsEncoded() {
        assertEquals(
            "https://www.google.com/search?q=a+b%26c",
            SearchTemplates.resolveUrl(SearchEngine.GOOGLE, null, "a b&c")
        )
    }

    @Test
    fun customTemplateValidation() {
        assertTrue(SearchTemplates.isValid("https://example.com/search?q=%s"))
        assertFalse(SearchTemplates.isValid("http://example.com/search?q=%s"))
        assertFalse(SearchTemplates.isValid("https://example.com/search"))
        assertFalse(SearchTemplates.isValid("https://example.com/%s/%s"))
        assertFalse(SearchTemplates.isValid("https://%s.example.com/"))
        assertFalse(SearchTemplates.isValid("https://user@example.com/?q=%s"))
        assertFalse(SearchTemplates.isValid("https://example.com/?q=%s evil"))
    }

    @Test
    fun invalidCustomTemplateYieldsNoUrl() {
        assertNull(SearchTemplates.resolveUrl(SearchEngine.CUSTOM, "nope", "x"))
        assertEquals(
            "https://example.com/?q=x",
            SearchTemplates.resolveUrl(SearchEngine.CUSTOM, "https://example.com/?q=%s", "x")
        )
    }
}
