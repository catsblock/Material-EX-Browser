package com.catsblock.materialex.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.materialex.R
import com.catsblock.materialex.core.ByteFormat
import com.catsblock.materialex.core.DownloadStatus
import com.catsblock.materialex.data.DownloadController
import com.catsblock.materialex.data.LiveDownload
import com.catsblock.materialex.data.db.DownloadRecord
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(controller: DownloadController, onClose: () -> Unit) {
    val records by remember { controller.records() }.collectAsStateWithLifecycle(emptyList())
    val live by controller.live.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_downloads)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { scope.launch { controller.clearFinished() } },
                        enabled = records.any { it.status != DownloadStatus.ACTIVE.name }
                    ) { Text(stringResource(R.string.downloads_clear_finished)) }
                }
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.downloads_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(records, key = { it.id }) { record ->
                    DownloadRow(
                        record = record,
                        live = live[record.id],
                        onOpen = {
                            scope.launch {
                                val intent = controller.openIntent(record) ?: return@launch
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                }
                            }
                        },
                        onCancel = { controller.cancel(record.id) },
                        onDelete = { scope.launch { controller.delete(record) } }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadRow(
    record: DownloadRecord,
    live: LiveDownload?,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val active = record.status == DownloadStatus.ACTIVE.name
    val downloaded = live?.downloaded ?: record.downloadedBytes
    val total = live?.total ?: record.totalBytes
    val host = Uri.parse(record.sourceUrl).host ?: record.sourceUrl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = record.status == DownloadStatus.COMPLETED.name, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(record.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (active) {
                if (total > 0) {
                    LinearWavyProgressIndicator(
                        progress = { (downloaded.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                val percent = if (total > 0) " \u00b7 ${(downloaded * 100 / total).toInt()}%" else ""
                val size = if (total > 0) "${ByteFormat.size(downloaded)} / ${ByteFormat.size(total)}" else ByteFormat.size(downloaded)
                val speed = live?.let { " \u00b7 ${ByteFormat.speed(it.bytesPerSecond)}" }.orEmpty()
                Text(size + percent + speed, style = MaterialTheme.typography.labelMedium)
            } else {
                val label = when (record.status) {
                    DownloadStatus.COMPLETED.name -> stringResource(R.string.download_completed) + " \u00b7 " + ByteFormat.size(record.downloadedBytes)
                    DownloadStatus.CANCELLED.name -> stringResource(R.string.download_cancelled)
                    DownloadStatus.MISSING.name -> stringResource(R.string.download_missing)
                    else -> stringResource(R.string.download_failed)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (record.status == DownloadStatus.COMPLETED.name) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
        }
        if (active) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}
