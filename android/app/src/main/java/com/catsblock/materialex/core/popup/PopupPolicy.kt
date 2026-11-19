package com.catsblock.materialex.core.popup

data class PopupWindow(
    val id: String,
    val openerId: String,
    val minimized: Boolean = false
)

object PopupPolicy {
    const val MAX_OPEN = 4
    const val GESTURE_WINDOW_MS = 1500L

    fun allow(
        blockingEnabled: Boolean,
        openCount: Int,
        lastGestureAt: Long,
        now: Long
    ): Boolean {
        if (openCount >= MAX_OPEN) return false
        if (!blockingEnabled) return true
        return lastGestureAt > 0L && now - lastGestureAt in 0..GESTURE_WINDOW_MS
    }

    fun foreground(popups: List<PopupWindow>, openerId: String?): PopupWindow? =
        popups.lastOrNull { it.openerId == openerId && !it.minimized }

    fun minimized(popups: List<PopupWindow>, openerId: String?): List<PopupWindow> =
        popups.filter { it.openerId == openerId && it.minimized }
}
