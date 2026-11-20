package com.catsblock.materialex.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R
import com.catsblock.materialex.core.profile.Profile
import com.catsblock.materialex.core.profile.ProfileRules
import com.catsblock.materialex.data.SettingsRepository
import kotlinx.coroutines.launch

private sealed interface ProfileDialog {
    data object Add : ProfileDialog
    data class Rename(val profile: Profile) : ProfileDialog
    data class Delete(val profile: Profile) : ProfileDialog
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfilesSection(
    profiles: List<Profile>,
    activeProfileId: String,
    repository: SettingsRepository,
    onSwitch: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExitGuest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    val inGuest = activeProfileId == ProfileRules.GUEST_ID
    val defaultProfile = Profile(ProfileRules.DEFAULT_ID, stringResource(R.string.profile_default_name))
    val rows = listOf(defaultProfile) + profiles

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, profile ->
            val selected = activeProfileId == profile.id
            Surface(
                onClick = { onSwitch(profile.id) },
                shape = groupedShape(index, rows.size),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        if (selected && !inGuest) {
                            Text(
                                stringResource(R.string.profile_active),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (selected && !inGuest) Icon(Icons.Default.Check, contentDescription = null)
                    if (ProfileRules.canDelete(profile.id)) {
                        IconButton(
                            onClick = { dialog = ProfileDialog.Rename(profile) },
                            shapes = IconButtonDefaults.shapes()
                        ) { Icon(Icons.Default.Create, contentDescription = stringResource(R.string.action_rename)) }
                        IconButton(
                            onClick = { dialog = ProfileDialog.Delete(profile) },
                            shapes = IconButtonDefaults.shapes()
                        ) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }
    Button(
        onClick = { dialog = ProfileDialog.Add },
        enabled = profiles.size < ProfileRules.MAX_PROFILES,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.padding(top = 8.dp)
    ) { Text(stringResource(R.string.profile_add)) }

    Text(
        stringResource(R.string.profile_isolation_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
    Text(
        stringResource(R.string.profile_guest_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FilledTonalButton(
        onClick = { if (inGuest) onExitGuest() else onSwitch(ProfileRules.GUEST_ID) },
        shapes = ButtonDefaults.shapes()
    ) {
        Text(stringResource(if (inGuest) R.string.profile_guest_exit else R.string.profile_guest_start))
    }

    when (val current = dialog) {
        is ProfileDialog.Add -> NameDialog(
            title = stringResource(R.string.profile_add),
            initial = "",
            existing = profiles,
            exceptId = null,
            onDismiss = { dialog = null },
            onConfirm = { name -> scope.launch { repository.addProfile(name) }; dialog = null }
        )
        is ProfileDialog.Rename -> NameDialog(
            title = stringResource(R.string.action_rename),
            initial = current.profile.name,
            existing = profiles,
            exceptId = current.profile.id,
            onDismiss = { dialog = null },
            onConfirm = { name -> scope.launch { repository.renameProfile(current.profile.id, name) }; dialog = null }
        )
        is ProfileDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_message, current.profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(current.profile.id)
                    scope.launch { repository.removeProfile(current.profile.id) }
                    dialog = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
        null -> Unit
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    existing: List<Profile>,
    exceptId: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val normalized = ProfileRules.normalizeName(text)
    val valid = normalized != null && !ProfileRules.nameTaken(existing, normalized, exceptId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                label = { Text(stringResource(R.string.profile_name_label)) },
                supportingText = { if (text.isNotEmpty() && !valid) Text(stringResource(R.string.profile_name_invalid)) }
            )
        },
        confirmButton = {
            TextButton(onClick = { normalized?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

fun groupedShape(index: Int, count: Int): RoundedCornerShape {
    val large = 28.dp
    val small = 4.dp
    return when {
        count == 1 -> RoundedCornerShape(large)
        index == 0 -> RoundedCornerShape(large, large, small, small)
        index == count - 1 -> RoundedCornerShape(small, small, large, large)
        else -> RoundedCornerShape(small)
    }
}
