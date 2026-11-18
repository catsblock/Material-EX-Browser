package com.catsblock.materialex.data

import com.catsblock.materialex.data.db.AppDatabase
import com.catsblock.materialex.data.db.Bookmark
import com.catsblock.materialex.data.db.HistoryEntry
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

    private fun isWebUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    companion object {
        const val DEFAULT_RETENTION_DAYS = 60
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
