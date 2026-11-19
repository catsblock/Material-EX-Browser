package com.catsblock.materialex.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    @Test
    fun requiresThreeCharacters() {
        assertFalse(SuggestionPolicy.shouldRequest("ab", SuggestionProvider.DUCKDUCKGO, false))
        assertFalse(SuggestionPolicy.shouldRequest("  ab ", SuggestionProvider.DUCKDUCKGO, false))
        assertTrue(SuggestionPolicy.shouldRequest("abc", SuggestionProvider.DUCKDUCKGO, false))
    }

    @Test
    fun neverRequestsInPrivateOrWhenOff() {
        assertFalse(SuggestionPolicy.shouldRequest("android", SuggestionProvider.DUCKDUCKGO, true))
        assertFalse(SuggestionPolicy.shouldRequest("android", SuggestionProvider.NONE, false))
    }

    @Test
    fun defaultProviderIsDuckDuckGo() {
        assertEquals(SuggestionProvider.DUCKDUCKGO, SuggestionProvider.fromId(null))
        assertEquals(SuggestionProvider.DUCKDUCKGO, SuggestionProvider.fromId("unknown"))
        assertEquals(SuggestionProvider.BRAVE, SuggestionProvider.fromId("brave"))
    }

    @Test
    fun encodesQueryInUrl() {
        val url = SuggestionProvider.GOOGLE.urlFor("a b&c")
        assertNotNull(url)
        assertTrue(url!!.endsWith("q=a+b%26c"))
        assertNull(SuggestionProvider.NONE.urlFor("abc"))
    }

    @Test
    fun parsesOpenSearchResponse() {
        assertEquals(listOf("android", "android studio"), SuggestionParser.parse("[\"andr\",[\"android\",\"android studio\"]]"))
    }

    @Test
    fun parsesObjectResponseAndEscapes() {
        assertEquals(listOf("caf\u00e9", "a\"b"), SuggestionParser.parse("[{\"phrase\":\"caf\\u00e9\"},{\"t\":\"a\\\"b\"}]"))
    }

    @Test
    fun rejectsMalformedAndOversizedBodies() {
        assertTrue(SuggestionParser.parse("not json").isEmpty())
        assertTrue(SuggestionParser.parse("[\"only query\"]").isEmpty())
        assertTrue(SuggestionParser.parse("x".repeat(70_000)).isEmpty())
    }

    @Test
    fun deduplicatesAndLimits() {
        val items = (1..20).joinToString(",") { "\"s${it % 12}\"" }
        val parsed = SuggestionParser.parse("[\"q\",[$items]]")
        assertEquals(SuggestionPolicy.MAX_RESULTS, parsed.size)
        assertEquals(parsed.distinct(), parsed)
    }
}
