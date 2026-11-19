package com.catsblock.materialex.core.memory

import com.catsblock.materialex.core.tabs.TabLifecycle

enum class MemoryMode {
    AUTOMATIC,
    LOW,
    BALANCED,
    HIGH,
    CUSTOM;

    companion object {
        fun fromId(id: String?): MemoryMode = entries.firstOrNull { it.name == id } ?: AUTOMATIC
    }
}

object MemoryBudget {
    fun loadedLimit(mode: MemoryMode, customLimit: Int, totalRamMb: Long): Int {
        val value = when (mode) {
            MemoryMode.AUTOMATIC -> when {
                totalRamMb <= 0L -> TabLifecycle.DEFAULT_LOADED
                totalRamMb < 3072L -> 3
                totalRamMb < 6144L -> 5
                totalRamMb < 10240L -> 8
                else -> 12
            }
            MemoryMode.LOW -> if (totalRamMb in 1 until 3072L) 2 else 3
            MemoryMode.BALANCED -> TabLifecycle.DEFAULT_LOADED
            MemoryMode.HIGH -> 10
            MemoryMode.CUSTOM -> customLimit
        }
        return value.coerceIn(TabLifecycle.MIN_LOADED, TabLifecycle.MAX_LOADED)
    }

    fun pressureLimit(trimLevel: Int): Int? = when {
        trimLevel >= 80 -> 1
        trimLevel >= 60 -> 2
        trimLevel in 15..19 -> 1
        trimLevel in 10..14 -> 2
        else -> null
    }

    fun effectiveLimit(configured: Int, trimLevel: Int): Int {
        val pressure = pressureLimit(trimLevel) ?: return configured
        return minOf(configured, pressure)
    }

    fun protectedIds(audibleIds: Set<String>, openerIds: Set<String>): Set<String> = audibleIds + openerIds
}
