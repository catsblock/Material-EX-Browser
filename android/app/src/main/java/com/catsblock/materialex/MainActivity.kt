package com.catsblock.materialex

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import com.catsblock.materialex.core.memory.MemoryBudget
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.catsblock.materialex.core.permission.Origin
import com.catsblock.materialex.ui.PermissionPromptSheet
import com.catsblock.materialex.ui.SiteInfoSheet
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.catsblock.materialex.core.Resolution
import com.catsblock.materialex.core.SearchTemplates
import com.catsblock.materialex.core.UrlResolver
import com.catsblock.materialex.data.BrowserSettings
import com.catsblock.materialex.data.LocaleApplier
import com.catsblock.materialex.ui.BookmarksScreen
import androidx.compose.runtime.LaunchedEffect
import com.catsblock.materialex.data.Suggestion
import com.catsblock.materialex.ui.NewTabPage
import kotlinx.coroutines.delay
import com.catsblock.materialex.ui.BrowserScreen
import com.catsblock.materialex.ui.DownloadsScreen
import com.catsblock.materialex.ui.HistoryScreen
import com.catsblock.materialex.ui.OnboardingScreen
import com.catsblock.materialex.ui.ExtensionPromptDialog
import com.catsblock.materialex.ui.SettingsScreen
import com.catsblock.materialex.ui.TabSwitcherScreen
import com.catsblock.materialex.ui.theme.MaterialExTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class Screen { BROWSER, TABS, SETTINGS, HISTORY, BOOKMARKS, DOWNLOADS }

class MainActivity : ComponentActivity() {
    private val app get() = application as MaterialExApp

