package com.catsblock.materialex.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HistoryEntry::class, Bookmark::class, SitePermission::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao

    abstract fun bookmarks(): BookmarkDao

    abstract fun permissions(): SitePermissionDao

    companion object {
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
