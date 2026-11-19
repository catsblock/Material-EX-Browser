package com.catsblock.materialex.data

import com.catsblock.materialex.core.Resolution
import com.catsblock.materialex.core.UrlResolver
import com.catsblock.materialex.data.db.AppDatabase
import com.catsblock.materialex.data.db.Bookmark
import com.catsblock.materialex.data.db.FrequentSite
import com.catsblock.materialex.data.db.HistoryEntry
import com.catsblock.materialex.data.db.SearchEntry
import com.catsblock.materialex.data.db.Shortcut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class LibraryRepository(
    private val database: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis
) {
    fun history(query: String): Flow<List<HistoryEntry>> = database.history().search(query.trim())

    fun bookmarks(query: String): Flow<List<Bookmark>> = database.bookmarks().search(query.trim())

    fun isBookmarked(url: String): Flow<Boolean> =
        if (isWebUrl(url)) database.bookmarks().exists(url) else flowOf(false)

    suspend fun recordVisit(url: String, title: String) {
        if (!isWebUrl(url)) return
        database.history().insert(HistoryEntry(url = url, title = title, visitedAt = now()))
    }

    suspend fun deleteHistory(id: Long) = database.history().delete(id)

    suspend fun clearHistory() = database.history().clear()

    suspend fun purgeHistory(retentionDays: Int) {
        database.history().deleteOlderThan(now() - retentionDays * MILLIS_PER_DAY)
    }

    suspend fun toggleBookmark(url: String, title: String, currentlyBookmarked: Boolean) {
        if (!isWebUrl(url)) return
        if (currentlyBookmarked) {
            database.bookmarks().deleteByUrl(url)
        } else {
            database.bookmarks().insert(Bookmark(title = title.ifBlank { url }, url = url, createdAt = now()))
        }
    }

    suspend fun deleteBookmark(id: Long) = database.bookmarks().delete(id)

    suspend fun renameBookmark(id: Long, title: String) {
        if (title.isNotBlank()) database.bookmarks().rename(id, title.trim())
    }

    fun recentPages(limit: Int): Flow<List<HistoryEntry>> = database.history().recent(limit)

    fun frequentSites(limit: Int): Flow<List<FrequentSite>> = database.history().frequent(limit)

    suspend fun recordSearch(query: String) {
        val text = query.trim()
        if (text.isEmpty() || text.length > MAX_SEARCH_LENGTH) return
        database.searches().upsert(SearchEntry(text, now()))
        database.searches().trim(MAX_SEARCHES)
    }

    suspend fun matchSearches(prefix: String): List<String> =
        database.searches().match(prefix.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))

    suspend fun deleteSearch(text: String) = database.searches().delete(text)

    suspend fun clearSearches() = database.searches().clear()

    suspend fun purgeSearches(retentionDays: Int) {
        database.searches().deleteOlderThan(now() - retentionDays * MILLIS_PER_DAY)
    }

    fun shortcuts(): Flow<List<Shortcut>> = database.shortcuts().observe()

    suspend fun saveShortcut(id: Long?, title: String, url: String): Boolean {
        val resolved = (UrlResolver.resolve(url) as? Resolution.Navigate)?.url ?: return false
        val name = title.trim().ifBlank { hostOf(resolved) }.take(MAX_SHORTCUT_TITLE)
        if (id == null) {
            val next = (database.shortcuts().all().maxOfOrNull { it.position } ?: -1) + 1
            database.shortcuts().insert(Shortcut(title = name, url = resolved, position = next))
        } else {
            database.shortcuts().update(id, name, resolved)
        }
        return true
    }

    suspend fun deleteShortcut(id: Long) = database.shortcuts().delete(id)

    suspend fun moveShortcut(id: Long, delta: Int) {
        val list = database.shortcuts().all()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        val reordered = list.toMutableList().apply { add(to, removeAt(from)) }
        reordered.forEachIndexed { index, shortcut -> database.shortcuts().setPosition(shortcut.id, index) }
    }

    suspend fun restoreDefaultShortcuts() {
        database.shortcuts().clear()
        DEFAULT_SHORTCUTS.forEachIndexed { index, (title, url) ->
            database.shortcuts().insert(Shortcut(title = title, url = url, position = index))
        }
    }

    private fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').removePrefix("www.")

    private fun isWebUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    companion object {
        const val DEFAULT_RETENTION_DAYS = 60
        const val MAX_SEARCHES = 15
        private const val MAX_SEARCH_LENGTH = 200
        private const val MAX_SHORTCUT_TITLE = 40
        private const val MILLIS_PER_DAY = 86_400_000L
        private val DEFAULT_SHORTCUTS = listOf(
            "Google" to "https://www.google.com/",
            "YouTube" to "https://www.youtube.com/",
            "Wikipedia" to "https://www.wikipedia.org/",
            "GitHub" to "https://github.com/"
        )
    }
}
