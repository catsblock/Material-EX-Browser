package com.catsblock.materialex.core

import com.catsblock.materialex.core.memory.MemoryBudget
import com.catsblock.materialex.core.memory.MemoryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryBudgetTest {
    @Test
    fun automaticScalesWithDeviceRam() {
        assertEquals(3, MemoryBudget.loadedLimit(MemoryMode.AUTOMATIC, 5, 2048))
        assertEquals(5, MemoryBudget.loadedLimit(MemoryMode.AUTOMATIC, 5, 4096))
        assertEquals(8, MemoryBudget.loadedLimit(MemoryMode.AUTOMATIC, 5, 8192))
        assertEquals(12, MemoryBudget.loadedLimit(MemoryMode.AUTOMATIC, 5, 16384))
        assertEquals(5, MemoryBudget.loadedLimit(MemoryMode.AUTOMATIC, 5, 0))
    }

    @Test
    fun fixedModesAndCustomRange() {
        assertEquals(2, MemoryBudget.loadedLimit(MemoryMode.LOW, 5, 2048))
        assertEquals(3, MemoryBudget.loadedLimit(MemoryMode.LOW, 5, 8192))
        assertEquals(5, MemoryBudget.loadedLimit(MemoryMode.BALANCED, 20, 8192))
        assertEquals(10, MemoryBudget.loadedLimit(MemoryMode.HIGH, 5, 8192))
        assertEquals(1, MemoryBudget.loadedLimit(MemoryMode.CUSTOM, 0, 8192))
        assertEquals(30, MemoryBudget.loadedLimit(MemoryMode.CUSTOM, 99, 8192))
        assertEquals(17, MemoryBudget.loadedLimit(MemoryMode.CUSTOM, 17, 8192))
    }

    @Test
    fun pressureNeverRaisesTheConfiguredLimit() {
        assertNull(MemoryBudget.pressureLimit(5))
        assertNull(MemoryBudget.pressureLimit(20))
        assertNull(MemoryBudget.pressureLimit(40))
        assertEquals(2, MemoryBudget.pressureLimit(10))
        assertEquals(1, MemoryBudget.pressureLimit(15))
        assertEquals(2, MemoryBudget.pressureLimit(60))
        assertEquals(1, MemoryBudget.pressureLimit(80))
        assertEquals(1, MemoryBudget.effectiveLimit(1, 10))
        assertEquals(2, MemoryBudget.effectiveLimit(8, 10))
        assertEquals(8, MemoryBudget.effectiveLimit(8, 20))
    }

    @Test
    fun unknownModeFallsBackToAutomatic() {
        assertEquals(MemoryMode.AUTOMATIC, MemoryMode.fromId("nope"))
        assertEquals(MemoryMode.HIGH, MemoryMode.fromId("HIGH"))
    }
}
