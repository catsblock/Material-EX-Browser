package com.catsblock.materialex.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
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
import com.catsblock.materialex.core.siteedit.SiteStyleRules
import com.catsblock.materialex.engine.BrowserEngine
import org.mozilla.geckoview.GeckoView

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BrowserScreen(
    engine: BrowserEngine,
    downloads: com.catsblock.materialex.data.DownloadController,
    settings: BrowserSettings,
    suggestions: List<Suggestion>,
    onQueryChange: (String) -> Unit,
    onPickSuggestion: (String) -> Unit,
    onDeleteSuggestion: (String) -> Unit,
    onSubmit: (String) -> Boolean,
    onOpenTabs: () -> Unit,
    isBookmarked: Boolean,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSiteInfo: () -> Unit,
    onShare: () -> Unit,
    onOpenDownloads: () -> Unit,
    onCloseTab: (String) -> Unit = {},
    focusTick: Int = 0,
    findTick: Int = 0,
    newTabContent: @Composable () -> Unit
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val tabs by engine.tabs.collectAsStateWithLifecycle()
    val session by engine.activeSession.collectAsStateWithLifecycle()
    val chromeVisible by engine.chromeVisible.collectAsStateWithLifecycle()
    val fullscreen by engine.fullscreen.collectAsStateWithLifecycle()
    val threats by engine.threats.collectAsStateWithLifecycle()
    val threat = tabs.activeId?.let { threats[it] }
    val atTop = settings.toolbarPosition == ToolbarPosition.TOP
    var findOpen by remember { mutableStateOf(false) }
    var siteEditorOpen by remember { mutableStateOf(false) }
    var userAgentOpen by remember { mutableStateOf(false) }
    val siteHost = SiteStyleRules.hostOf(state.url)
    val siteToolsEnabled = siteHost != null
    val desktop = rememberDesktopLayout()
    LaunchedEffect(findTick) {
        if (findTick > 0) findOpen = true
    }
    val atPageTop by engine.atTop.collectAsStateWithLifecycle()
    val currentAtTop by rememberUpdatedState(atPageTop)
    val density = LocalDensity.current
    val thresholdPx = with(density) { 88.dp.toPx() }
    val frameAllowancePx = with(density) { (if (atTop) 76.dp else 8.dp).toPx() }
    val indicatorPx = with(density) { 48.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val shownPull by animateFloatAsState(
        targetValue = pull,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "pull"
    )

    DisposableEffect(state.url) {
        onDispose {
            if (findOpen) engine.clearFind()
        }
    }

    if (siteEditorOpen && siteHost != null) {
        SiteEditorDialog(
            host = siteHost,
            bridge = engine.siteRules,
            isPrivate = tabs.active?.isPrivate == true,
            onDismiss = { siteEditorOpen = false }
        )
    }
    if (userAgentOpen && siteHost != null) {
        UserAgentDialog(
            host = siteHost,
            current = engine.userAgentFor(siteHost),
            onApply = { choice ->
                engine.applySiteUserAgent(siteHost, choice)
                userAgentOpen = false
            },
            onDismiss = { userAgentOpen = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    engine.noteUserGesture()
                }
            }
            .pointerInput(thresholdPx) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!currentAtTop || engine.fullscreen.value) return@awaitEachGesture
                    var tracking = false
                    var aborted = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size > 1) aborted = true
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dy = change.position.y - down.position.y
                        val dx = abs(change.position.x - down.position.x)
                        if (!tracking && !aborted) {
                            if (dy < -slop || dx > slop * 3) {
                                aborted = true
                            } else if (change.uptimeMillis - down.uptimeMillis > LONG_PRESS_MS) {
                                aborted = true
                            } else if (dy > slop && dy > dx * 2) {
                                tracking = true
                            }
                        }
                        pull = if (tracking && !aborted && currentAtTop) (dy * 0.5f).coerceIn(0f, thresholdPx * 1.4f) else 0f
                    }
                    val fire = tracking && !aborted && pull >= thresholdPx
                    pull = 0f
                    if (fire) engine.reload()
                }
            }
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
            modifier = if (fullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = if (desktop) DesktopStripHeight else 0.dp)
            }
        )
        if (fullscreen) return@Box
        if (state.url.isEmpty() && !state.isLoading) {
            Box(
                modifier = if (desktop) {
                    Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .padding(top = DesktopStripHeight)
                } else {
                    Modifier
                }
            ) { newTabContent() }
        }
        if (shownPull > 1f) {
            val progress = (shownPull / thresholdPx).coerceIn(0f, 1f)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .offset { IntOffset(0, (frameAllowancePx - indicatorPx * 0.8f + shownPull * 0.8f).roundToInt()) }
                    .size(48.dp)
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.6f + 0.4f * progress
                        scaleY = 0.6f + 0.4f * progress
                    }
            ) {
                LoadingIndicator(progress = { progress }, modifier = Modifier.padding(8.dp))
            }
        }
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
        PopupHost(
            engine = engine,
            openerId = tabs.activeId,
            dockAtBottom = atTop
        )
        threat?.let {
            ThreatPage(
                threat = it,
                onLeave = { engine.leaveThreat(it.tabId) },
                onContinue = { engine.continueDespiteThreat(it.tabId) }
            )
        }
        if (desktop) {
            DesktopTabStripHost(
                engine = engine,
                onNewTab = onNewTab,
                onCloseTab = onCloseTab,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            )
        }
        DownloadPanel(
            controller = downloads,
            onOpenAll = onOpenDownloads,
            modifier = Modifier
                .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(if (atTop) WindowInsetsSides.Top else WindowInsetsSides.Bottom)
                )
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (atTop) 84.dp + (if (desktop) DesktopStripHeight else 0.dp) else 0.dp,
                    bottom = if (atTop) 0.dp else 84.dp
                )
        )
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
            onOpenDownloads = onOpenDownloads,
            onFind = { findOpen = true },
            onToggleDesktop = { engine.setDesktopMode(!state.desktopMode) },
            siteToolsEnabled = siteToolsEnabled,
            onOpenSiteEditor = { siteEditorOpen = true },
            onOpenUserAgent = { userAgentOpen = true },
            onSubmit = onSubmit,
            onBack = engine::goBack,
            onForward = engine::goForward,
            onReload = engine::reload,
            onStop = engine::stop,
            onOpenTabs = onOpenTabs,
            onNewTab = onNewTab,
            onNewPrivateTab = onNewPrivateTab,
            onToggleBookmark = onToggleBookmark,
            onOpenHistory = onOpenHistory,
            onOpenBookmarks = onOpenBookmarks,
            onOpenSettings = onOpenSettings,
            onOpenSiteInfo = onOpenSiteInfo,
            desktop = desktop,
            focusTick = focusTick,
            modifier = Modifier
                .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(
                    if (desktop && atTop) {
                        Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                            .padding(top = DesktopStripHeight)
                    } else {
                        Modifier
                    }
                )
        )
    }
}

private const val LONG_PRESS_MS = 400L

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
