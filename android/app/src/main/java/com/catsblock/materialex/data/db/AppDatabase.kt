package com.catsblock.materialex.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HistoryEntry::class, Bookmark::class, SitePermission::class, SearchEntry::class, Shortcut::class, DownloadRecord::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao

    abstract fun bookmarks(): BookmarkDao

    abstract fun permissions(): SitePermissionDao

    abstract fun searches(): SearchHistoryDao

    abstract fun shortcuts(): ShortcutDao

    abstract fun downloads(): DownloadDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `fileUri` TEXT NOT NULL, " +
                        "`totalBytes` INTEGER NOT NULL, `downloadedBytes` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_createdAt` ON `downloads` (`createdAt`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`text` TEXT NOT NULL, `searchedAt` INTEGER NOT NULL, PRIMARY KEY(`text`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_history_searchedAt` ON `search_history` (`searchedAt`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shortcuts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `position` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `site_permissions` (`profileId` TEXT NOT NULL, `origin` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `state` TEXT NOT NULL, `denialCount` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`profileId`, `origin`, `type`))"
                )
            }
        }
    }
}
