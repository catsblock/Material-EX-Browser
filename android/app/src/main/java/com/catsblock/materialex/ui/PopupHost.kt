package com.catsblock.materialex.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.materialex.R
import com.catsblock.materialex.core.popup.PopupPolicy
import com.catsblock.materialex.core.popup.PopupWindow
import com.catsblock.materialex.engine.BrowserEngine
import com.catsblock.materialex.engine.PageState
import org.mozilla.geckoview.GeckoView
import java.net.URI

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PopupHost(
    engine: BrowserEngine,
    openerId: String?,
    dockAtBottom: Boolean,
    modifier: Modifier = Modifier
) {
    val popups by engine.popups.collectAsStateWithLifecycle()
    val pages by engine.pageStates.collectAsStateWithLifecycle()
    val foreground = PopupPolicy.foreground(popups, openerId)
    val minimized = PopupPolicy.minimized(popups, openerId)
    var lastShown by remember { mutableStateOf<PopupWindow?>(null) }
    LaunchedEffect(foreground) {
        if (foreground != null) lastShown = foreground
    }
    val shown = foreground ?: lastShown
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    BackHandler(enabled = foreground != null) {
        val id = foreground?.id ?: return@BackHandler
        if (pages[id]?.canGoBack == true) engine.popupGoBack(id) else engine.closePopup(id)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = foreground != null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .pointerInput(Unit) { detectTapGestures { } }
            )
        }
        AnimatedVisibility(
            visible = foreground != null,
            enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.86f),
            exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            shown?.let { popup ->
                PopupSurface(
                    engine = engine,
                    popup = popup,
                    page = pages[popup.id] ?: PageState()
                )
            }
        }
        Column(
            modifier = Modifier
                .align(if (dockAtBottom) Alignment.BottomStart else Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            minimized.forEach { popup ->
                val page = pages[popup.id] ?: PageState()
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.6f)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .widthIn(max = 220.dp)
                            .height(48.dp)
                            .clickable(onClickLabel = stringResource(R.string.popup_restore)) {
                                engine.restorePopup(popup.id)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = popupLabel(page),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PopupSurface(engine: BrowserEngine, popup: PopupWindow, page: PageState) {
    val session = engine.popupSession(popup.id)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 14.dp,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 16.dp)
            .widthIn(max = 720.dp)
            .fillMaxSize()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp)
            ) {
                val secure = page.url.startsWith("https://") && page.isSecure
                Icon(
                    imageVector = if (secure) Icons.Default.Lock else Icons.Default.Warning,
                    contentDescription = stringResource(if (secure) R.string.security_secure else R.string.security_insecure),
                    tint = if (secure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        text = page.title.ifEmpty { hostOf(page.url) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = hostOf(page.url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { engine.minimizePopup(popup.id) }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.popup_minimize))
                }
                IconButton(onClick = { engine.closePopup(popup.id) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.popup_close))
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 20.dp)) {
                if (page.isLoading) {
                    LinearWavyProgressIndicator(
                        progress = { (page.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                if (session != null) {
                    AndroidView(
                        factory = { context -> GeckoView(context) },
                        update = { view ->
                            if (view.session !== session) {
                                view.releaseSession()
                                view.setSession(session)
                            }
                        },
                        onRelease = { view -> view.releaseSession() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun popupLabel(page: PageState): String = page.title.ifEmpty { hostOf(page.url) }

private fun hostOf(url: String): String =
    runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
