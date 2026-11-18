package com.catsblock.materialex.core.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabManagerTest {
    private var ids = 0
    private var clock = 0L
    private fun manager() = TabManager(newId = { "t${++ids}" }, now = { ++clock })

    @Test
    fun openSelectsNewTab() {
        val m = manager()
        val a = m.open("https://a.com", "default", false)
        val b = m.open("https://b.com", "default", false)
        assertEquals(b.id, m.state.value.activeId)
        assertEquals(listOf(a.id, b.id), m.state.value.tabs.map { it.id })
    }

    @Test
    fun backgroundOpenKeepsActive() {
        val m = manager()
        val a = m.open("https://a.com", "default", false)
        m.open("https://b.com", "default", false, select = false)
        assertEquals(a.id, m.state.value.activeId)
    }

    @Test
    fun closingActiveSelectsRightNeighbourThenLeft() {
        val m = manager()
        val a = m.open("a", "default", false)
        val b = m.open("b", "default", false)
        val c = m.open("c", "default", false)
        m.select(b.id)
        m.close(b.id)
        assertEquals(c.id, m.state.value.activeId)
        m.close(c.id)
        assertEquals(a.id, m.state.value.activeId)
        m.close(a.id)
        assertNull(m.state.value.activeId)
        assertTrue(m.state.value.tabs.isEmpty())
    }

    @Test
    fun closingInactiveKeepsActive() {
        val m = manager()
        val a = m.open("a", "default", false)
        val b = m.open("b", "default", false)
        m.close(a.id)
        assertEquals(b.id, m.state.value.activeId)
    }

    @Test
    fun restoreReopensMostRecentlyClosed() {
        val m = manager()
        m.open("a", "default", false)
        val b = m.open("b", "default", false)
        m.close(b.id)
        val restored = m.restoreLastClosed()
        assertEquals("b", restored?.url)
        assertEquals(restored?.id, m.state.value.activeId)
        assertNull(m.restoreLastClosed())
    }

    @Test
    fun privateTabsAreNeverRemembered() {
        val m = manager()
        m.open("a", "default", false)
        val p = m.open("secret", "default", true)
        m.close(p.id)
        assertTrue(m.state.value.recentlyClosed.isEmpty())
        assertNull(m.restoreLastClosed())
    }

    @Test
    fun duplicateInsertsAfterSourceAndSelects() {
        val m = manager()
        val a = m.open("a", "default", false)
        val b = m.open("b", "default", false)
        val copy = m.duplicate(a.id)
        assertEquals(listOf(a.id, copy?.id, b.id), m.state.value.tabs.map { it.id })
        assertEquals(copy?.id, m.state.value.activeId)
    }

    @Test
    fun closeOthersKeepsOnlyChosen() {
        val m = manager()
        m.open("a", "default", false)
        val b = m.open("b", "default", false)
        m.open("c", "default", false)
        m.closeOthers(b.id)
        assertEquals(listOf(b.id), m.state.value.tabs.map { it.id })
        assertEquals(b.id, m.state.value.activeId)
        assertEquals(2, m.state.value.recentlyClosed.size)
    }

    @Test
    fun moveReorders() {
        val m = manager()
        val a = m.open("a", "default", false)
        val b = m.open("b", "default", false)
        val c = m.open("c", "default", false)
        m.move(a.id, 2)
        assertEquals(listOf(b.id, c.id, a.id), m.state.value.tabs.map { it.id })
    }

    @Test
    fun updateChangesUrlAndTitle() {
        val m = manager()
        val a = m.open("a", "default", false)
        m.update(a.id, url = "https://x.com", title = "X")
        assertEquals("https://x.com", m.state.value.tabs.single().url)
        assertEquals("X", m.state.value.tabs.single().title)
    }

    @Test
    fun loadedSetAlwaysContainsActiveAndRespectsLimit() {
        val m = manager()
        val tabs = (1..8).map { m.open("u$it", "default", false) }
        m.select(tabs[0].id)
        val loaded = TabLifecycle.loadedSet(m.state.value, 3)
        assertEquals(3, loaded.size)
        assertTrue(tabs[0].id in loaded)
        assertTrue(tabs[7].id in loaded)
        assertTrue(tabs[6].id in loaded)
        assertFalse(tabs[1].id in loaded)
    }

    @Test
    fun loadedLimitIsClamped() {
        val m = manager()
        repeat(40) { m.open("u$it", "default", false) }
        assertEquals(1, TabLifecycle.loadedSet(m.state.value, 0).size)
        assertEquals(30, TabLifecycle.loadedSet(m.state.value, 99).size)
    }
}
