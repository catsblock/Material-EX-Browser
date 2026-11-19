package com.catsblock.materialex.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.content.res.Resources

object LocaleApplier {
    fun apply(context: Context, tag: String) {
        val manager = context.getSystemService(LocaleManager::class.java)
        manager.applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    fun deviceTags(): List<String> {
        val locales = Resources.getSystem().configuration.locales
        return List(locales.size()) { locales.get(it).toLanguageTag() }
    }
}
