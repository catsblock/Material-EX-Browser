package com.catsblock.materialex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query(
        "SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' " +
            "ORDER BY visitedAt DESC LIMIT 500"
    )
    fun search(query: String): Flow<List<HistoryEntry>>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE visitedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HistoryEntry>>

    @Query(
        "SELECT url, MAX(title) AS title, COUNT(*) AS visits FROM history " +
            "GROUP BY url ORDER BY visits DESC, MAX(visitedAt) DESC LIMIT :limit"
    )
    fun frequent(limit: Int): Flow<List<FrequentSite>>
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SearchEntry)

    @Query("SELECT text FROM search_history WHERE text LIKE :prefix || '%' ESCAPE '\\' ORDER BY searchedAt DESC LIMIT 5")
    suspend fun match(prefix: String): List<String>

    @Query("DELETE FROM search_history WHERE text NOT IN (SELECT text FROM search_history ORDER BY searchedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM search_history WHERE text = :text")
    suspend fun delete(text: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("DELETE FROM search_history WHERE searchedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts ORDER BY position ASC, id ASC")
    fun observe(): Flow<List<Shortcut>>

    @Query("SELECT * FROM shortcuts ORDER BY position ASC, id ASC")
    suspend fun all(): List<Shortcut>

    @Insert
    suspend fun insert(shortcut: Shortcut): Long

    @Query("UPDATE shortcuts SET title = :title, url = :url WHERE id = :id")
    suspend fun update(id: Long, title: String, url: String)

    @Query("UPDATE shortcuts SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM shortcuts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM shortcuts")
    suspend fun clear()
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: Bookmark): Long

    @Query(
        "SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' " +
            "ORDER BY createdAt DESC"
    )
    fun search(query: String): Flow<List<Bookmark>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun exists(url: String): Flow<Boolean>

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("UPDATE bookmarks SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)
}

@androidx.room.Dao
interface SitePermissionDao {
    @androidx.room.Query("SELECT * FROM site_permissions WHERE profileId = :profileId AND origin = :origin AND type = :type")
    suspend fun find(profileId: String, origin: String, type: String): SitePermission?

    @androidx.room.Query("SELECT * FROM site_permissions WHERE profileId = :profileId AND origin = :origin")
    suspend fun forOrigin(profileId: String, origin: String): List<SitePermission>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(permission: SitePermission)

    @androidx.room.Query("DELETE FROM site_permissions WHERE profileId = :profileId")
    suspend fun clearProfile(profileId: String)
}
