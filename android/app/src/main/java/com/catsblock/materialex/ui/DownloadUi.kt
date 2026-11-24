package com.catsblock.materialex.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.materialex.R
import com.catsblock.materialex.core.ByteFormat
import com.catsblock.materialex.core.DownloadEta
import com.catsblock.materialex.core.DownloadRisk
import com.catsblock.materialex.core.DownloadSafety
import com.catsblock.materialex.core.DownloadStatus
import com.catsblock.materialex.data.DownloadController
import com.catsblock.materialex.data.LiveDownload
import com.catsblock.materialex.data.db.DownloadRecord
import kotlinx.coroutines.delay

object DownloadIcons {
    val Download: ImageVector by lazy {
        ImageVector.Builder("Download", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 20f)
                horizontalLineTo(19f)
                verticalLineTo(18f)
                horizontalLineTo(5f)
                close()
                moveTo(19f, 9f)
                horizontalLineTo(15f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                horizontalLineTo(5f)
                lineTo(12f, 16f)
                close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                horizontalLineTo(10f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                close()
                moveTo(14f, 19f)
                horizontalLineTo(18f)
                verticalLineTo(5f)
                horizontalLineTo(14f)
                close()
            }
        }.build()
    }
}

@Composable
internal fun riskText(risk: DownloadRisk): String = stringResource(
    when (risk) {
        DownloadRisk.EXECUTABLE -> R.string.download_risk_executable
        DownloadRisk.SPOOFED_NAME -> R.string.download_risk_spoofed
        DownloadRisk.MIME_MISMATCH -> R.string.download_risk_mismatch
        DownloadRisk.INSECURE_SOURCE -> R.string.download_risk_insecure
    }
)

@Composable
internal fun downloadSummary(record: DownloadRecord, live: LiveDownload?): String {
    val downloaded = live?.downloaded ?: record.downloadedBytes
    val total = live?.total ?: record.totalBytes
    val parts = ArrayList<String>()
    when (record.status) {
        DownloadStatus.ACTIVE.name -> {
            if (live?.paused == true) parts += stringResource(R.string.download_paused)
            if (total > 0) parts += "${(downloaded * 100 / total).toInt()}%"
            parts += if (total > 0) "${ByteFormat.size(downloaded)} / ${ByteFormat.size(total)}" else ByteFormat.size(downloaded)
            if (live != null && !live.paused && live.bytesPerSecond > 0) {
                parts += ByteFormat.speed(live.bytesPerSecond)
                val eta = DownloadEta.seconds(downloaded, total, live.bytesPerSecond)
                if (eta != null) parts += stringResource(R.string.download_remaining, DownloadEta.format(eta))
            }
        }
        DownloadStatus.COMPLETED.name -> {
            parts += stringResource(R.string.download_completed)
            parts += ByteFormat.size(record.downloadedBytes)
        }
        DownloadStatus.CANCELLED.name -> parts += stringResource(R.string.download_cancelled)
        DownloadStatus.MISSING.name -> parts += stringResource(R.string.download_missing)
        DownloadStatus.BLOCKED.name -> parts += stringResource(R.string.download_blocked_reason)
        else -> parts += stringResource(R.string.download_failed)
    }
    return parts.joinToString(" \u00b7 ")
}

