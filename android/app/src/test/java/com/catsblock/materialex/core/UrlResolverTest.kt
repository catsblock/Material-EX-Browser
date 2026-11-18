package com.catsblock.materialex.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlResolverTest {
    private fun nav(url: String) = Resolution.Navigate(url)

    @Test
    fun explicitHttpsIsNavigated() {
        assertEquals(nav("https://example.com/a?b=1"), UrlResolver.resolve("https://example.com/a?b=1"))
    }

    @Test
    fun bareDomainGetsHttps() {
        assertEquals(nav("https://example.com"), UrlResolver.resolve(" example.com "))
    }

    @Test
    fun localhostAndIpUseHttp() {
        assertEquals(nav("http://localhost:3000"), UrlResolver.resolve("localhost:3000"))
        assertEquals(nav("http://192.168.0.1/admin"), UrlResolver.resolve("192.168.0.1/admin"))
    }

    @Test
    fun domainWithPortIsNavigated() {
        assertEquals(nav("https://example.com:8080/x"), UrlResolver.resolve("example.com:8080/x"))
    }

    @Test
    fun plainWordsAreSearch() {
        assertEquals(Resolution.Search("kotlin"), UrlResolver.resolve("kotlin"))
        assertEquals(Resolution.Search("best browser 2026"), UrlResolver.resolve("best browser 2026"))
    }

    @Test
    fun dangerousSchemesAreRejected() {
        assertEquals(Resolution.Rejected, UrlResolver.resolve("javascript:alert(1)"))
        assertEquals(Resolution.Rejected, UrlResolver.resolve("data:text/html,hi"))
        assertEquals(Resolution.Rejected, UrlResolver.resolve("file:///etc/passwd"))
        assertEquals(Resolution.Rejected, UrlResolver.resolve("intent://x#Intent;end"))
        assertEquals(Resolution.Rejected, UrlResolver.resolve("ftp://example.com"))
    }

    @Test
    fun credentialsInUrlAreRejected() {
        assertEquals(Resolution.Rejected, UrlResolver.resolve("https://google.com@evil.com/"))
        assertEquals(Resolution.Search("google.com@evil.com"), UrlResolver.resolve("google.com@evil.com"))
    }

    @Test
    fun blankIsRejected() {
        assertEquals(Resolution.Rejected, UrlResolver.resolve("   "))
    }

    @Test
    fun invalidPortFallsBackToSearch() {
        assertEquals(Resolution.Search("example.com:99999"), UrlResolver.resolve("example.com:99999"))
    }
}
