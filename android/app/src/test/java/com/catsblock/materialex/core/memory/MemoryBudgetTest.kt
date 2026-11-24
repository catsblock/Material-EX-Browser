package com.catsblock.materialex.core.memory

import com.catsblock.materialex.core.tabs.TabLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryBudgetTest {
    @Test
    fun automaticModeScalesWithInstalledRam() {
        assertEquals(3, MemoryBudget.loadedLimit(MemoryMode.AUTOMATIC, 0, 2048))
        assertEquals(5, MemoryBudget.loadedLimit(MemoryMode.AUTOMATIC, 0, 4096))
        assertEquals(8, MemoryBudget.loadedLimit(MemoryMode.AUTOMATIC, 0, 8192))
        assertEquals(12, MemoryBudget.loadedLimit(MemoryMode.AUTOMATIC, 0, 16384))
        assertEquals(TabLifecycle.DEFAULT_LOADED, MemoryBudget.loadedLimit(MemoryMode.AUTOMATIC, 0, 0))
    }

    @Test
    fun customLimitIsClamped() {
        assertEquals(TabLifecycle.MIN_LOADED, MemoryBudget.loadedLimit(MemoryMode.CUSTOM, -5, 4096))
        assertEquals(TabLifecycle.MAX_LOADED, MemoryBudget.loadedLimit(MemoryMode.CUSTOM, 999, 4096))
    }

    @Test
    fun lowModeIsTighterOnSmallDevices() {
        assertEquals(2, MemoryBudget.loadedLimit(MemoryMode.LOW, 0, 2048))
        assertEquals(3, MemoryBudget.loadedLimit(MemoryMode.LOW, 0, 8192))
    }

    @Test
    fun memoryPressureOnlyEverTightensTheLimit() {
        assertNull(MemoryBudget.pressureLimit(5))
        assertEquals(2, MemoryBudget.pressureLimit(12))
        assertEquals(1, MemoryBudget.pressureLimit(80))
        assertEquals(4, MemoryBudget.effectiveLimit(4, 5))
        assertEquals(1, MemoryBudget.effectiveLimit(4, 15))
        assertEquals(1, MemoryBudget.effectiveLimit(1, 12))
    }

    @Test
    fun audibleAndOpenerTabsAreProtected() {
        assertEquals(setOf("a", "b"), MemoryBudget.protectedIds(setOf("a"), setOf("b")))
        assertEquals(MemoryMode.AUTOMATIC, MemoryMode.fromId("bogus"))
        assertEquals(MemoryMode.HIGH, MemoryMode.fromId("HIGH"))
    }
}
