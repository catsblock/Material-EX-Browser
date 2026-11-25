package com.catsblock.materialex.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.materialex.R
import com.catsblock.materialex.core.extension.StoreExtension
import com.catsblock.materialex.data.ExtensionStoreClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}

@Composable
fun ExtensionStoreDialog(
    installedIds: Set<String>,
    onInstall: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val client = remember { ExtensionStoreClient() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<StoreExtension>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(setOf<String>()) }

    fun load(reset: Boolean) {
        val next = if (reset) 1 else page + 1
        loading = true
        failed = false
        scope.launch {
            client.search(query, next).onSuccess { result ->
                items = if (reset) result.items else items + result.items
                page = next
                hasMore = result.hasMore
            }.onFailure { failed = true }
            loading = false
        }
    }

    LaunchedEffect(query) {
        if (items.isNotEmpty() || !loading) delay(350)
        load(reset = true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.ext_store_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.onboarding_close)) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ext_store_search)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.ext_store_source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (failed) {
                    Text(
                        stringResource(R.string.ext_store_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { load(reset = true) }) { Text(stringResource(R.string.download_retry)) }
                } else if (!loading && items.isEmpty()) {
                    Text(stringResource(R.string.ext_store_empty), style = MaterialTheme.typography.bodyLarge)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(items, key = { it.guid }) { ext ->
                        val installed = ext.guid in installedIds
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(ext.name, style = MaterialTheme.typography.titleMedium)
                                if (ext.summary.isNotBlank()) {
                                    Text(
                                        ext.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val rating = ext.rating?.let { "★ %.1f · ".format(it) }.orEmpty()
                                Text(
                                    rating + stringResource(R.string.ext_store_users, compactCount(ext.users)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (installed) {
                                Text(
                                    stringResource(R.string.ext_store_installed),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            } else {
                                Button(
                                    onClick = {
                                        requested = requested + ext.guid
                                        onInstall(ext.installUrl)
                                    },
                                    enabled = ext.guid !in requested,
                                    modifier = Modifier.padding(start = 12.dp)
                                ) { Text(stringResource(R.string.ext_store_install)) }
                            }
                        }
                    }
                    if (hasMore && !loading) {
                        item {
                            OutlinedButton(onClick = { load(reset = false) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.ext_store_more))
                            }
                        }
                    }
                    if (loading) {
                        item { Text(stringResource(R.string.ext_store_loading), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}
