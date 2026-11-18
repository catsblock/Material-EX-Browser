package com.catsblock.materialex.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntry::class, Bookmark::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao

    abstract fun bookmarks(): BookmarkDao
}
