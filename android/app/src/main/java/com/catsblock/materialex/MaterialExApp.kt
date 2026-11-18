package com.catsblock.materialex

import android.app.Application
import androidx.room.Room
import com.catsblock.materialex.data.LibraryRepository
import com.catsblock.materialex.data.SettingsRepository
import com.catsblock.materialex.data.db.AppDatabase
import com.catsblock.materialex.engine.BrowserEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MaterialExApp : Application() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val library: LibraryRepository by lazy {
        LibraryRepository(Room.databaseBuilder(this, AppDatabase::class.java, "materialex.db").build())
    }

    val engine: BrowserEngine by lazy {
        BrowserEngine(this).also { engine ->
            engine.onPageVisited = { url, title -> ioScope.launch { library.recordVisit(url, title) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ioScope.launch { library.purgeHistory(LibraryRepository.DEFAULT_RETENTION_DAYS) }
    }
}
