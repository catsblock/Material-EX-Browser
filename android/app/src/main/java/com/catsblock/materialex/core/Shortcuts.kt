package com.catsblock.materialex.core

import android.view.KeyEvent

enum class ShortcutAction {
    NEW_TAB,
    CLOSE_TAB,
    RESTORE_TAB,
    FOCUS_ADDRESS,
    RELOAD,
    BOOKMARK,
    HISTORY,
    DOWNLOADS,
    FIND,
    NEXT_TAB,
    PREVIOUS_TAB,
    MOVE_TAB_LEFT,
    MOVE_TAB_RIGHT,
    BACK,
    FORWARD
}

object Shortcuts {
    fun resolve(keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean): ShortcutAction? {
        if (ctrl && !alt) return resolveControl(keyCode, shift)
        if (alt && !ctrl && !shift) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> ShortcutAction.BACK
                KeyEvent.KEYCODE_DPAD_RIGHT -> ShortcutAction.FORWARD
                else -> null
            }
        }
        if (!ctrl && !alt && !shift && keyCode == KeyEvent.KEYCODE_F5) return ShortcutAction.RELOAD
        return null
    }

    private fun resolveControl(keyCode: Int, shift: Boolean): ShortcutAction? = when (keyCode) {
        KeyEvent.KEYCODE_T -> if (shift) ShortcutAction.RESTORE_TAB else ShortcutAction.NEW_TAB
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_F4 -> if (shift) null else ShortcutAction.CLOSE_TAB
        KeyEvent.KEYCODE_L -> if (shift) null else ShortcutAction.FOCUS_ADDRESS
        KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_F5 -> if (shift) null else ShortcutAction.RELOAD
        KeyEvent.KEYCODE_D -> if (shift) null else ShortcutAction.BOOKMARK
        KeyEvent.KEYCODE_H -> if (shift) null else ShortcutAction.HISTORY
        KeyEvent.KEYCODE_J -> if (shift) null else ShortcutAction.DOWNLOADS
        KeyEvent.KEYCODE_F -> if (shift) null else ShortcutAction.FIND
        KeyEvent.KEYCODE_TAB -> if (shift) ShortcutAction.PREVIOUS_TAB else ShortcutAction.NEXT_TAB
        KeyEvent.KEYCODE_PAGE_DOWN -> if (shift) ShortcutAction.MOVE_TAB_RIGHT else ShortcutAction.NEXT_TAB
        KeyEvent.KEYCODE_PAGE_UP -> if (shift) ShortcutAction.MOVE_TAB_LEFT else ShortcutAction.PREVIOUS_TAB
        else -> null
    }
}