@Composable
internal fun statusLabel(record: DownloadRecord, live: LiveDownload?): String = stringResource(
    when (record.status) {
        DownloadStatus.ACTIVE.name -> if (live?.paused == true) R.string.download_paused else R.string.download_active
        DownloadStatus.COMPLETED.name -> R.string.download_completed
        DownloadStatus.CANCELLED.name -> R.string.download_cancelled
        DownloadStatus.MISSING.name -> R.string.download_missing
        DownloadStatus.BLOCKED.name -> R.string.download_blocked
        else -> R.string.download_failed
    }
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadPanel(controller: DownloadController, onOpenAll: () -> Unit, modifier: Modifier = Modifier) {
    val records by remember { controller.records() }.collectAsStateWithLifecycle(emptyList())
    val live by controller.live.collectAsStateWithLifecycle()
    val active = records.filter { it.status == DownloadStatus.ACTIVE.name }
    val activeIds = active.map { it.id }.toSet()
    val latestDone = records.firstOrNull { it.status == DownloadStatus.COMPLETED.name }
    var dismissedAt by remember { mutableLongStateOf(0L) }
    var flash by remember { mutableStateOf(false) }
    var flashKey by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf(activeIds) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var held by remember { mutableStateOf(emptyList<DownloadRecord>()) }

    LaunchedEffect(activeIds) {
        val ended = previous - activeIds
        previous = activeIds
        if (ended.isNotEmpty() && records.any { it.id in ended && it.status == DownloadStatus.COMPLETED.name }) {
            flash = true
            flashKey++
        }
    }
    LaunchedEffect(flashKey) {
        if (flashKey > 0) {
            delay(DONE_VISIBLE_MS)
            flash = false
        }
    }

    val newest = active.maxOfOrNull { it.createdAt } ?: 0L
    val shown = when {
        active.isNotEmpty() && newest > dismissedAt -> active
        active.isEmpty() && flash && latestDone != null -> listOf(latestDone)
        else -> emptyList()
    }
    LaunchedEffect(shown) {
        if (shown.isNotEmpty()) held = shown
    }
    val items = if (shown.isNotEmpty()) shown else held
    val reduceMotion = rememberReduceMotion()

    AnimatedVisibility(
        visible = shown.isNotEmpty(),
        modifier = modifier,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(200)) + expandVertically(tween(260)),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(160)) + shrinkVertically(tween(200))
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.action_downloads),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (items.size > PANEL_LIMIT) {
                        TextButton(onClick = onOpenAll) { Text(stringResource(R.string.download_panel_more, items.size - PANEL_LIMIT)) }
                    }
                    IconButton(onClick = { dismissedAt = System.currentTimeMillis() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.download_panel_dismiss))
                    }
                }
                items.take(PANEL_LIMIT).forEach { record ->
                    PanelItem(
                        record = record,
                        live = live[record.id],
                        onPauseResume = { paused -> if (paused) controller.resume(record.id) else controller.pause(record.id) },
                        onCancel = { controller.cancel(record.id) },
                        onDetails = { detailId = record.id }
                    )
                }
            }
        }
    }

    detailId?.let { id ->
        records.firstOrNull { it.id == id }?.let { record ->
            DownloadDetailsSheet(record = record, live = live[record.id], onDismiss = { detailId = null })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PanelItem(
    record: DownloadRecord,
    live: LiveDownload?,
    onPauseResume: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onDetails: () -> Unit
) {
    val active = record.status == DownloadStatus.ACTIVE.name
    val paused = live?.paused == true
    val downloaded = live?.downloaded ?: record.downloadedBytes
    val total = live?.total ?: record.totalBytes
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(DownloadIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    downloadSummary(record, live),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
        }
        if (active) {
            if (total > 0) {
                LinearWavyProgressIndicator(
                    progress = { (downloaded.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp)
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadDetailsSheet(record: DownloadRecord, live: LiveDownload?, onDismiss: () -> Unit) {
    val downloaded = live?.downloaded ?: record.downloadedBytes
    val total = live?.total ?: record.totalBytes
    val risks = DownloadSafety.assess(record.fileName, record.mimeType, record.sourceUrl)
    val extension = DownloadSafety.extension(record.fileName).uppercase()
    val started = remember(record.createdAt) {
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            .format(java.util.Date(record.createdAt))
    }
    val unknown = stringResource(R.string.download_unknown_size)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(record.fileName, style = MaterialTheme.typography.titleLarge)
            DetailRow(stringResource(R.string.download_detail_status), statusLabel(record, live))
            DetailRow(
                stringResource(R.string.download_detail_type),
                if (extension.isEmpty()) record.mimeType else "$extension \u00b7 ${record.mimeType}"
            )
            DetailRow(stringResource(R.string.download_detail_source), Uri.parse(record.sourceUrl).host ?: record.sourceUrl)
            DetailRow(stringResource(R.string.download_detail_destination), stringResource(R.string.download_destination_value))
            DetailRow(stringResource(R.string.download_detail_size), if (total > 0) ByteFormat.size(total) else unknown)
            DetailRow(
                stringResource(R.string.download_detail_progress),
                if (total > 0) "${(downloaded * 100 / total).toInt()}% \u00b7 ${ByteFormat.size(downloaded)}" else ByteFormat.size(downloaded)
            )
            if (record.status == DownloadStatus.ACTIVE.name && live != null && !live.paused && live.bytesPerSecond > 0) {
                DetailRow(stringResource(R.string.download_detail_speed), ByteFormat.speed(live.bytesPerSecond))
                DownloadEta.seconds(downloaded, total, live.bytesPerSecond)?.let {
                    DetailRow(stringResource(R.string.download_detail_remaining), DownloadEta.format(it))
                }
            }
            DetailRow(stringResource(R.string.download_detail_started), started)
            risks.forEach { risk ->
                Text(riskText(risk), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun RiskyOpenDialog(record: DownloadRecord, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val risks = DownloadSafety.assess(record.fileName, record.mimeType, record.sourceUrl)
        .filter { it != DownloadRisk.INSECURE_SOURCE }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_open_anyway_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.fileName, style = MaterialTheme.typography.titleSmall)
                risks.forEach { Text(riskText(it)) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.download_open_anyway)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private const val PANEL_LIMIT = 3
private const val DONE_VISIBLE_MS = 4000L
