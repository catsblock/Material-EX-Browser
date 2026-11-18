package com.catsblock.materialex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.catsblock.materialex.core.Resolution
import com.catsblock.materialex.core.SearchTemplates
import com.catsblock.materialex.core.UrlResolver
import com.catsblock.materialex.data.BrowserSettings
import com.catsblock.materialex.ui.BrowserScreen
import com.catsblock.materialex.ui.SettingsScreen
import com.catsblock.materialex.ui.theme.MaterialExTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
                val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = BrowserSettings())
                val pageState by app.engine.state.collectAsStateWithLifecycle()
                var showSettings by rememberSaveable { mutableStateOf(false) }

                BackHandler(enabled = showSettings || pageState.canGoBack) {
                    if (showSettings) showSettings = false else app.engine.goBack()
                }

                if (showSettings) {
                    SettingsScreen(
                        settings = settings,
                        repository = app.settings,
                        onClose = { showSettings = false }
                    )
                } else {
                    BrowserScreen(
                        engine = app.engine,
                        settings = settings,
                        onSubmit = { input -> submit(input, settings) },
                        onOpenSettings = { showSettings = true }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { openFromIntent(intent) }
    }

    private suspend fun openFromIntent(intent: Intent?) {
        val target = intentUrl(intent)
        if (target != null) {
            app.engine.load(target)
            return
        }
        if (app.engine.state.value.url.isEmpty()) {
            app.engine.load(app.settings.settings.first().homepage)
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