    private var osCallback: ((Boolean) -> Unit)? = null

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        app.engine.trimMemory(level)
    }

    private val osPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val callback = osCallback
            osCallback = null
            callback?.invoke(results.isNotEmpty() && results.values.all { it })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.engine.androidPermissionRequester = { permissions, callback ->
            osCallback = callback
            osPermissionLauncher.launch(permissions)
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            lifecycleScope.launch { openFromIntent(intent) }
        }
        setContent {
            MaterialExTheme {
                val loaded by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
                val current = loaded
                when {
                    current == null -> Box(Modifier.fillMaxSize())
                    !current.onboardingCompleted -> OnboardingScreen(
                        selectedLanguage = current.language,
                        onLanguageSelected = { tag ->
                            lifecycleScope.launch {
                                app.settings.setLanguage(tag)
                                LocaleApplier.apply(this@MainActivity, tag)
                            }
                        },
                        googleEmail = current.googleEmail,
                        onGoogleConnected = { email -> lifecycleScope.launch { app.settings.setGoogleEmail(email) } },
                        onGoogleDisconnect = { lifecycleScope.launch { app.settings.setGoogleEmail("") } },
                        onFinish = {
                            lifecycleScope.launch { app.settings.completeOnboarding(System.currentTimeMillis()) }
                        }
                    )
                    else -> BrowserHost(current)
                }
            }
        }
    }

    @Composable
    private fun BrowserHost(settings: BrowserSettings) {
        val pageState by app.engine.state.collectAsStateWithLifecycle()
        val isBookmarked by remember(pageState.url) { app.library.isBookmarked(pageState.url) }
            .collectAsStateWithLifecycle(false)
        val shortcuts by remember { app.library.shortcuts() }.collectAsStateWithLifecycle(emptyList())
        val frequent by remember { app.library.frequentSites(6) }.collectAsStateWithLifecycle(emptyList())
        val recent by remember { app.library.recentPages(6) }.collectAsStateWithLifecycle(emptyList())
        val homeBookmarks by remember { app.library.bookmarks("") }.collectAsStateWithLifecycle(emptyList())
        LaunchedEffect(settings.popupBlocking) { app.engine.popupBlocking = settings.popupBlocking }
        LaunchedEffect(settings.safeBrowsing) { app.engine.safeBrowsingEnabled = settings.safeBrowsing }
        LaunchedEffect(settings.cookieBanner) { app.engine.cookieBannerHandling = settings.cookieBanner }
        LaunchedEffect(settings.memoryMode, settings.customLoadedTabs) {
            val info = ActivityManager.MemoryInfo()
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            app.engine.loadedLimit = MemoryBudget.loadedLimit(
                settings.memoryMode,
                settings.customLoadedTabs,
                info.totalMem / (1024L * 1024L)
            )
        }
        val profiles by app.settings.profiles.collectAsStateWithLifecycle(emptyList())
        val activeProfile by app.engine.activeProfile.collectAsStateWithLifecycle()
        val privateActive by remember { app.engine.tabs.map { it.active?.isPrivate == true } }
            .collectAsStateWithLifecycle(false)
        var query by remember { mutableStateOf("") }
        var suggestions by remember { mutableStateOf(emptyList<Suggestion>()) }
        LaunchedEffect(query, settings.suggestionProvider, privateActive) {
            if (query.isBlank() || privateActive) {
                suggestions = emptyList()
            } else {
                delay(SUGGESTION_DEBOUNCE_MS)
                suggestions = app.suggestions.load(query, settings.suggestionProvider, false)
            }
        }
        var screen by rememberSaveable { mutableStateOf(Screen.BROWSER) }
        var siteInfoOpen by rememberSaveable { mutableStateOf(false) }
        val extensions by app.engine.extensions.collectAsStateWithLifecycle()
        val extensionError by app.engine.extensionError.collectAsStateWithLifecycle()
        val extensionPrompt by app.engine.extensionPrompt.collectAsStateWithLifecycle()
        extensionPrompt?.let { ExtensionPromptDialog(it) { allow -> app.engine.answerExtensionPrompt(allow) } }
        val prompt by app.engine.permissions.prompt.collectAsStateWithLifecycle()
        val osBlocked by app.engine.osBlocked.collectAsStateWithLifecycle()

        BackHandler(enabled = screen != Screen.BROWSER || pageState.canGoBack) {
            if (screen != Screen.BROWSER) screen = Screen.BROWSER else app.engine.goBack()
        }

        when (screen) {
            Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                repository = app.settings,
                library = app.library,
                profiles = profiles,
                activeProfileId = activeProfile,
                onSwitchProfile = { app.engine.switchProfile(it) },
                onDeleteProfile = { app.engine.deleteProfile(it) },
                onExitGuest = { app.engine.exitGuest() },
                extensions = extensions,
                extensionInstallFailed = extensionError,
                onInstallExtension = { app.engine.installExtension(it) },
                onToggleExtension = { id, enabled -> app.engine.setExtensionEnabled(id, enabled) },
                onRemoveExtension = { app.engine.removeExtension(it) },
                onClose = { screen = Screen.BROWSER }
            )
            Screen.HISTORY -> HistoryScreen(
                library = app.library,
                onClose = { screen = Screen.BROWSER },
                onOpen = { url ->
                    app.engine.load(url)
                    screen = Screen.BROWSER
                }
            )
            Screen.BOOKMARKS -> BookmarksScreen(
                library = app.library,
                onClose = { screen = Screen.BROWSER },
                onOpen = { url ->
                    app.engine.load(url)
                    screen = Screen.BROWSER
                }
            )
            Screen.DOWNLOADS -> DownloadsScreen(
                controller = app.downloads,
                onClose = { screen = Screen.BROWSER }
            )
            Screen.TABS -> TabSwitcherScreen(
                engine = app.engine,
                layout = settings.tabLayout,
                onClose = { screen = Screen.BROWSER },
                onNewTab = {
                    app.engine.openTab("")
                    screen = Screen.BROWSER
                },
                onCloseTab = { id -> closeTab(id, settings.homepage) }
            )
            Screen.BROWSER -> BrowserScreen(
                engine = app.engine,
                settings = settings,
                suggestions = suggestions,
                onQueryChange = { query = it },
                onPickSuggestion = { text ->
                    query = ""
                    submit(text, settings)
                },
                onDeleteSuggestion = { text ->
                    lifecycleScope.launch {
                        app.library.deleteSearch(text)
                        suggestions = suggestions.filterNot { it.text == text && it.fromHistory }
                    }
                },
                onShare = { share(pageState.url) },
                onOpenDownloads = { screen = Screen.DOWNLOADS },
                newTabContent = {
                    NewTabPage(
                        settings = settings,
                        shortcuts = shortcuts,
                        frequent = frequent,
                        recent = recent,
                        bookmarks = homeBookmarks,
                        onOpen = { url -> app.engine.load(url) },
                        onSaveShortcut = { id, title, url ->
                            lifecycleScope.launch { app.library.saveShortcut(id, title, url) }
                        },
                        onDeleteShortcut = { id -> lifecycleScope.launch { app.library.deleteShortcut(id) } },
                        onMoveShortcut = { id, delta -> lifecycleScope.launch { app.library.moveShortcut(id, delta) } }
                    )
                },
                onSubmit = { input -> submit(input, settings) },
                onOpenTabs = { screen = Screen.TABS },
                isBookmarked = isBookmarked,
                onNewTab = { app.engine.openTab("") },
                onNewPrivateTab = { app.engine.openTab("", private = true) },
                onToggleBookmark = {
                    lifecycleScope.launch {
                        app.library.toggleBookmark(pageState.url, pageState.title, isBookmarked)
                    }
                },
                onOpenHistory = { screen = Screen.HISTORY },
                onOpenBookmarks = { screen = Screen.BOOKMARKS },
                onOpenSettings = { screen = Screen.SETTINGS },
                onOpenSiteInfo = { siteInfoOpen = true }
            )
        }

        prompt?.let { current ->
            PermissionPromptSheet(prompt = current, onChoice = app.engine.permissions::answer)
        }

        val siteOrigin = Origin.normalize(pageState.url)
        if (siteInfoOpen && siteOrigin != null) {
            SiteInfoSheet(
                origin = siteOrigin,
                secure = pageState.url.startsWith("https://") && pageState.isSecure,
                manager = app.permissionManager,
                profileId = "default",
                onDismiss = { siteInfoOpen = false }
            )
        }

        if (osBlocked) {
            AlertDialog(
                onDismissRequest = app.engine::clearOsBlocked,
                text = { Text(stringResource(R.string.perm_os_blocked)) },
                confirmButton = {
                    TextButton(onClick = {
                        app.engine.clearOsBlocked()
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        )
                    }) { Text(stringResource(R.string.perm_open_os_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = app.engine::clearOsBlocked) { Text(stringResource(R.string.onboarding_close)) }
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { openFromIntent(intent) }
    }

    private fun closeTab(id: String, homepage: String) {
        app.engine.closeTab(id)
        app.engine.flushClosed()
        if (app.engine.tabs.value.tabs.isEmpty()) app.engine.openTab(homepage)
    }

    private suspend fun openFromIntent(intent: Intent?) {
        val target = intentUrl(intent)
        if (target != null) {
            app.engine.openTab(target)
            return
        }
        if (app.engine.tabs.value.tabs.isEmpty()) {
            app.engine.openTab(app.settings.settings.first().homepage)
        }
    }

    private fun share(url: String) {
        if (!url.startsWith("http")) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(send, null))
    }

    private fun intentUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.dataString ?: return null
        return (UrlResolver.resolve(data) as? Resolution.Navigate)?.url
    }

    private fun submit(input: String, settings: BrowserSettings): Boolean =
        when (val resolution = UrlResolver.resolve(input)) {
            is Resolution.Navigate -> {
                app.engine.load(resolution.url)
                true
            }
            is Resolution.Search -> {
                if (app.engine.tabs.value.active?.isPrivate != true) lifecycleScope.launch { app.library.recordSearch(resolution.query) }
                val url = SearchTemplates.resolveUrl(settings.searchEngine, settings.customSearchTemplate, resolution.query)
                if (url != null) app.engine.load(url)
                url != null
            }
            Resolution.Rejected -> false
        }

    private companion object {
        const val SUGGESTION_DEBOUNCE_MS = 250L
    }
}
