package com.catsblock.materialex.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.catsblock.materialex.core.Resolution
import com.catsblock.materialex.core.memory.MemoryMode
import com.catsblock.materialex.core.profile.Profile
import com.catsblock.materialex.core.profile.ProfileRules
import com.catsblock.materialex.core.SearchEngine
import com.catsblock.materialex.core.SearchTemplates
import com.catsblock.materialex.core.SuggestionProvider
import com.catsblock.materialex.core.UrlResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

enum class TabLayout {
    GRID,
    LIST,
    COVER_FLOW;

    companion object {
        fun fromId(id: String?): TabLayout = entries.firstOrNull { it.name == id } ?: GRID
    }
}

enum class HomeSection {
    SHORTCUTS,
    FREQUENT,
    RECENT,
    BOOKMARKS
}

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
    val customSearchTemplate: String = "",
    val onboardingCompleted: Boolean = false,
    val language: String = "",
    val legalVersion: Int = 0,
    val legalAcceptedAt: Long = 0L,
    val googleEmail: String = "",
    val autoHideFrame: Boolean = true,
    val suggestionProvider: SuggestionProvider = SuggestionProvider.DUCKDUCKGO,
    val showShortcuts: Boolean = true,
    val showFrequent: Boolean = true,
    val showRecent: Boolean = true,
    val showBookmarks: Boolean = true,
    val tabLayout: TabLayout = TabLayout.GRID,
    val popupBlocking: Boolean = true,
    val memoryMode: MemoryMode = MemoryMode.AUTOMATIC,
    val customLoadedTabs: Int = 5,
    val safeBrowsing: Boolean = true,
    val cookieBanner: Boolean = true
) {
    companion object {
        const val DEFAULT_HOMEPAGE = "https://www.google.com/"
        const val LEGAL_VERSION = 1
    }
}

class SettingsRepository(private val context: Context) {
    private val toolbarKey = stringPreferencesKey("toolbar_position")
    private val homepageKey = stringPreferencesKey("homepage")
    private val engineKey = stringPreferencesKey("search_engine")
    private val templateKey = stringPreferencesKey("custom_search_template")
    private val onboardingKey = booleanPreferencesKey("onboarding_completed")
    private val languageKey = stringPreferencesKey("app_language")
    private val legalVersionKey = intPreferencesKey("legal_version")
    private val googleEmailKey = stringPreferencesKey("google_email")
    private val autoHideKey = booleanPreferencesKey("auto_hide_frame")
    private val providerKey = stringPreferencesKey("suggestion_provider")
    private val showShortcutsKey = booleanPreferencesKey("home_show_shortcuts")
    private val showFrequentKey = booleanPreferencesKey("home_show_frequent")
    private val showRecentKey = booleanPreferencesKey("home_show_recent")
    private val showBookmarksKey = booleanPreferencesKey("home_show_bookmarks")
    private val tabLayoutKey = stringPreferencesKey("tab_layout")
    private val popupBlockingKey = booleanPreferencesKey("popup_blocking")
    private val safeBrowsingKey = booleanPreferencesKey("safe_browsing")
    private val cookieBannerKey = booleanPreferencesKey("cookie_banner")
    private val profilesKey = stringPreferencesKey("profiles")
    private val memoryModeKey = stringPreferencesKey("memory_mode")
    private val customLoadedTabsKey = intPreferencesKey("custom_loaded_tabs")
    private val shortcutsSeededKey = booleanPreferencesKey("shortcuts_seeded")
    private val legalAcceptedAtKey = longPreferencesKey("legal_accepted_at")

    val settings: Flow<BrowserSettings> = context.settingsStore.data.map(::read)
    val profiles: Flow<List<Profile>> = context.settingsStore.data.map { ProfileRules.decode(it[profilesKey]) }

    suspend fun addProfile(name: String): Boolean {
        var added = false
        context.settingsStore.edit { prefs ->
            val list = ProfileRules.decode(prefs[profilesKey])
            val clean = ProfileRules.normalizeName(name)
            if (clean != null && list.size < ProfileRules.MAX_PROFILES && !ProfileRules.nameTaken(list, clean)) {
                prefs[profilesKey] = ProfileRules.encode(list + Profile(ProfileRules.newId(), clean))
                added = true
            }
        }
        return added
    }

    suspend fun renameProfile(id: String, name: String): Boolean {
        var renamed = false
        context.settingsStore.edit { prefs ->
            val list = ProfileRules.decode(prefs[profilesKey])
            val clean = ProfileRules.normalizeName(name)
            if (clean != null && list.any { it.id == id } && !ProfileRules.nameTaken(list, clean, id)) {
                prefs[profilesKey] = ProfileRules.encode(list.map { if (it.id == id) it.copy(name = clean) else it })
                renamed = true
            }
        }
        return renamed
    }

