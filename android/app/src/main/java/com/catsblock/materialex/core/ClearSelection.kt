package com.catsblock.materialex.core

data class ClearSelection(
    val history: Boolean = false,
    val cookies: Boolean = false,
    val cache: Boolean = false,
    val searches: Boolean = false,
    val downloads: Boolean = false,
    val permissions: Boolean = false
) {
    val isEmpty: Boolean get() = !(history || cookies || cache || searches || downloads || permissions)
}
