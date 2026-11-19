package com.catsblock.materialex.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R
import com.catsblock.materialex.core.permission.Origin
import com.catsblock.materialex.core.permission.PermissionManager
import com.catsblock.materialex.core.permission.PermissionPolicy
import com.catsblock.materialex.core.permission.PermissionPrompt
import com.catsblock.materialex.core.permission.PermissionRecord
import com.catsblock.materialex.core.permission.PermissionState
import com.catsblock.materialex.core.permission.PermissionType
import com.catsblock.materialex.core.permission.PromptChoice
import kotlinx.coroutines.launch

@StringRes
fun PermissionType.labelRes(): Int = when (this) {
    PermissionType.CAMERA -> R.string.perm_camera
    PermissionType.MICROPHONE -> R.string.perm_microphone
    PermissionType.LOCATION -> R.string.perm_location
    PermissionType.NOTIFICATIONS -> R.string.perm_notifications
    PermissionType.PERSISTENT_STORAGE -> R.string.perm_persistent_storage
}

@StringRes
private fun PermissionType.explanationRes(): Int = when (this) {
    PermissionType.CAMERA -> R.string.perm_explain_camera
    PermissionType.MICROPHONE -> R.string.perm_explain_microphone
    PermissionType.LOCATION -> R.string.perm_explain_location
    PermissionType.NOTIFICATIONS -> R.string.perm_explain_notifications
    PermissionType.PERSISTENT_STORAGE -> R.string.perm_explain_storage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionPromptSheet(prompt: PermissionPrompt, onChoice: (PromptChoice) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onChoice(PromptChoice.ALWAYS_ASK) }) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.perm_prompt_title,
                    Origin.host(prompt.origin),
                    stringResource(prompt.type.labelRes())
                ),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(prompt.type.explanationRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.perm_allow_scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { onChoice(PromptChoice.ALWAYS_ASK) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.perm_always_ask))
            }
            FilledTonalButton(onClick = { onChoice(PromptChoice.DENY) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.perm_deny))
            }
            Button(onClick = { onChoice(PromptChoice.ALLOW) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.perm_allow))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoSheet(
    origin: String,
    secure: Boolean,
    manager: PermissionManager,
    profileId: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<Map<PermissionType, PermissionRecord>>(emptyMap()) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(origin, version) { records = manager.overview(profileId, origin) }

    fun change(block: suspend () -> Unit) {
        scope.launch {
            block()
            version += 1
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.site_info_title), style = MaterialTheme.typography.headlineSmall)
            Text(Origin.host(origin), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(if (secure) R.string.security_secure else R.string.security_insecure),
                style = MaterialTheme.typography.bodyMedium,
                color = if (secure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.site_info_permissions),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            PermissionType.entries.forEach { type ->
                val record = records[type] ?: PermissionRecord()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(type.labelRes()), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            PermissionState.ASK to R.string.perm_state_ask,
                            PermissionState.ALLOW to R.string.perm_state_allow,
                            PermissionState.DENY to R.string.perm_state_deny
                        ).forEach { (state, label) ->
                            FilterChip(
                                selected = record.state == state,
                                onClick = { change { manager.setState(profileId, origin, type, state) } },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                    if (record.state == PermissionState.AUTO_BLOCKED) {
                        Text(
                            text = stringResource(R.string.perm_state_blocked),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (record.denialCount > 0) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.perm_denials, record.denialCount, PermissionPolicy.AUTO_BLOCK_THRESHOLD),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { change { manager.resetCounter(profileId, origin, type) } }) {
                                Text(stringResource(R.string.perm_reset_block))
                            }
                        }
                    }
                }
            }
        }
    }
}
