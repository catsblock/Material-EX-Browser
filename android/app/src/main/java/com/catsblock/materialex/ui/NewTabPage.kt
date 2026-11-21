package com.catsblock.materialex.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R
import com.catsblock.materialex.core.Resolution
import com.catsblock.materialex.core.UrlResolver
import com.catsblock.materialex.data.BrowserSettings
import com.catsblock.materialex.data.db.Bookmark
import com.catsblock.materialex.data.db.FrequentSite
import com.catsblock.materialex.data.db.HistoryEntry
import com.catsblock.materialex.data.db.Shortcut

private data class ShortcutDraft(val id: Long?, val title: String, val url: String)

private val tileShapes: List<Shape> = listOf(
    RoundedCornerShape(20.dp),
    CircleShape,
    RoundedCornerShape(topStart = 26.dp, topEnd = 8.dp, bottomEnd = 26.dp, bottomStart = 8.dp),
    RoundedCornerShape(12.dp)
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NewTabPage(
    settings: BrowserSettings,
    shortcuts: List<Shortcut>,
    frequent: List<FrequentSite>,
    recent: List<HistoryEntry>,
    bookmarks: List<Bookmark>,
    onOpen: (String) -> Unit,
    onSaveShortcut: (Long?, String, String) -> Unit,
    onDeleteShortcut: (Long) -> Unit,
    onMoveShortcut: (Long, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf<ShortcutDraft?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 96.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (settings.showShortcuts) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    shortcuts.forEachIndexed { index, shortcut ->
                        Column(
                            modifier = Modifier
                                .width(76.dp)
                                .combinedClickable(
                                    onClick = { onOpen(shortcut.url) },
                                    onLongClick = { draft = ShortcutDraft(shortcut.id, shortcut.title, shortcut.url) }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, tileShapes[index % tileShapes.size]),
                                contentAlignment = Alignment.Center
                            ) {
                                Favicon(url = shortcut.url, size = 36.dp)
                            }
                            Text(
                                text = shortcut.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .width(76.dp)
                            .clickable { draft = ShortcutDraft(null, "", "") },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = stringResource(R.string.home_shortcuts_add),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (settings.showFrequent && frequent.isNotEmpty()) {
                PageSection(stringResource(R.string.home_frequent)) {
                    frequent.take(5).forEach { site ->
                        PageRow(site.title.ifBlank { site.url }, site.url) { onOpen(site.url) }
                    }
                }
            }
            if (settings.showBookmarks && bookmarks.isNotEmpty()) {
                PageSection(stringResource(R.string.bookmarks_title)) {
                    bookmarks.take(5).forEach { bookmark ->
                        PageRow(bookmark.title, bookmark.url) { onOpen(bookmark.url) }
                    }
                }
            }
            if (settings.showRecent && recent.isNotEmpty()) {
                PageSection(stringResource(R.string.home_recent)) {
                    recent.take(5).forEach { entry ->
                        PageRow(entry.title.ifBlank { entry.url }, entry.url) { onOpen(entry.url) }
                    }
                }
            }
        }
    }

    draft?.let { current ->
        ShortcutDialog(
            draft = current,
            canMoveEarlier = current.id != null && shortcuts.firstOrNull()?.id != current.id,
            canMoveLater = current.id != null && shortcuts.lastOrNull()?.id != current.id,
            onDismiss = { draft = null },
            onSave = { title, url ->
                onSaveShortcut(current.id, title, url)
                draft = null
            },
            onDelete = {
                current.id?.let(onDeleteShortcut)
                draft = null
            },
            onMove = { delta -> current.id?.let { onMoveShortcut(it, delta) } }
        )
    }
}

@Composable
private fun PageSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}

@Composable
private fun PageRow(title: String, url: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(Uri.parse(url).host ?: url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ShortcutDialog(
    draft: ShortcutDraft,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit
) {
    var title by remember { mutableStateOf(draft.title) }
    var url by remember { mutableStateOf(draft.url) }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.id == null) R.string.home_shortcuts_add else R.string.shortcut_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.shortcut_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        invalid = false
                    },
                    singleLine = true,
                    isError = invalid,
                    label = { Text(stringResource(R.string.shortcut_url)) },
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.settings_homepage_invalid)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (draft.id != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMove(-1) }, enabled = canMoveEarlier) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.shortcut_move_earlier))
                        }
                        IconButton(onClick = { onMove(1) }, enabled = canMoveLater) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.shortcut_move_later))
                        }
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (UrlResolver.resolve(url) is Resolution.Navigate) onSave(title, url) else invalid = true
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
