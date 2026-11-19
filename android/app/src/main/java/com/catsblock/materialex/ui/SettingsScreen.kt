package com.catsblock.materialex.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.catsblock.materialex.core.SearchEngine
import com.catsblock.materialex.data.BrowserSettings
import com.catsblock.materialex.data.SettingsRepository
import com.catsblock.materialex.data.ToolbarPosition
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: BrowserSettings,
    repository: SettingsRepository,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var homepage by remember(settings.homepage) { mutableStateOf(settings.homepage) }
    var homepageInvalid by remember { mutableStateOf(false) }
    var template by remember(settings.customSearchTemplate) { mutableStateOf(settings.customSearchTemplate) }
    var templateInvalid by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Column {
                Text(stringResource(R.string.settings_toolbar_position), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_toolbar_position_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToolbarPosition.entries.forEach { position ->
                    val label = when (position) {
                        ToolbarPosition.TOP -> stringResource(R.string.settings_toolbar_top)
                        ToolbarPosition.BOTTOM -> stringResource(R.string.settings_toolbar_bottom)
                    }
                    ChoiceRow(
                        label = label,
                        selected = settings.toolbarPosition == position,
                        onSelect = { scope.launch { repository.setToolbarPosition(position) } }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.autoHideFrame,
                        role = Role.Switch,
                        onValueChange = { scope.launch { repository.setAutoHideFrame(it) } }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_frame_autohide), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_frame_autohide_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = settings.autoHideFrame, onCheckedChange = null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_homepage), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = homepage,
                    onValueChange = {
                        homepage = it
                        homepageInvalid = false
                    },
                    singleLine = true,
                    isError = homepageInvalid,
                    supportingText = if (homepageInvalid) {
                        { Text(stringResource(R.string.settings_homepage_invalid)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    scope.launch { homepageInvalid = !repository.setHomepage(homepage) }
                }) {
                    Text(stringResource(R.string.action_save))
                }
            }
            Column {
                Text(stringResource(R.string.settings_search_engine), style = MaterialTheme.typography.titleMedium)
                SearchEngine.entries.forEach { engine ->
                    ChoiceRow(
                        label = engineLabel(engine),
                        selected = settings.searchEngine == engine,
                        onSelect = { scope.launch { repository.setSearchEngine(engine) } }
                    )
                }
            }
            if (settings.searchEngine == SearchEngine.CUSTOM) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_custom_template), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = template,
                        onValueChange = {
                            template = it
                            templateInvalid = false
                        },
                        singleLine = true,
                        isError = templateInvalid,
                        placeholder = { Text(stringResource(R.string.settings_custom_template_hint)) },
                        supportingText = if (templateInvalid) {
                            { Text(stringResource(R.string.settings_custom_template_invalid)) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        scope.launch { templateInvalid = !repository.setCustomSearchTemplate(template) }
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun engineLabel(engine: SearchEngine): String = when (engine) {
    SearchEngine.GOOGLE -> "Google"
    SearchEngine.DUCKDUCKGO -> "DuckDuckGo"
    SearchEngine.BING -> "Bing"
    SearchEngine.BRAVE -> "Brave Search"
    SearchEngine.ECOSIA -> "Ecosia"
    SearchEngine.CHATGPT -> "ChatGPT"
    SearchEngine.QWANT -> "Qwant"
    SearchEngine.KAGI -> "Kagi"
    SearchEngine.CUSTOM -> stringResource(R.string.settings_search_engine_custom)
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
