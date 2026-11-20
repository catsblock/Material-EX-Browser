package com.catsblock.materialex.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import com.catsblock.materialex.core.memory.MemoryMode
import com.catsblock.materialex.core.extension.ExtensionInfo
import com.catsblock.materialex.core.profile.Profile
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.catsblock.materialex.R
import com.catsblock.materialex.core.SearchEngine
import com.catsblock.materialex.core.SuggestionProvider
import com.catsblock.materialex.data.BrowserSettings
import com.catsblock.materialex.data.HomeSection
import com.catsblock.materialex.data.LibraryRepository
import com.catsblock.materialex.data.SettingsRepository
import com.catsblock.materialex.data.TabLayout
import com.catsblock.materialex.data.ToolbarPosition
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: BrowserSettings,
    repository: SettingsRepository,
    library: LibraryRepository,
    profiles: List<Profile>,
    activeProfileId: String,
    onSwitchProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onExitGuest: () -> Unit,
    extensions: List<ExtensionInfo>,
    extensionInstallFailed: Boolean,
    onInstallExtension: (String) -> Unit,
    onToggleExtension: (String, Boolean) -> Unit,
    onRemoveExtension: (String) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var homepage by remember(settings.homepage) { mutableStateOf(settings.homepage) }
    var homepageInvalid by remember { mutableStateOf(false) }
    var template by remember(settings.customSearchTemplate) { mutableStateOf(settings.customSearchTemplate) }
    var templateInvalid by remember { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = category != null) { category = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(category?.title ?: R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (category != null) category = null else onClose() }) {
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
            if (category == null) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_search_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                val needle = query.trim()
                if (needle.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SettingsCategory.entries.forEachIndexed { position, entry ->
                            CategoryRow(
                                title = stringResource(entry.title),
                                shape = groupedShape(position, SettingsCategory.entries.size)
                            ) { category = entry }
                        }
                    }
                } else {
                    val matches = SettingsCategory.index.filter {
                        stringResource(it.first).contains(needle, ignoreCase = true)
                    }
                    if (matches.isEmpty()) {
                        Text(stringResource(R.string.settings_no_results), style = MaterialTheme.typography.bodyLarge)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        matches.forEachIndexed { position, (label, target) ->
                            CategoryRow(
                                title = stringResource(label),
                                subtitle = stringResource(target.title),
                                shape = groupedShape(position, matches.size)
                            ) {
                                query = ""
                                category = target
                            }
                        }
                    }
                }
            }
            if (category == SettingsCategory.GENERAL) {
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
                    Text(stringResource(R.string.settings_home_sections), style = MaterialTheme.typography.titleMedium)
                    SectionSwitch(R.string.settings_show_shortcuts, settings.showShortcuts) {
                        scope.launch { repository.setHomeSection(HomeSection.SHORTCUTS, it) }
                    }
                    SectionSwitch(R.string.settings_show_frequent, settings.showFrequent) {
                        scope.launch { repository.setHomeSection(HomeSection.FREQUENT, it) }
                    }
                    SectionSwitch(R.string.settings_show_bookmarks, settings.showBookmarks) {
                        scope.launch { repository.setHomeSection(HomeSection.BOOKMARKS, it) }
                    }
                    SectionSwitch(R.string.settings_show_recent, settings.showRecent) {
                        scope.launch { repository.setHomeSection(HomeSection.RECENT, it) }
                    }
                    TextButton(onClick = {
                        scope.launch {
                            repository.resetHomeSettings()
                            library.restoreDefaultShortcuts()
                        }
                    }) {
                        Text(stringResource(R.string.settings_restore_home))
                    }
                }
            }
            if (category == SettingsCategory.SEARCH) {
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
                Column {
                    Text(stringResource(R.string.settings_suggestions), style = MaterialTheme.typography.titleMedium)
                    SuggestionProvider.entries.forEach { provider ->
                        ChoiceRow(
                            label = providerLabel(provider),
                            selected = settings.suggestionProvider == provider,
                            onSelect = { scope.launch { repository.setSuggestionProvider(provider) } }
                        )
                    }
                    TextButton(onClick = { scope.launch { library.clearSearches() } }) {
                        Text(stringResource(R.string.settings_clear_searches))
                    }
                }
            }
            if (category == SettingsCategory.APPEARANCE) {
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
            }
            if (category == SettingsCategory.PRIVACY) {
                SectionSwitch(R.string.settings_popup_block, settings.popupBlocking) {
                    scope.launch { repository.setPopupBlocking(it) }
                }
                SectionSwitch(R.string.settings_safe_browsing, settings.safeBrowsing) {
                    scope.launch { repository.setSafeBrowsing(it) }
                }
                SectionSwitch(R.string.settings_cookie_banner, settings.cookieBanner) {
                    scope.launch { repository.setCookieBanner(it) }
                }
            }
            if (category == SettingsCategory.PERFORMANCE) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_memory_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.settings_memory_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemoryMode.entries.forEach { mode ->
                            ToggleButton(
                                checked = settings.memoryMode == mode,
                                onCheckedChange = { scope.launch { repository.setMemoryMode(mode) } },
                                shapes = ToggleButtonDefaults.shapes()
                            ) { Text(stringResource(memoryModeLabel(mode))) }
                        }
                    }
                    if (settings.memoryMode == MemoryMode.CUSTOM) {
                        Text(
                            stringResource(R.string.settings_memory_custom, settings.customLoadedTabs),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        var sliderValue by remember(settings.customLoadedTabs) { mutableFloatStateOf(settings.customLoadedTabs.toFloat()) }
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = { scope.launch { repository.setCustomLoadedTabs(sliderValue.toInt()) } },
                            valueRange = 1f..30f,
                            steps = 28
                        )
                    }
                }
            }
            if (category == SettingsCategory.TABS) {
                Column {
                    Text(stringResource(R.string.settings_tab_layout), style = MaterialTheme.typography.titleMedium)
                    TabLayout.entries.forEach { layout ->
                        ChoiceRow(
                            label = stringResource(
                                when (layout) {
                                    TabLayout.GRID -> R.string.tab_layout_grid
                                    TabLayout.LIST -> R.string.tab_layout_list
                                    TabLayout.COVER_FLOW -> R.string.tab_layout_cover
                                }
                            ),
                            selected = settings.tabLayout == layout,
                            onSelect = { scope.launch { repository.setTabLayout(layout) } }
                        )
                    }
                }
            }
            if (category == SettingsCategory.PROFILES) {
                ProfilesSection(
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    repository = repository,
                    onSwitch = onSwitchProfile,
                    onDelete = onDeleteProfile,
                    onExitGuest = onExitGuest
                )
            }
            if (category == SettingsCategory.EXTENSIONS) {
                ExtensionsSection(
                    extensions = extensions,
                    installFailed = extensionInstallFailed,
                    onInstall = onInstallExtension,
                    onToggle = onToggleExtension,
                    onRemove = onRemoveExtension
                )
            }
            if (category == SettingsCategory.ABOUT) {
                val context = LocalContext.current
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                }
                Text(stringResource(R.string.settings_about_version, version), style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun providerLabel(provider: SuggestionProvider): String = when (provider) {
    SuggestionProvider.NONE -> stringResource(R.string.suggestion_provider_none)
    SuggestionProvider.DUCKDUCKGO -> "DuckDuckGo"
    SuggestionProvider.GOOGLE -> "Google"
    SuggestionProvider.BRAVE -> "Brave Search"
    SuggestionProvider.ECOSIA -> "Ecosia"
    SuggestionProvider.KAGI -> "Kagi"
}

private fun memoryModeLabel(mode: MemoryMode): Int = when (mode) {
    MemoryMode.AUTOMATIC -> R.string.settings_memory_automatic
    MemoryMode.LOW -> R.string.settings_memory_low
    MemoryMode.BALANCED -> R.string.settings_memory_balanced
    MemoryMode.HIGH -> R.string.settings_memory_high
    MemoryMode.CUSTOM -> R.string.settings_memory_custom_mode
}

@Composable
private fun SectionSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}


