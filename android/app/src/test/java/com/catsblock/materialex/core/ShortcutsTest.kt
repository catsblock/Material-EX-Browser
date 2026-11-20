package com.catsblock.materialex.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutsTest {
    @Test
    fun controlKeysMapToBrowserActions() {
        assertEquals(ShortcutAction.NEW_TAB, Shortcuts.resolve(KeyEvent.KEYCODE_T, true, false, false))
        assertEquals(ShortcutAction.RESTORE_TAB, Shortcuts.resolve(KeyEvent.KEYCODE_T, true, true, false))
        assertEquals(ShortcutAction.CLOSE_TAB, Shortcuts.resolve(KeyEvent.KEYCODE_W, true, false, false))
        assertEquals(ShortcutAction.FOCUS_ADDRESS, Shortcuts.resolve(KeyEvent.KEYCODE_L, true, false, false))
        assertEquals(ShortcutAction.RELOAD, Shortcuts.resolve(KeyEvent.KEYCODE_R, true, false, false))
        assertEquals(ShortcutAction.BOOKMARK, Shortcuts.resolve(KeyEvent.KEYCODE_D, true, false, false))
        assertEquals(ShortcutAction.HISTORY, Shortcuts.resolve(KeyEvent.KEYCODE_H, true, false, false))
        assertEquals(ShortcutAction.DOWNLOADS, Shortcuts.resolve(KeyEvent.KEYCODE_J, true, false, false))
        assertEquals(ShortcutAction.FIND, Shortcuts.resolve(KeyEvent.KEYCODE_F, true, false, false))
    }

    @Test
    fun tabCyclingAndReordering() {
        assertEquals(ShortcutAction.NEXT_TAB, Shortcuts.resolve(KeyEvent.KEYCODE_TAB, true, false, false))
        assertEquals(ShortcutAction.PREVIOUS_TAB, Shortcuts.resolve(KeyEvent.KEYCODE_TAB, true, true, false))
        assertEquals(ShortcutAction.MOVE_TAB_LEFT, Shortcuts.resolve(KeyEvent.KEYCODE_PAGE_UP, true, true, false))
        assertEquals(ShortcutAction.MOVE_TAB_RIGHT, Shortcuts.resolve(KeyEvent.KEYCODE_PAGE_DOWN, true, true, false))
    }

    @Test
    fun altArrowsNavigateHistory() {
        assertEquals(ShortcutAction.BACK, Shortcuts.resolve(KeyEvent.KEYCODE_DPAD_LEFT, false, false, true))
        assertEquals(ShortcutAction.FORWARD, Shortcuts.resolve(KeyEvent.KEYCODE_DPAD_RIGHT, false, false, true))
    }

    @Test
    fun plainKeysAreNotIntercepted() {
        assertNull(Shortcuts.resolve(KeyEvent.KEYCODE_T, false, false, false))
        assertNull(Shortcuts.resolve(KeyEvent.KEYCODE_L, false, true, false))
        assertEquals(ShortcutAction.RELOAD, Shortcuts.resolve(KeyEvent.KEYCODE_F5, false, false, false))
    }
}
