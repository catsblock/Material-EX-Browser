package com.catsblock.materialex

import android.content.Intent
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
import com.catsblock.materialex.ui.BrowserScreen
import com.catsblock.materialex.ui.HistoryScreen
import com.catsblock.materialex.ui.OnboardingScreen
import com.catsblock.materialex.ui.SettingsScreen
import com.catsblock.materialex.ui.TabSwitcherScreen
import com.catsblock.materialex.ui.theme.MaterialExTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Screen { BROWSER, TABS, SETTINGS, HISTORY, BOOKMARKS }

class MainActivity : ComponentActivity() {
    private val app get() = application as MaterialExApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        var screen by rememberSaveable { mutableStateOf(Screen.BROWSER) }

        BackHandler(enabled = screen != Screen.BROWSER || pageState.canGoBack) {
            if (screen != Screen.BROWSER) screen = Screen.BROWSER else app.engine.goBack()
        }

        when (screen) {
            Screen.SETTINGS -> SettingsScreen(
                settings = settings,
                repository = app.settings,
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
            Screen.TABS -> TabSwitcherScreen(
                engine = app.engine,
                onClose = { screen = Screen.BROWSER },
                onNewTab = {
                    app.engine.openTab(settings.homepage)
                    screen = Screen.BROWSER
                },
                onCloseTab = { id -> closeTab(id, settings.homepage) }
            )
            Screen.BROWSER -> BrowserScreen(
                engine = app.engine,
                settings = settings,
                onSubmit = { input -> submit(input, settings) },
                onOpenTabs = { screen = Screen.TABS },
                isBookmarked = isBookmarked,
                onNewTab = { app.engine.openTab(settings.homepage) },
                onToggleBookmark = {
                    lifecycleScope.launch {
                        app.library.toggleBookmark(pageState.url, pageState.title, isBookmarked)
                    }
                },
                onOpenHistory = { screen = Screen.HISTORY },
                onOpenBookmarks = { screen = Screen.BOOKMARKS },
                onOpenSettings = { screen = Screen.SETTINGS }
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
                val url = SearchTemplates.resolveUrl(settings.searchEngine, settings.customSearchTemplate, resolution.query)
                if (url != null) app.engine.load(url)
                url != null
            }
            Resolution.Rejected -> false
        }
}
