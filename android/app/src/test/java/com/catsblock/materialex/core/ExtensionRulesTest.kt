package com.catsblock.materialex.core

import com.catsblock.materialex.core.extension.ExtensionInfo
import com.catsblock.materialex.core.extension.ExtensionPrompt
import com.catsblock.materialex.core.extension.ExtensionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRulesTest {
    @Test
    fun onlyHttpsXpiAddressesAreAccepted() {
        assertEquals(
            "https://addons.mozilla.org/firefox/downloads/file/1/ext.xpi",
            ExtensionRules.normalizeInstallUrl(" https://addons.mozilla.org/firefox/downloads/file/1/ext.xpi ")
        )
        assertEquals("https://example.com/a.XPI?x=1", ExtensionRules.normalizeInstallUrl("https://example.com/a.XPI?x=1"))
        assertNull(ExtensionRules.normalizeInstallUrl("http://example.com/a.xpi"))
        assertNull(ExtensionRules.normalizeInstallUrl("file:///sdcard/a.xpi"))
        assertNull(ExtensionRules.normalizeInstallUrl("https://example.com/page.html"))
        assertNull(ExtensionRules.normalizeInstallUrl("https://localhost/a.xpi"))
        assertNull(ExtensionRules.normalizeInstallUrl("https://exa mple.com/a.xpi"))
        assertNull(ExtensionRules.normalizeInstallUrl(""))
    }

    @Test
    fun broadHostAccessIsDetected() {
        assertTrue(ExtensionRules.hasBroadAccess(listOf("<all_urls>")))
        assertTrue(ExtensionRules.hasBroadAccess(listOf("https://a.example/*", "*://*/*")))
        assertFalse(ExtensionRules.hasBroadAccess(listOf("https://a.example/*")))
        assertTrue(ExtensionPrompt("x", emptyList(), listOf("https://*/*")).broadAccess)
    }

    @Test
    fun enabledExtensionsSortFirstThenByName() {
        val list = listOf(
            ExtensionInfo("1", "Zeta", "1", true),
            ExtensionInfo("2", "alpha", "1", false),
            ExtensionInfo("3", "Beta", "1", true)
        )
        assertEquals(listOf("Beta", "Zeta", "alpha"), ExtensionRules.sortedForDisplay(list).map { it.name })
    }
}
