package com.catsblock.materialex.core.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionStoreTest {
    private val sample = """
        {"count": 2, "next": "https://addons.mozilla.org/api/v5/addons/search/?page=2", "results": [
          {"guid": "uBlock0@raymondhill.net", "name": "uBlock Origin", "summary": "Finally, an efficient blocker.",
           "average_daily_users": 8000000, "ratings": {"average": 4.7},
           "current_version": {"file": {"url": "https://addons.mozilla.org/firefox/downloads/file/1/ublock.xpi"}}},
          {"guid": "bad@example", "name": {"en-US": "Evil"}, "summary": {"en-US": "x"},
           "current_version": {"file": {"url": "https://evil.example/e.xpi"}}},
          {"guid": "noname@x", "name": "", "current_version": {"file": {"url": "https://addons.mozilla.org/a.xpi"}}},
          {"guid": "local@x", "name": {"en-US": "Localized"}, "summary": {"en-US": "Sum"}, "average_daily_users": 5,
           "ratings": {"average": 0.0},
           "current_version": {"file": {"url": "https://addons.cdn.mozilla.net/user-media/addons/2/l.xpi"}}}
        ]}
    """.trimIndent()

    @Test
    fun parsesTrustedEntriesAndSkipsOthers() {
        val page = ExtensionStore.parse(sample)
        assertEquals(listOf("uBlock0@raymondhill.net", "local@x"), page.items.map { it.guid })
        assertTrue(page.hasMore)
        assertEquals(4.7, page.items[0].rating!!, 0.001)
        assertEquals(8_000_000L, page.items[0].users)
        assertEquals("Localized", page.items[1].name)
        assertNull(page.items[1].rating)
    }

    @Test
    fun garbageProducesAnEmptyPage() {
        assertTrue(ExtensionStore.parse("not json").items.isEmpty())
        assertFalse(ExtensionStore.parse("{}").hasMore)
    }

    @Test
    fun onlyMozillaHostsAreTrusted() {
        assertTrue(ExtensionStore.isTrustedInstallUrl("https://addons.mozilla.org/firefox/downloads/file/1/a.xpi"))
        assertFalse(ExtensionStore.isTrustedInstallUrl("http://addons.mozilla.org/a.xpi"))
        assertFalse(ExtensionStore.isTrustedInstallUrl("https://addons.mozilla.org.evil.com/a.xpi"))
        assertFalse(ExtensionStore.isTrustedInstallUrl("https://addons.mozilla.org@evil.com/a.xpi"))
        assertFalse(ExtensionStore.isTrustedInstallUrl("https://addons.mozilla.org/a.zip"))
    }

    @Test
    fun searchUrlEncodesAndBoundsTheQuery() {
        assertTrue(ExtensionStore.searchUrl("dark mode&x=1", 2).contains("q=dark+mode%26x%3D1"))
        assertTrue(ExtensionStore.searchUrl("", 0).contains("page=1"))
        assertFalse(ExtensionStore.searchUrl("", 1).contains("&q="))
        assertTrue(ExtensionStore.searchUrl("a".repeat(500), 1).length < 400)
    }
}
