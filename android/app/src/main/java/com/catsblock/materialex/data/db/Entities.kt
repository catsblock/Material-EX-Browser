package com.catsblock.materialex.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "history", indices = [Index("visitedAt")])
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long
)

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long
)

@androidx.room.Entity(tableName = "site_permissions", primaryKeys = ["profileId", "origin", "type"])
data class SitePermission(
    val profileId: String,
    val origin: String,
    val type: String,
    val state: String,
    val denialCount: Int
)

@Entity(tableName = "search_history", indices = [Index("searchedAt")])
data class SearchEntry(
    @PrimaryKey val text: String,
    val searchedAt: Long
)

@Entity(tableName = "shortcuts")
data class Shortcut(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val position: Int
)

data class FrequentSite(
    val url: String,
    val title: String,
    val visits: Int
)

@Entity(tableName = "downloads", indices = [Index("createdAt")])
data class DownloadRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val sourceUrl: String,
    val mimeType: String,
    val fileUri: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val createdAt: Long
)
