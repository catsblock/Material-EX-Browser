package com.catsblock.materialex.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R
import com.catsblock.materialex.core.ClearSelection
import kotlinx.coroutines.launch

@Composable
internal fun ClearDataSection(onClear: suspend (ClearSelection) -> Unit) {
    var selection by remember { mutableStateOf(ClearSelection()) }
    var confirm by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SettingsSection(R.string.settings_clear_data_title) {
        ClearOption(R.string.settings_clear_history, selection.history) { selection = selection.copy(history = it); done = false }
        ClearOption(R.string.settings_clear_cookies, selection.cookies) { selection = selection.copy(cookies = it); done = false }
        ClearOption(R.string.settings_clear_cache, selection.cache) { selection = selection.copy(cache = it); done = false }
        ClearOption(R.string.settings_clear_opt_searches, selection.searches) { selection = selection.copy(searches = it); done = false }
        ClearOption(R.string.settings_clear_downloads, selection.downloads) { selection = selection.copy(downloads = it); done = false }
        ClearOption(R.string.settings_clear_permissions, selection.permissions) { selection = selection.copy(permissions = it); done = false }
        Button(onClick = { confirm = true }, enabled = !selection.isEmpty, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_clear_action))
        }
        if (done) {
            Text(
                stringResource(R.string.settings_clear_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = selection
                    confirm = false
                    scope.launch {
                        onClear(chosen)
                        selection = ClearSelection()
                        done = true
                    }
                }) { Text(stringResource(R.string.settings_clear_action)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun ClearOption(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}
