package com.catsblock.materialex.core

import com.catsblock.materialex.core.popup.PopupPolicy
import com.catsblock.materialex.core.popup.PopupWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupPolicyTest {
    @Test
    fun blockedWithoutRecentGesture() {
        assertFalse(PopupPolicy.allow(true, 0, 0L, 10_000L))
        assertFalse(PopupPolicy.allow(true, 0, 1_000L, 10_000L))
    }

    @Test
    fun allowedWithinGestureWindow() {
        assertTrue(PopupPolicy.allow(true, 0, 9_000L, 10_000L))
    }

    @Test
    fun gestureInTheFutureIsRejected() {
        assertFalse(PopupPolicy.allow(true, 0, 11_000L, 10_000L))
    }

    @Test
    fun disabledBlockingAllowsWithoutGesture() {
        assertTrue(PopupPolicy.allow(false, 0, 0L, 10_000L))
    }

    @Test
    fun openLimitAppliesAlways() {
        assertFalse(PopupPolicy.allow(false, PopupPolicy.MAX_OPEN, 9_999L, 10_000L))
    }

    @Test
    fun foregroundIsLatestNonMinimizedForOpener() {
        val popups = listOf(
            PopupWindow("a", "tab1"),
            PopupWindow("b", "tab1", minimized = true),
            PopupWindow("c", "tab2"),
            PopupWindow("d", "tab1")
        )
        assertEquals("d", PopupPolicy.foreground(popups, "tab1")?.id)
        assertEquals("c", PopupPolicy.foreground(popups, "tab2")?.id)
        assertNull(PopupPolicy.foreground(popups, "tab3"))
        assertEquals(listOf("b"), PopupPolicy.minimized(popups, "tab1").map { it.id })
    }

    @Test
    fun onlyWebAndBlankSchemesAreAllowed() {
        assertTrue(PopupPolicy.schemeAllowed("https://example.com/auth"))
        assertTrue(PopupPolicy.schemeAllowed("http://example.com"))
        assertTrue(PopupPolicy.schemeAllowed("about:blank"))
        assertTrue(PopupPolicy.schemeAllowed(""))
        assertFalse(PopupPolicy.schemeAllowed("javascript:alert(1)"))
        assertFalse(PopupPolicy.schemeAllowed("file:///data/data/app/secret"))
        assertFalse(PopupPolicy.schemeAllowed("intent://x#Intent;end"))
        assertFalse(PopupPolicy.schemeAllowed("data:text/html,hi"))
    }
}
