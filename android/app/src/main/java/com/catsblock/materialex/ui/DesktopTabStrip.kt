package com.catsblock.materialex.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.materialex.R
import com.catsblock.materialex.core.tabs.Tab
import com.catsblock.materialex.core.tabs.TabsState
import com.catsblock.materialex.engine.BrowserEngine
import com.catsblock.materialex.engine.PageState

val DesktopStripHeight = 48.dp

private const val DRAG_STEP_PX = 120f

@Composable
fun DesktopTabStripHost(
    engine: BrowserEngine,
    onNewTab: () -> Unit,
    onCloseTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs by engine.tabs.collectAsStateWithLifecycle()
    val pages by engine.pageStates.collectAsStateWithLifecycle()
    DesktopTabStrip(
        tabs = tabs,
        pages = pages,
        onSelect = engine::selectTab,
        onClose = onCloseTab,
        onNewTab = onNewTab,
        onDuplicate = engine::duplicateTab,
        onCloseOthers = engine::closeOthers,
        onCloseRight = engine::closeTabsToRight,
        onRestore = engine::restoreClosedTab,
        onMove = engine::moveTabBy,
        modifier = modifier
    )
}

@Composable
fun DesktopTabStrip(
    tabs: TabsState,
    pages: Map<String, PageState>,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onDuplicate: (String) -> Unit,
    onCloseOthers: (String) -> Unit,
    onCloseRight: (String) -> Unit,
    onRestore: () -> Unit,
    onMove: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeIndex = tabs.tabs.indexOfFirst { it.id == tabs.activeId }
    LaunchedEffect(tabs.activeId) {
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DesktopStripHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(tabs.tabs, key = { _, tab -> tab.id }) { _, tab ->
                DesktopTab(
                    tab = tab,
                    page = pages[tab.id],
                    selected = tab.id == tabs.activeId,
                    canRestore = tabs.recentlyClosed.isNotEmpty(),
                    onSelect = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) },
                    onDuplicate = { onDuplicate(tab.id) },
                    onCloseOthers = { onCloseOthers(tab.id) },
                    onCloseRight = { onCloseRight(tab.id) },
                    onRestore = onRestore,
                    onMove = { delta -> onMove(tab.id, delta) },
                    modifier = Modifier.animateItem()
                )
            }
        }
        IconButton(onClick = onNewTab, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_new_tab), modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopTab(
    tab: Tab,
    page: PageState?,
    selected: Boolean,
    canRestore: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseRight: () -> Unit,
    onRestore: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = when {
            selected -> colors.surfaceContainerHighest
            hovered -> colors.surfaceContainerHigh
            else -> Color.Transparent
        },
        label = "tabBackground"
    )
    val indicator by animateFloatAsState(if (selected) 0.5f else 0f, label = "tabIndicator")
    val title = page?.title?.ifBlank { null } ?: tab.title.ifBlank { null } ?: stringResource(R.string.tab_untitled)
    val address = page?.url?.ifBlank { null } ?: tab.url
    val currentMove by rememberUpdatedState(onMove)

    Box(
        modifier = modifier
            .widthIn(min = 112.dp, max = 220.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .hoverable(hover)
            .pointerInput(tab.id) {
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = { travelled = 0f },
                    onDragCancel = { travelled = 0f }
                ) { change, delta ->
                    change.consume()
                    travelled += delta
                    if (travelled > DRAG_STEP_PX) {
                        travelled = 0f
                        currentMove(1)
                    } else if (travelled < -DRAG_STEP_PX) {
                        travelled = 0f
                        currentMove(-1)
                    }
                }
            }
            .combinedClickable(onClick = onSelect, onLongClick = { menuOpen = true })
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp).align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page?.isLoading == true) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Favicon(url = address, size = 18.dp, enabled = !tab.isPrivate)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.onboarding_close),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(indicator)
                .height(3.dp)
                .clip(CircleShape)
                .background(colors.primary)
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tab_duplicate)) },
                onClick = {
                    menuOpen = false
                    onDuplicate()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tab_close_others)) },
                onClick = {
                    menuOpen = false
                    onCloseOthers()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tab_close_right)) },
                onClick = {
                    menuOpen = false
                    onCloseRight()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tab_restore_closed)) },
                enabled = canRestore,
                onClick = {
                    menuOpen = false
                    onRestore()
                }
            )
        }
    }
}
