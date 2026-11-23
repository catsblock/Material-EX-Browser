package com.catsblock.materialex.core

object TextScale {
    const val MIN = 50
    const val MAX = 200
    const val STEP = 10
    const val DEFAULT = 100

    fun coerce(percent: Int): Int = ((percent.coerceIn(MIN, MAX) + STEP / 2) / STEP) * STEP

    fun factor(percent: Int): Float = coerce(percent) / 100f
}
