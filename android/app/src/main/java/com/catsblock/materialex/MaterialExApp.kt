package com.catsblock.materialex

import android.app.Application
import com.catsblock.materialex.data.SettingsRepository
import com.catsblock.materialex.engine.BrowserEngine

class MaterialExApp : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val engine: BrowserEngine by lazy { BrowserEngine(this) }
}