private enum class SettingsCategory(val title: Int) {
    GENERAL(R.string.settings_cat_general),
    SEARCH(R.string.settings_cat_search),
    APPEARANCE(R.string.settings_cat_appearance),
    PRIVACY(R.string.settings_cat_privacy),
    PERFORMANCE(R.string.settings_cat_performance),
    TABS(R.string.settings_cat_tabs),
    PROFILES(R.string.settings_cat_profiles),
    EXTENSIONS(R.string.settings_cat_extensions),
    ABOUT(R.string.settings_cat_about);

    companion object {
        val index: List<Pair<Int, SettingsCategory>> = listOf(
            R.string.settings_homepage to GENERAL,
            R.string.settings_home_sections to GENERAL,
            R.string.settings_search_engine to SEARCH,
            R.string.settings_suggestions to SEARCH,
            R.string.settings_toolbar_position to APPEARANCE,
            R.string.settings_frame_autohide to APPEARANCE,
            R.string.settings_popup_block to PRIVACY,
            R.string.settings_safe_browsing to PRIVACY,
            R.string.settings_cookie_banner to PRIVACY,
            R.string.settings_memory_title to PERFORMANCE,
            R.string.settings_tab_layout to TABS,
            R.string.profile_add to PROFILES,
            R.string.profile_guest_start to PROFILES,
            R.string.ext_install to EXTENSIONS
        )
    }
}

@Composable
private fun CategoryRow(
    title: String,
    shape: androidx.compose.ui.graphics.Shape,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
