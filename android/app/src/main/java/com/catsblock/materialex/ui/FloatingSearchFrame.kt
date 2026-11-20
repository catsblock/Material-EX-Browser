package com.catsblock.materialex.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R
import com.catsblock.materialex.data.Suggestion
import com.catsblock.materialex.data.ToolbarPosition
import com.catsblock.materialex.engine.PageState

@Composable
fun FloatingSearchFrame(
    state: PageState,
    tabCount: Int,
    isBookmarked: Boolean,
    position: ToolbarPosition,
    hidden: Boolean,
    desktopMode: Boolean,
    suggestions: List<Suggestion>,
    onQueryChange: (String) -> Unit,
    onPickSuggestion: (String) -> Unit,
    onDeleteSuggestion: (String) -> Unit,
    onShare: () -> Unit,
    onOpenDownloads: () -> Unit,
    onFind: () -> Unit,
    onToggleDesktop: () -> Unit,
    onSubmit: (String) -> Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onOpenTabs: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSiteInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var rejected by remember { mutableStateOf(false) }

    val edge = if (position == ToolbarPosition.TOP) WindowInsetsSides.Top else WindowInsetsSides.Bottom
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + edge)
    val shown = if (focused) field else TextFieldValue(state.url)

    val concealed = hidden && !focused && !menuOpen
    val fromTop = position == ToolbarPosition.TOP

    AnimatedVisibility(
        visible = !concealed,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { full -> if (fromTop) -full else full } +
            fadeIn(spring(stiffness = 380f)),
        exit = slideOutVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { full -> if (fromTop) -full else full } +
            fadeOut(spring(stiffness = 500f))
    ) {
    Surface(
        modifier = Modifier
            .windowInsetsPadding(insets)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val secure = state.url.startsWith("https://") && state.isSecure
                val icon = when {
                    state.url.isEmpty() -> Icons.Default.Search
                    secure -> Icons.Default.Lock
                    else -> Icons.Default.Warning
                }
                val description = when {
                    state.url.isEmpty() -> null
                    secure -> stringResource(R.string.security_secure)
                    else -> stringResource(R.string.security_insecure)
                }
                IconButton(
                    onClick = onOpenSiteInfo,
                    enabled = state.url.isNotEmpty(),
                    modifier = Modifier.padding(start = 4.dp).size(44.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = description,
                        modifier = Modifier.size(20.dp),
                        tint = if (icon == Icons.Default.Warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = shown,
                    onValueChange = {
                        field = it
                        rejected = false
                        onQueryChange(it.text)
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        textDirection = TextDirection.Content
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        val accepted = onSubmit(field.text)
                        rejected = !accepted
                        if (accepted) focusManager.clearFocus()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                        .onFocusChanged {
                            if (it.isFocused && !focused) {
                                field = TextFieldValue(state.url, TextRange(0, state.url.length))
                                rejected = false
                            }
                            if (!it.isFocused && focused) onQueryChange("")
                            focused = it.isFocused
                        },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (shown.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.address_placeholder),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = LocalTextStyle.current
                                )
                            }
                            inner()
                        }
                    }
                )
                if (state.isLoading) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_stop))
                    }
                } else if (state.url.isNotEmpty()) {
                    IconButton(onClick = onReload) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_reload))
                    }
                }
                val tabsDescription = stringResource(R.string.tabs_title)
                IconButton(onClick = onOpenTabs) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
                            .semantics { contentDescription = tabsDescription },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = tabCount.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_new_tab)) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onNewTab()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_new_private_tab)) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onNewPrivateTab()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            enabled = state.url.startsWith("http"),
                            onClick = {
                                menuOpen = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_find)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            enabled = state.url.startsWith("http"),
                            onClick = {
                                menuOpen = false
                                onFind()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_desktop_view)) },
                            trailingIcon = if (desktopMode) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            enabled = state.url.startsWith("http"),
                            onClick = {
                                menuOpen = false
                                onToggleDesktop()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_back)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                            enabled = state.canGoBack,
                            onClick = {
                                menuOpen = false
                                onBack()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_forward)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                            enabled = state.canGoForward,
                            onClick = {
                                menuOpen = false
                                onForward()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (isBookmarked) R.string.action_remove_bookmark else R.string.action_add_bookmark
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null
                                )
                            },
                            enabled = state.url.startsWith("http"),
                            onClick = {
                                menuOpen = false
                                onToggleBookmark()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bookmarks_title)) },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onOpenBookmarks()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_downloads)) },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onOpenDownloads()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_title)) },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onOpenHistory()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_settings)) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            }
            if (focused && suggestions.isNotEmpty()) {
                suggestions.forEach { suggestion ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPickSuggestion(suggestion.text)
                                focusManager.clearFocus()
                            }
                            .padding(start = 16.dp, end = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = suggestion.text,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                        if (suggestion.fromHistory) {
                            IconButton(onClick = { onDeleteSuggestion(suggestion.text) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.suggestion_delete),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (rejected) {
                Text(
                    text = stringResource(R.string.input_rejected),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 10.dp)
                )
            }
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    trackColor = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }
    }
    }
}
