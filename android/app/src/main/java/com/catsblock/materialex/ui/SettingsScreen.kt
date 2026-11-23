package com.catsblock.materialex.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.catsblock.materialex.core.TextScale
import com.catsblock.materialex.core.extension.ExtensionUpdateStatus
import com.catsblock.materialex.core.settings.SearchEntry
import com.catsblock.materialex.core.settings.SettingsSearch
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
    extensionUpdates: Map<String, ExtensionUpdateStatus>,
    onCheckExtensionUpdate: (String) -> Unit,
    onClearData: suspend (com.catsblock.materialex.core.ClearSelection) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var homepage by remember(settings.homepage) { mutableStateOf(settings.homepage) }
    var homepageInvalid by remember { mutableStateOf(false) }
    var template by remember(settings.customSearchTemplate) { mutableStateOf(settings.customSearchTemplate) }
    var templateInvalid by remember { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<SettingsCategory?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val desktop = rememberDesktopLayout()
    val shown = category ?: if (desktop) SettingsCategory.GENERAL else null
    BackHandler(enabled = category != null && !desktop) { category = null }

    var legalDoc by rememberSaveable { mutableStateOf<String?>(null) }
    legalDoc?.let { name ->
        BackHandler { legalDoc = null }
        LegalDocumentViewer(doc = LegalDoc.valueOf(name), onClose = { legalDoc = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(shown?.title ?: R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (category != null && !desktop) category = null else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Row(modifier = Modifier.padding(padding)) {
        if (desktop) {
            CategoryPane(
                selected = shown,
                query = query,
                onQueryChange = { query = it },
                onSelect = {
                    query = ""
                    category = it
                }
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            if (shown == null) {
                SettingsSearchField(query) { query = it }
                val needle = query.trim()
                if (needle.isEmpty()) {
                    SettingsCategory.groups.forEach { (header, entries) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(header),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                            )
                            entries.forEachIndexed { position, entry ->
                                CategoryRow(
                                    title = stringResource(entry.title),
                                    subtitle = stringResource(entry.summary),
                                    icon = entry.icon,
                                    shape = groupedShape(position, entries.size)
                                ) { category = entry }
                            }
                        }
                    }
                } else {
                    val matches = settingsMatches(needle)
                    if (matches.isEmpty()) {
                        Text(stringResource(R.string.settings_no_results), style = MaterialTheme.typography.bodyLarge)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        matches.forEachIndexed { position, (label, target) ->
                            CategoryRow(
                                title = stringResource(label),
                                subtitle = stringResource(target.title),
                                icon = target.icon,
                                shape = groupedShape(position, matches.size)
                            ) {
                                query = ""
                                category = target
                            }
                        }
                    }
                }
            }
            if (shown == SettingsCategory.GENERAL) {
                SettingsSection(R.string.settings_homepage) {
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
                SettingsSection(R.string.settings_home_sections) {
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
            if (shown == SettingsCategory.SEARCH) {
                SettingsSection(R.string.settings_search_engine) {
                    SearchEngine.entries.forEach { engine ->
                        ChoiceRow(
                            label = engineLabel(engine),
                            selected = settings.searchEngine == engine,
                            onSelect = { scope.launch { repository.setSearchEngine(engine) } }
                        )
                    }
                }
                if (settings.searchEngine == SearchEngine.CUSTOM) {
                    SettingsSection(R.string.settings_custom_template) {
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
                SettingsSection(R.string.settings_suggestions) {
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
            if (shown == SettingsCategory.APPEARANCE) {
                SettingsSection(R.string.settings_toolbar_position) {
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
                SettingsSection(null) {
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
                SettingsSection(R.string.settings_web_display) {
                    SectionSwitch(R.string.settings_force_zoom, settings.forceZoom, R.string.settings_force_zoom_desc) {
                        scope.launch { repository.setForceZoom(it) }
                    }
                    SectionSwitch(R.string.settings_text_follow_system, settings.textFollowSystem, R.string.settings_text_follow_system_desc) {
                        scope.launch { repository.setTextFollowSystem(it) }
                    }
                    Text(
                        stringResource(R.string.settings_text_scale) + ": " + settings.textScalePercent + "%",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        stringResource(R.string.settings_text_scale_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.textScalePercent.toFloat(),
                        onValueChange = { scope.launch { repository.setTextScale(it.toInt()) } },
                        valueRange = TextScale.MIN.toFloat()..TextScale.MAX.toFloat(),
                        steps = (TextScale.MAX - TextScale.MIN) / TextScale.STEP - 1,
                        enabled = !settings.textFollowSystem
                    )
                }
            }
            if (shown == SettingsCategory.PRIVACY) {
                SettingsSection(null) {
                    SectionSwitch(R.string.settings_popup_block, settings.popupBlocking, R.string.settings_popup_block_desc) {
                        scope.launch { repository.setPopupBlocking(it) }
                    }
                    SectionSwitch(R.string.settings_safe_browsing, settings.safeBrowsing, R.string.settings_safe_browsing_desc) {
                        scope.launch { repository.setSafeBrowsing(it) }
                    }
                    SectionSwitch(R.string.settings_cookie_banner, settings.cookieBanner, R.string.settings_cookie_banner_desc) {
                        scope.launch { repository.setCookieBanner(it) }
                    }
                }
                ClearDataSection(onClear = onClearData)
            }
            if (shown == SettingsCategory.PERFORMANCE) {
                SettingsSection(R.string.settings_memory_title) {
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
            if (shown == SettingsCategory.TABS) {
                SettingsSection(R.string.settings_tab_layout) {
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
            if (shown == SettingsCategory.PROFILES) {
                ProfilesSection(
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    repository = repository,
                    onSwitch = onSwitchProfile,
                    onDelete = onDeleteProfile,
                    onExitGuest = onExitGuest
                )
            }
            if (shown == SettingsCategory.EXTENSIONS) {
                ExtensionsSection(
                    extensions = extensions,
                    installFailed = extensionInstallFailed,
                    onInstall = onInstallExtension,
                    onToggle = onToggleExtension,
                    onRemove = onRemoveExtension,
                    updateStatus = extensionUpdates,
                    onCheckUpdate = onCheckExtensionUpdate
                )
            }
            if (shown == SettingsCategory.ABOUT) {
                val context = LocalContext.current
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
                }
                SettingsSection(null) {
                    Text(stringResource(R.string.settings_about_version, version), style = MaterialTheme.typography.bodyLarge)
                }
                SettingsSection(R.string.settings_legal_title) {
                    LegalDoc.entries.forEach { doc ->
                        TextButton(onClick = { legalDoc = doc.name }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(doc.title), modifier = Modifier.fillMaxWidth())
                        }
                    }
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
private fun SectionSwitch(label: Int, checked: Boolean, description: Int? = null, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}


private enum class SettingsCategory(val title: Int, val summary: Int, val icon: ImageVector) {
    GENERAL(R.string.settings_cat_general, R.string.settings_sum_general, Icons.Default.Home),
    SEARCH(R.string.settings_cat_search, R.string.settings_sum_search, Icons.Default.Search),
    APPEARANCE(R.string.settings_cat_appearance, R.string.settings_sum_appearance, Icons.Default.Edit),
    PRIVACY(R.string.settings_cat_privacy, R.string.settings_sum_privacy, Icons.Default.Lock),
    PERFORMANCE(R.string.settings_cat_performance, R.string.settings_sum_performance, Icons.Default.Settings),
    TABS(R.string.settings_cat_tabs, R.string.settings_sum_tabs, Icons.AutoMirrored.Filled.List),
    PROFILES(R.string.settings_cat_profiles, R.string.settings_sum_profiles, Icons.Default.Person),
    EXTENSIONS(R.string.settings_cat_extensions, R.string.settings_sum_extensions, Icons.Default.Add),
    ABOUT(R.string.settings_cat_about, R.string.settings_sum_about, Icons.Default.Info);

    companion object {
        val index: List<Pair<Int, SettingsCategory>> = listOf(
            R.string.settings_homepage to GENERAL,
            R.string.settings_home_sections to GENERAL,
            R.string.settings_restore_home to GENERAL,
            R.string.settings_search_engine to SEARCH,
            R.string.settings_custom_template to SEARCH,
            R.string.settings_suggestions to SEARCH,
            R.string.settings_clear_searches to SEARCH,
            R.string.settings_toolbar_position to APPEARANCE,
            R.string.settings_frame_autohide to APPEARANCE,
            R.string.settings_force_zoom to APPEARANCE,
            R.string.settings_text_follow_system to APPEARANCE,
            R.string.settings_text_scale to APPEARANCE,
            R.string.settings_popup_block to PRIVACY,
            R.string.settings_safe_browsing to PRIVACY,
            R.string.settings_cookie_banner to PRIVACY,
            R.string.settings_memory_title to PERFORMANCE,
            R.string.settings_tab_layout to TABS,
            R.string.profile_add to PROFILES,
            R.string.profile_guest_start to PROFILES,
            R.string.ext_install to EXTENSIONS
        )

        val groups: List<Pair<Int, List<SettingsCategory>>> = listOf(
            R.string.settings_group_navigation to listOf(GENERAL, SEARCH),
            R.string.settings_group_browser to listOf(APPEARANCE, TABS, PERFORMANCE),
            R.string.settings_group_protection to listOf(PRIVACY, PROFILES),
            R.string.settings_group_more to listOf(EXTENSIONS, ABOUT)
        )
    }
}

@Composable
private fun CategoryPane(
    selected: SettingsCategory?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (SettingsCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SettingsSearchField(query, onQueryChange)
        Spacer(Modifier.height(10.dp))
        val needle = query.trim()
        if (needle.isEmpty()) {
            SettingsCategory.entries.forEachIndexed { position, entry ->
                CategoryRow(
                    title = stringResource(entry.title),
                    icon = entry.icon,
                    shape = groupedShape(position, SettingsCategory.entries.size),
                    selected = entry == selected
                ) { onSelect(entry) }
            }
        } else {
            val matches = settingsMatches(needle)
            if (matches.isEmpty()) {
                Text(stringResource(R.string.settings_no_results), style = MaterialTheme.typography.bodyLarge)
            }
            matches.forEachIndexed { position, (label, target) ->
                CategoryRow(
                    title = stringResource(label),
                    subtitle = stringResource(target.title),
                    icon = target.icon,
                    shape = groupedShape(position, matches.size)
                ) { onSelect(target) }
            }
        }
    }
}

@Composable
private fun settingsMatches(needle: String): List<Pair<Int, SettingsCategory>> {
    val entries = SettingsCategory.index.map { (label, target) ->
        SearchEntry(
            title = stringResource(label),
            description = stringResource(target.summary),
            category = stringResource(target.title),
            payload = label to target
        )
    }
    return SettingsSearch.results(needle, entries).map { it.payload }
}

@Composable
private fun CategoryRow(
    title: String,
    shape: androidx.compose.ui.graphics.Shape,
    subtitle: String? = null,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.settings_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun SettingsSection(title: Int?, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}
