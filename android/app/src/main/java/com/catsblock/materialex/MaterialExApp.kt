package com.catsblock.materialex

import android.app.Application
import androidx.room.Room
import com.catsblock.materialex.core.permission.PermissionManager
import com.catsblock.materialex.data.LibraryRepository
import com.catsblock.materialex.data.RoomPermissionStore
import com.catsblock.materialex.data.SettingsRepository
import com.catsblock.materialex.data.SuggestionClient
import com.catsblock.materialex.data.SuggestionEngine
import com.catsblock.materialex.data.db.AppDatabase
import com.catsblock.materialex.engine.BrowserEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MaterialExApp : Application() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "materialex.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    val library: LibraryRepository by lazy { LibraryRepository(database) }

    val suggestions: SuggestionEngine by lazy { SuggestionEngine(library, SuggestionClient()) }

    val permissionManager: PermissionManager by lazy { PermissionManager(RoomPermissionStore(database.permissions())) }

    val engine: BrowserEngine by lazy {
        BrowserEngine(this, permissionManager).also { engine ->
            engine.onPageVisited = { url, title -> ioScope.launch { library.recordVisit(url, title) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ioScope.launch {
            library.purgeHistory(LibraryRepository.DEFAULT_RETENTION_DAYS)
            library.purgeSearches(LibraryRepository.DEFAULT_RETENTION_DAYS)
            if (!settings.shortcutsSeeded()) {
                library.restoreDefaultShortcuts()
                settings.markShortcutsSeeded()
            }
        }
    }
}
