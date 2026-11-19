package com.catsblock.materialex.core

import kotlin.math.abs

class ScrollChromeTracker(
    private val travelThreshold: Int = 48,
    private val topZone: Int = 8
) {
    private var lastY = 0
    private var travel = 0

    var visible: Boolean = true
        private set

    fun onScroll(y: Int): Boolean {
        val delta = y - lastY
        lastY = y
        if (y <= topZone) {
            travel = 0
            visible = true
            return visible
        }
        if (delta == 0) return visible
        travel = if (travel != 0 && (travel > 0) != (delta > 0)) delta else travel + delta
        if (travel > travelThreshold) {
            visible = false
            travel = 0
        } else if (abs(travel) > travelThreshold) {
            visible = true
            travel = 0
        }
        return visible
    }

    fun reset() {
        lastY = 0
        travel = 0
        visible = true
    }
}
