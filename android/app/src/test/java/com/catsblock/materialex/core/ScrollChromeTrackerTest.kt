package com.catsblock.materialex.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollChromeTrackerTest {
    @Test
    fun startsVisible() {
        assertTrue(ScrollChromeTracker().visible)
    }

    @Test
    fun hidesAfterScrollingDownPastThreshold() {
        val tracker = ScrollChromeTracker()
        tracker.onScroll(20)
        assertTrue(tracker.onScroll(40))
        assertFalse(tracker.onScroll(100))
    }

    @Test
    fun revealsAfterScrollingUpPastThreshold() {
        val tracker = ScrollChromeTracker()
        tracker.onScroll(400)
        tracker.onScroll(600)
        assertFalse(tracker.visible)
        assertTrue(tracker.onScroll(500))
    }

    @Test
    fun smallJitterDoesNotToggle() {
        val tracker = ScrollChromeTracker()
        tracker.onScroll(20)
        tracker.onScroll(30)
        tracker.onScroll(25)
        tracker.onScroll(35)
        assertTrue(tracker.visible)
    }

    @Test
    fun reachingTopAlwaysReveals() {
        val tracker = ScrollChromeTracker()
        tracker.onScroll(100)
        tracker.onScroll(400)
        assertFalse(tracker.visible)
        assertTrue(tracker.onScroll(0))
    }

    @Test
    fun resetRestoresVisibility() {
        val tracker = ScrollChromeTracker()
        tracker.onScroll(100)
        tracker.onScroll(500)
        tracker.reset()
        assertTrue(tracker.visible)
    }
}
