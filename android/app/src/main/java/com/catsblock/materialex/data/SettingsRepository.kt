package com.catsblock.materialex.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.catsblock.materialex.core.Resolution
import com.catsblock.materialex.core.SearchEngine
import com.catsblock.materialex.core.SearchTemplates
import com.catsblock.materialex.core.UrlResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

enum class ToolbarPosition {
    TOP,
    BOTTOM;

    companion object {
        fun fromId(id: String?): ToolbarPosition = entries.firstOrNull { it.name == id } ?: TOP
    }
}

data class BrowserSettings(
    val toolbarPosition: ToolbarPosition = ToolbarPosition.TOP,
    val homepage: String = DEFAULT_HOMEPAGE,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val customSearchTemplate: String = ""
) {
    companion object {
        const val DEFAULT_HOMEPAGE = "https://www.google.com/"
    }
}

class SettingsRepository(private val context: Context) {
    private val toolbarKey = stringPreferencesKey("toolbar_position")
    private val homepageKey = stringPreferencesKey("homepage")
    private val engineKey = stringPreferencesKey("search_engine")
    private val templateKey = stringPreferencesKey("custom_search_template")

    val settings: Flow<BrowserSettings> = context.settingsStore.data.map(::read)

    suspend fun setToolbarPosition(position: ToolbarPosition) {
        context.settingsStore.edit { it[toolbarKey] = position.name }
    }

    suspend fun setHomepage(input: String): Boolean {
        val resolution = UrlResolver.resolve(input)
        if (resolution !is Resolution.Navigate) return false
        context.settingsStore.edit { it[homepageKey] = resolution.url }
        return true
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.settingsStore.edit { it[engineKey] = engine.id }
    }

    suspend fun setCustomSearchTemplate(template: String): Boolean {
        if (!SearchTemplates.isValid(template)) return false
        context.settingsStore.edit { it[templateKey] = template.trim() }
        return true
    }

    private fun read(preferences: Preferences): BrowserSettings = BrowserSettings(
        toolbarPosition = ToolbarPosition.fromId(preferences[toolbarKey]),
        homepage = preferences[homepageKey] ?: BrowserSettings.DEFAULT_HOMEPAGE,
        searchEngine = SearchEngine.fromId(preferences[engineKey]),
        customSearchTemplate = preferences[templateKey].orEmpty()
    )
}
