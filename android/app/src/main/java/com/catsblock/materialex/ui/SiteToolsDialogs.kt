package com.catsblock.materialex.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catsblock.materialex.R
import com.catsblock.materialex.core.siteedit.CssIssue
import com.catsblock.materialex.core.siteedit.UaChoice
import com.catsblock.materialex.core.siteedit.UaMode
import com.catsblock.materialex.core.siteedit.UserAgentRules
import com.catsblock.materialex.engine.SiteStyleBridge
import kotlinx.coroutines.launch

private fun issueText(issue: CssIssue): Int = when (issue) {
    CssIssue.TOO_LARGE -> R.string.site_editor_error_size
    CssIssue.UNBALANCED_BRACES -> R.string.site_editor_error_braces
    CssIssue.IMPORT_NOT_ALLOWED -> R.string.site_editor_error_import
}

@Composable
fun SiteEditorDialog(
    host: String,
    bridge: SiteStyleBridge,
    isPrivate: Boolean,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var css by remember(host) { mutableStateOf(bridge.styles.value[host].orEmpty()) }
    var issue by remember { mutableStateOf<CssIssue?>(null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.action_site_editor), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.site_editor_hint, host),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPrivate) {
                    Text(
                        stringResource(R.string.site_editor_private_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = css,
                    onValueChange = {
                        css = it
                        issue = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    isError = issue != null,
                    supportingText = issue?.let { { Text(stringResource(issueText(it))) } }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { issue = bridge.preview(host, css) }) {
                        Text(stringResource(R.string.site_editor_preview))
                    }
                    Button(onClick = {
                        scope.launch {
                            issue = bridge.save(host, css)
                            if (issue == null) onDismiss()
                        }
                    }) { Text(stringResource(R.string.action_save)) }
                    OutlinedButton(onClick = {
                        scope.launch {
                            bridge.reset(host)
                            css = ""
                            issue = null
                        }
                    }) { Text(stringResource(R.string.site_editor_reset)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
fun UserAgentDialog(
    host: String,
    current: UaChoice,
    onApply: (UaChoice) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember(host) { mutableStateOf(current.mode) }
    var custom by remember(host) { mutableStateOf(current.custom) }
    var invalid by remember { mutableStateOf(false) }
    val options = listOf(
        UaMode.DEFAULT to R.string.user_agent_default,
        UaMode.DESKTOP to R.string.user_agent_desktop,
        UaMode.MOBILE to R.string.user_agent_mobile,
        UaMode.CUSTOM to R.string.user_agent_custom
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.user_agent_title, host)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(Modifier.selectableGroup()) {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = mode == value, role = Role.RadioButton, onClick = {
                                    mode = value
                                    invalid = false
                                })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mode == value, onClick = null)
                            Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                if (mode == UaMode.CUSTOM) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = {
                            custom = it
                            invalid = false
                        },
                        label = { Text(stringResource(R.string.user_agent_custom_label)) },
                        isError = invalid,
                        supportingText = if (invalid) {
                            { Text(stringResource(R.string.user_agent_error)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (mode == UaMode.CUSTOM && !UserAgentRules.validateCustom(custom)) {
                    invalid = true
                } else {
                    onApply(UaChoice(mode, if (mode == UaMode.CUSTOM) custom.trim() else ""))
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
