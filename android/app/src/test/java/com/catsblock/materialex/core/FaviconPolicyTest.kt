package com.catsblock.materialex.core

import com.catsblock.materialex.core.favicon.FaviconPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconPolicyTest {
    @Test
    fun extractsLowercaseHostWithoutCredentialsOrPort() {
        assertEquals("example.com", FaviconPolicy.hostKey("https://User:pw@Example.com:8443/a?b#c"))
        assertEquals("example.com", FaviconPolicy.hostKey("http://example.com"))
    }

    @Test
    fun rejectsNonWebLocalAndIpHosts() {
        assertNull(FaviconPolicy.hostKey("about:blank"))
        assertNull(FaviconPolicy.hostKey("file:///x"))
        assertNull(FaviconPolicy.hostKey("https://localhost/"))
        assertNull(FaviconPolicy.hostKey("http://192.168.0.1/"))
        assertNull(FaviconPolicy.hostKey("http://[::1]/"))
        assertNull(FaviconPolicy.hostKey("http://intranet/"))
        assertNull(FaviconPolicy.hostKey(""))
    }

    @Test
    fun iconUrlIsAlwaysHttps() {
        assertEquals("https://example.com/favicon.ico", FaviconPolicy.iconUrl("example.com"))
    }

    @Test
    fun sampleSizeKeepsLargeIconsNearLimit() {
        assertEquals(1, FaviconPolicy.sampleSize(64, 64))
        assertEquals(1, FaviconPolicy.sampleSize(128, 128))
        assertEquals(2, FaviconPolicy.sampleSize(256, 256))
        assertEquals(8, FaviconPolicy.sampleSize(1024, 512))
    }
}
