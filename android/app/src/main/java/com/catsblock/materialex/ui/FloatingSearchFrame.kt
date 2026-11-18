package com.catsblock.materialex.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R
import com.catsblock.materialex.data.ToolbarPosition
import com.catsblock.materialex.engine.PageState

@Composable
fun FloatingSearchFrame(
    state: PageState,
    position: ToolbarPosition,
    onSubmit: (String) -> Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(insets)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp
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
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(20.dp),
                    tint = if (icon == Icons.Default.Warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                BasicTextField(
                    value = shown,
                    onValueChange = {
                        field = it
                        rejected = false
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
                } else {
                    IconButton(onClick = onReload, enabled = state.url.isNotEmpty()) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_reload))
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 6.dp)
                )
            }
        }
    }
}
