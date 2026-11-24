package com.catsblock.materialex.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.materialex.R
import com.catsblock.materialex.core.tabs.Tab
import com.catsblock.materialex.data.TabLayout
import com.catsblock.materialex.engine.BrowserEngine
import com.catsblock.materialex.engine.PageState
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TabSwitcherScreen(
    engine: BrowserEngine,
    layout: TabLayout,
    onClose: () -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: (String) -> Unit
) {
    val tabsState by engine.tabs.collectAsStateWithLifecycle()
    val pages by engine.pageStates.collectAsStateWithLifecycle()
    val context = LocalContext.current

    @Composable
    fun entry(tab: Tab, tall: Boolean, height: Int, modifier: Modifier = Modifier) {
        val page = pages[tab.id]
        TabEntry(
            tab = tab,
            page = page,
            selected = tab.id == tabsState.activeId,
            tall = tall,
            areaHeight = height,
            modifier = modifier,
            onSelect = {
                engine.selectTab(tab.id)
                onClose()
            },
            onClose = { onCloseTab(tab.id) },
            onDuplicate = { engine.duplicateTab(tab.id) },
            onCloseOthers = { engine.closeOthers(tab.id) },
            onCloseRight = { engine.closeTabsToRight(tab.id) },
            onCopy = {
                val address = page?.url?.ifBlank { null } ?: tab.url
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("url", address))
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tabs_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = engine::restoreClosedTab, enabled = tabsState.recentlyClosed.isNotEmpty()) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.tab_restore_closed))
                    }
                    IconButton(onClick = onNewTab) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_new_tab))
                    }
                }
            )
        }
    ) { padding ->
        when (layout) {
            TabLayout.GRID -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tabsState.tabs, key = { it.id }) { tab -> entry(tab, true, 88) }
            }
            TabLayout.LIST -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabsState.tabs, key = { it.id }) { tab -> entry(tab, false, 0) }
            }
            TabLayout.COVER_FLOW -> {
                val tabs = tabsState.tabs
                val tilt = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -24f else 24f
                val pager = rememberPagerState(
                    initialPage = tabs.indexOfFirst { it.id == tabsState.activeId }.coerceAtLeast(0)
                ) { tabs.size }
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    HorizontalPager(
                        state = pager,
                        contentPadding = PaddingValues(horizontal = 56.dp),
                        pageSpacing = 12.dp,
                        key = { tabs[it].id }
                    ) { page ->
                        val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        entry(
                            tab = tabs[page],
                            tall = true,
                            height = 280,
                            modifier = Modifier.graphicsLayer {
                                val distance = abs(offset).coerceAtMost(1f)
                                val scale = 1f - 0.14f * distance
                                scaleX = scale
                                scaleY = scale
                                rotationY = offset.coerceIn(-1f, 1f) * tilt
                                alpha = 1f - 0.35f * distance
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabEntry(
    tab: Tab,
    page: PageState?,
    selected: Boolean,
    tall: Boolean,
    areaHeight: Int,
    modifier: Modifier,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseRight: () -> Unit,
    onCopy: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val title = page?.title?.ifBlank { null } ?: tab.title.ifBlank { null } ?: stringResource(R.string.tab_untitled)
    val address = page?.url?.ifBlank { null } ?: tab.url

    OutlinedCard(
        onClick = onSelect,
        modifier = modifier,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        if (tall) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(areaHeight.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Favicon(url = address, size = 56.dp, enabled = !tab.isPrivate)
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) { Indicators(page) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!tall) {
                Favicon(url = address, size = 36.dp, enabled = !tab.isPrivate)
                Box(modifier = Modifier.size(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (tall) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (tab.isPrivate) stringResource(R.string.tab_private) + " · " + address else address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!tall) Row { Indicators(page) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_menu))
                }
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
                        text = { Text(stringResource(R.string.tab_copy_url)) },
                        enabled = address.isNotEmpty(),
                        onClick = {
                            menuOpen = false
                            onCopy()
                        }
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close_tab))
            }
        }
    }
}

@Composable
private fun Indicators(page: PageState?) {
    if (page == null) return
    if (page.isLoading) {
        CircularProgressIndicator(
            progress = { page.progress / 100f },
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp
        )
    }
    if (page.audible) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.tab_audio_playing),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
