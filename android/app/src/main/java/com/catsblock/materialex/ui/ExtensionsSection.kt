package com.catsblock.materialex.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R
import com.catsblock.materialex.core.extension.ExtensionUpdateStatus
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import com.catsblock.materialex.core.extension.ExtensionInfo
import com.catsblock.materialex.core.extension.ExtensionPrompt
import com.catsblock.materialex.core.extension.ExtensionRules

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExtensionsSection(
    extensions: List<ExtensionInfo>,
    installFailed: Boolean,
    onInstall: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    updateStatus: Map<String, ExtensionUpdateStatus>,
    onCheckUpdate: (String) -> Unit
) {
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var address by rememberSaveable { mutableStateOf("") }
    val normalized = ExtensionRules.normalizeInstallUrl(address)
    val shown = ExtensionRules.sortedForDisplay(extensions)

    if (shown.isEmpty()) {
        Text(
            stringResource(R.string.ext_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEachIndexed { index, extension ->
            Surface(
                shape = groupedShape(index, shown.size),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).clickable { detailsId = extension.id }) {
                        Text(extension.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            extension.version,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = extension.enabled, onCheckedChange = { onToggle(extension.id, it) })
                    IconButton(onClick = { onRemove(extension.id) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ext_remove))
                    }
                }
            }
        }
    }
    shown.firstOrNull { it.id == detailsId }?.let { extension ->
        ExtensionDetailsDialog(
            extension = extension,
            status = updateStatus[extension.id],
            onCheckUpdate = { onCheckUpdate(extension.id) },
            onDismiss = { detailsId = null }
        )
    }
    OutlinedTextField(
        value = address,
        onValueChange = { address = it },
        singleLine = true,
        isError = address.isNotEmpty() && normalized == null,
        label = { Text(stringResource(R.string.ext_url_label)) },
        supportingText = { if (address.isNotEmpty() && normalized == null) Text(stringResource(R.string.ext_url_invalid)) },
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { normalized?.let { onInstall(it); address = "" } },
        enabled = normalized != null,
        shapes = ButtonDefaults.shapes()
    ) { Text(stringResource(R.string.ext_install)) }
    if (installFailed) {
        Text(stringResource(R.string.ext_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    Text(
        stringResource(R.string.ext_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ExtensionPromptDialog(prompt: ExtensionPrompt, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        icon = if (prompt.broadAccess) {
            { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
        } else null,
        title = { Text(stringResource(R.string.ext_prompt_title, prompt.extensionName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ext_prompt_message))
                if (prompt.broadAccess) {
                    Text(
                        stringResource(R.string.ext_perm_all_sites),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                prompt.origins.filterNot { ExtensionRules.hasBroadAccess(listOf(it)) }.forEach {
                    Text(stringResource(R.string.ext_perm_site, it), style = MaterialTheme.typography.bodyMedium)
                }
                prompt.permissions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.ext_allow)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun ExtensionDetailsDialog(
    extension: ExtensionInfo,
    status: ExtensionUpdateStatus?,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(extension.name) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                Text(extension.version, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (extension.description.isNotBlank()) Text(extension.description, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.ext_details_permissions), style = MaterialTheme.typography.titleSmall)
                if (extension.broadAccess) {
                    Text(stringResource(R.string.ext_perm_all_sites), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                extension.origins.filterNot { ExtensionRules.hasBroadAccess(listOf(it)) }.forEach {
                    Text(stringResource(R.string.ext_perm_site, it), style = MaterialTheme.typography.bodyMedium)
                }
                extension.permissions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                if (!extension.broadAccess && extension.origins.isEmpty() && extension.permissions.isEmpty()) {
                    Text(stringResource(R.string.ext_details_none), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.ext_compat_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                status?.let {
                    Text(
                        stringResource(
                            when (it) {
                                ExtensionUpdateStatus.CHECKING -> R.string.ext_update_checking
                                ExtensionUpdateStatus.UPDATED -> R.string.ext_update_done
                                ExtensionUpdateStatus.LATEST -> R.string.ext_update_latest
                                ExtensionUpdateStatus.FAILED -> R.string.ext_update_failed
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (it == ExtensionUpdateStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCheckUpdate, enabled = status != ExtensionUpdateStatus.CHECKING) {
                Text(stringResource(R.string.ext_check_updates))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.onboarding_close)) } }
    )
}
