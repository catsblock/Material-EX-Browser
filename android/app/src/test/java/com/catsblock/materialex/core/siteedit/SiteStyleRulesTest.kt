package com.catsblock.materialex.core.siteedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteStyleRulesTest {
    @Test
    fun hostIsLowercasedAndWebOnly() {
        assertEquals("example.com", SiteStyleRules.hostOf("https://Example.COM:8443/a?b#c"))
        assertNull(SiteStyleRules.hostOf("file:///sdcard/a.html"))
        assertNull(SiteStyleRules.hostOf("about:blank"))
        assertNull(SiteStyleRules.hostOf("not a url"))
    }

    @Test
    fun validCssPasses() {
        assertNull(SiteStyleRules.validate("body { color: red; } /* } */ a::after { content: \"}\"; }"))
    }

    @Test
    fun unbalancedBracesAreRejected() {
        assertEquals(CssIssue.UNBALANCED_BRACES, SiteStyleRules.validate("body { color: red;"))
        assertEquals(CssIssue.UNBALANCED_BRACES, SiteStyleRules.validate("} body { }"))
    }

    @Test
    fun importIsRejectedEvenWhenMixedCase() {
        assertEquals(CssIssue.IMPORT_NOT_ALLOWED, SiteStyleRules.validate("@ImPort url(x);"))
        assertNull(SiteStyleRules.validate("/* @import */ body { }"))
    }

    @Test
    fun oversizedCssIsRejected() {
        assertEquals(CssIssue.TOO_LARGE, SiteStyleRules.validate("a{}".repeat(SiteStyleRules.MAX_BYTES)))
    }

    @Test
    fun encodeDecodeRoundTripKeepsSpecialCharacters() {
        val styles = mapOf("a.com" to "body {\n\tcolor: red; }\\x", "b.org" to "p { margin: 0 }")
        assertEquals(styles, SiteStyleRules.decode(SiteStyleRules.encode(styles)))
        assertEquals(emptyMap<String, String>(), SiteStyleRules.decode(null))
    }

    @Test
    fun withStyleReplacesRemovesAndCaps() {
        var s = SiteStyleRules.withStyle(emptyMap(), "a.com", "a{}")
        s = SiteStyleRules.withStyle(s, "a.com", "b{}")
        assertEquals(mapOf("a.com" to "b{}"), s)
        assertEquals(emptyMap<String, String>(), SiteStyleRules.withStyle(s, "a.com", "  "))
        var many = emptyMap<String, String>()
        repeat(SiteStyleRules.MAX_SITES + 5) { many = SiteStyleRules.withStyle(many, "h$it.com", "a{}") }
        assertEquals(SiteStyleRules.MAX_SITES, many.size)
        assertNull(many["h0.com"])
    }
}
