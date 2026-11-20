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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.materialex.R
import com.catsblock.materialex.core.DownloadSafety
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
    var detailId by remember { mutableStateOf<Long?>(null) }
    var confirmOpen by remember { mutableStateOf<DownloadRecord?>(null) }

    fun open(record: DownloadRecord) {
        scope.launch {
            val intent = controller.openIntent(record) ?: return@launch
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

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
                            val risks = DownloadSafety.assess(record.fileName, record.mimeType, record.sourceUrl)
                            if (DownloadSafety.needsOpenConfirmation(risks)) confirmOpen = record else open(record)
                        },
                        onPauseResume = { paused -> if (paused) controller.resume(record.id) else controller.pause(record.id) },
                        onCancel = { controller.cancel(record.id) },
                        onDetails = { detailId = record.id },
                        onDelete = { scope.launch { controller.delete(record) } }
                    )
                }
            }
        }
    }

    detailId?.let { id ->
        records.firstOrNull { it.id == id }?.let { DownloadDetailsSheet(it, live[it.id]) { detailId = null } }
    }
    confirmOpen?.let { record ->
        RiskyOpenDialog(
            record = record,
            onConfirm = {
                confirmOpen = null
                open(record)
            },
            onDismiss = { confirmOpen = null }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadRow(
    record: DownloadRecord,
    live: LiveDownload?,
    onOpen: () -> Unit,
    onPauseResume: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val active = record.status == DownloadStatus.ACTIVE.name
    val paused = live?.paused == true
    val downloaded = live?.downloaded ?: record.downloadedBytes
    val total = live?.total ?: record.totalBytes
    val host = Uri.parse(record.sourceUrl).host ?: record.sourceUrl
    val bad = record.status == DownloadStatus.FAILED.name || record.status == DownloadStatus.MISSING.name ||
        record.status == DownloadStatus.BLOCKED.name

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
            }
            Text(
                downloadSummary(record, live),
                style = MaterialTheme.typography.labelMedium,
                color = if (bad || record.status == DownloadStatus.CANCELLED.name) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (active) {
            IconButton(onClick = { onPauseResume(paused) }) {
                if (paused) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.download_resume))
                } else {
                    Icon(DownloadIcons.Pause, contentDescription = stringResource(R.string.download_pause))
                }
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        }
        IconButton(onClick = onDetails) {
            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.download_details))
        }
        if (!active) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}
