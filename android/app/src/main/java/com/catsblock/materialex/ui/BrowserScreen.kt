package com.catsblock.materialex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.materialex.data.BrowserSettings
import com.catsblock.materialex.data.ToolbarPosition
import com.catsblock.materialex.engine.BrowserEngine
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(
    engine: BrowserEngine,
    settings: BrowserSettings,
    onSubmit: (String) -> Boolean,
    onOpenTabs: () -> Unit,
    onNewTab: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val tabs by engine.tabs.collectAsStateWithLifecycle()
    val session by engine.activeSession.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    var frameHeight by remember { mutableStateOf(0.dp) }
    val atTop = settings.toolbarPosition == ToolbarPosition.TOP
    val topInset: Dp = if (atTop) frameHeight else 0.dp
    val bottomInset: Dp = if (atTop) 0.dp else frameHeight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AndroidView(
            factory = { context -> GeckoView(context) },
            update = { view ->
                val target = session
                if (target == null) {
                    view.releaseSession()
                } else if (view.session !== target) {
                    view.releaseSession()
                    view.setSession(target)
                }
                engine.flushClosed()
            },
            onRelease = { view -> view.releaseSession() },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset, bottom = bottomInset)
        )
        FloatingSearchFrame(
            state = state,
            tabCount = tabs.tabs.size,
            position = settings.toolbarPosition,
            onSubmit = onSubmit,
            onBack = engine::goBack,
            onForward = engine::goForward,
            onReload = engine::reload,
            onStop = engine::stop,
            onOpenTabs = onOpenTabs,
            onNewTab = onNewTab,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                .onSizeChanged { size -> frameHeight = with(density) { size.height.toDp() } }
        )
    }
}