    suspend fun removeProfile(id: String) {
        context.settingsStore.edit { prefs ->
            prefs[profilesKey] = ProfileRules.encode(ProfileRules.decode(prefs[profilesKey]).filterNot { it.id == id })
        }
    }

    suspend fun setToolbarPosition(position: ToolbarPosition) {
        context.settingsStore.edit { it[toolbarKey] = position.name }
    }

    suspend fun setAutoHideFrame(enabled: Boolean) {
        context.settingsStore.edit { it[autoHideKey] = enabled }
    }

    suspend fun setSafeBrowsing(enabled: Boolean) {
        context.settingsStore.edit { it[safeBrowsingKey] = enabled }
    }

    suspend fun setCookieBanner(enabled: Boolean) {
        context.settingsStore.edit { it[cookieBannerKey] = enabled }
    }

    suspend fun setMemoryMode(mode: MemoryMode) {
        context.settingsStore.edit { it[memoryModeKey] = mode.name }
    }

    suspend fun setCustomLoadedTabs(count: Int) {
        context.settingsStore.edit { it[customLoadedTabsKey] = count.coerceIn(1, 30) }
    }

    suspend fun setPopupBlocking(enabled: Boolean) {
        context.settingsStore.edit { it[popupBlockingKey] = enabled }
    }

    suspend fun setTabLayout(layout: TabLayout) {
        context.settingsStore.edit { it[tabLayoutKey] = layout.name }
    }

    suspend fun setSuggestionProvider(provider: SuggestionProvider) {
        context.settingsStore.edit { it[providerKey] = provider.id }
    }

    suspend fun setHomeSection(section: HomeSection, visible: Boolean) {
        context.settingsStore.edit { it[sectionKey(section)] = visible }
    }

    suspend fun resetHomeSettings() {
        context.settingsStore.edit {
            HomeSection.entries.forEach { section -> it.remove(sectionKey(section)) }
            it.remove(homepageKey)
        }
    }

    suspend fun shortcutsSeeded(): Boolean = context.settingsStore.data.first()[shortcutsSeededKey] ?: false

    suspend fun markShortcutsSeeded() {
        context.settingsStore.edit { it[shortcutsSeededKey] = true }
    }

    private fun sectionKey(section: HomeSection) = when (section) {
        HomeSection.SHORTCUTS -> showShortcutsKey
        HomeSection.FREQUENT -> showFrequentKey
        HomeSection.RECENT -> showRecentKey
        HomeSection.BOOKMARKS -> showBookmarksKey
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

    suspend fun setLanguage(tag: String) {
        context.settingsStore.edit { it[languageKey] = tag }
    }

    suspend fun setGoogleEmail(email: String) {
        context.settingsStore.edit {
            if (email.isEmpty()) it.remove(googleEmailKey) else it[googleEmailKey] = email
        }
    }

    suspend fun completeOnboarding(acceptedAt: Long) {
        context.settingsStore.edit {
            it[legalVersionKey] = BrowserSettings.LEGAL_VERSION
            it[legalAcceptedAtKey] = acceptedAt
            it[onboardingKey] = true
        }
    }

    private fun read(preferences: Preferences): BrowserSettings = BrowserSettings(
        toolbarPosition = ToolbarPosition.fromId(preferences[toolbarKey]),
        homepage = preferences[homepageKey] ?: BrowserSettings.DEFAULT_HOMEPAGE,
        searchEngine = SearchEngine.fromId(preferences[engineKey]),
        customSearchTemplate = preferences[templateKey].orEmpty(),
        onboardingCompleted = preferences[onboardingKey] ?: false,
        language = preferences[languageKey].orEmpty(),
        legalVersion = preferences[legalVersionKey] ?: 0,
        legalAcceptedAt = preferences[legalAcceptedAtKey] ?: 0L,
        googleEmail = preferences[googleEmailKey].orEmpty(),
        autoHideFrame = preferences[autoHideKey] ?: true,
        suggestionProvider = SuggestionProvider.fromId(preferences[providerKey]),
        showShortcuts = preferences[showShortcutsKey] ?: true,
        showFrequent = preferences[showFrequentKey] ?: true,
        showRecent = preferences[showRecentKey] ?: true,
        showBookmarks = preferences[showBookmarksKey] ?: true,
        tabLayout = TabLayout.fromId(preferences[tabLayoutKey]),
        popupBlocking = preferences[popupBlockingKey] ?: true,
        memoryMode = MemoryMode.fromId(preferences[memoryModeKey]),
        customLoadedTabs = (preferences[customLoadedTabsKey] ?: 5).coerceIn(1, 30),
        safeBrowsing = preferences[safeBrowsingKey] ?: true,
        cookieBanner = preferences[cookieBannerKey] ?: true
    )
}
