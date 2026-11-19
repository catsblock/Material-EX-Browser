package com.catsblock.materialex.core

import com.catsblock.materialex.core.security.BypassGate
import com.catsblock.materialex.core.security.ThreatInfo
import com.catsblock.materialex.core.security.ThreatKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreatStateTest {
    @Test
    fun bypassIsScopedToOneTabAndEndsOnce() {
        val gate = BypassGate()
        assertFalse(gate.isActive())
        gate.begin("tab1", "https://bad.example/x")
        assertTrue(gate.isActiveFor("tab1"))
        assertFalse(gate.isActiveFor("tab2"))
        assertEquals("https://bad.example/x", gate.targetUrl())
        assertFalse(gate.endIfFor("tab2"))
        assertTrue(gate.isActive())
        assertTrue(gate.endIfFor("tab1"))
        assertFalse(gate.isActive())
        assertNull(gate.targetUrl())
        assertFalse(gate.end())
    }

    @Test
    fun hostIsExtractedFromUrl() {
        val info = ThreatInfo("t", "https://bad.example:8080/path?q=1", ThreatKind.PHISHING, 1, "X", 0L)
        assertEquals("bad.example:8080", info.host)
        assertEquals("bad.example", ThreatInfo("t", "http://bad.example", ThreatKind.MALWARE, 1, "X", 0L).host)
    }
}
