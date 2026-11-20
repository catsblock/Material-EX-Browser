package com.catsblock.materialex.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

private const val DESKTOP_MIN_WIDTH_DP = 840
private const val DESKTOP_MIN_HEIGHT_DP = 480

@Composable
fun rememberDesktopLayout(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp >= DESKTOP_MIN_WIDTH_DP && configuration.screenHeightDp >= DESKTOP_MIN_HEIGHT_DP
}
