package com.catsblock.materialex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.materialex.R
import com.catsblock.materialex.data.BrowserSettings
import com.catsblock.materialex.data.Suggestion
import com.catsblock.materialex.data.ToolbarPosition
import com.catsblock.materialex.engine.BrowserEngine
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(
    engine: BrowserEngine,
    settings: BrowserSettings,
    suggestions: List<Suggestion>,
    onQueryChange: (String) -> Unit,
    onPickSuggestion: (String) -> Unit,
    onDeleteSuggestion: (String) -> Unit,
    onSubmit: (String) -> Boolean,
    onOpenTabs: () -> Unit,
    isBookmarked: Boolean,
    onNewTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSiteInfo: () -> Unit,
    onShare: () -> Unit,
    newTabContent: @Composable () -> Unit
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val tabs by engine.tabs.collectAsStateWithLifecycle()
    val session by engine.activeSession.collectAsStateWithLifecycle()
    val chromeVisible by engine.chromeVisible.collectAsStateWithLifecycle()
    val atTop = settings.toolbarPosition == ToolbarPosition.TOP
    var findOpen by remember { mutableStateOf(false) }

    DisposableEffect(state.url) {
        onDispose {
            if (findOpen) engine.clearFind()
        }
    }

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
            modifier = Modifier.fillMaxSize()
        )
        if (state.url.isEmpty() && !state.isLoading) newTabContent()
        if (findOpen) {
            FindBar(
                engine = engine,
                onClose = {
                    engine.clearFind()
                    findOpen = false
                },
                modifier = Modifier.align(if (atTop) Alignment.BottomCenter else Alignment.TopCenter)
            )
        }
        FloatingSearchFrame(
            state = state,
            tabCount = tabs.tabs.size,
            isBookmarked = isBookmarked,
            position = settings.toolbarPosition,
            hidden = settings.autoHideFrame && !chromeVisible && state.url.isNotEmpty(),
            desktopMode = state.desktopMode,
            suggestions = suggestions,
            onQueryChange = onQueryChange,
            onPickSuggestion = onPickSuggestion,
            onDeleteSuggestion = onDeleteSuggestion,
            onShare = onShare,
            onFind = { findOpen = true },
            onToggleDesktop = { engine.setDesktopMode(!state.desktopMode) },
            onSubmit = onSubmit,
            onBack = engine::goBack,
            onForward = engine::goForward,
            onReload = engine::reload,
            onStop = engine::stop,
            onOpenTabs = onOpenTabs,
            onNewTab = onNewTab,
            onToggleBookmark = onToggleBookmark,
            onOpenHistory = onOpenHistory,
            onOpenBookmarks = onOpenBookmarks,
            onOpenSettings = onOpenSettings,
            onOpenSiteInfo = onOpenSiteInfo,
            modifier = Modifier.align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
        )
    }
}

@Composable
private fun FindBar(engine: BrowserEngine, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var total by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it
                    engine.find(it, true) { count -> total = count }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { engine.find(query, true) { count -> total = count } }),
                modifier = Modifier.weight(1f).padding(vertical = 16.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.action_find),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = LocalTextStyle.current
                            )
                        }
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Text(total.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { engine.find(query, false) { count -> total = count } }, enabled = query.isNotEmpty()) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
            }
            IconButton(onClick = { engine.find(query, true) { count -> total = count } }, enabled = query.isNotEmpty()) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.onboarding_close))
            }
        }
    }
}
