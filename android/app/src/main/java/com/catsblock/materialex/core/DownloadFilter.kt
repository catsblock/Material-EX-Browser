package com.catsblock.materialex.core

import com.catsblock.materialex.core.settings.SettingsSearch
import java.net.URI

object DownloadFilter {
    fun matches(fileName: String, sourceUrl: String, query: String): Boolean {
        val terms = SettingsSearch.normalize(query.take(SettingsSearch.MAX_QUERY_LENGTH)).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        val host = runCatching { URI(sourceUrl).host }.getOrNull().orEmpty()
        val haystack = SettingsSearch.normalize("$fileName $host")
        return terms.all { haystack.contains(it) }
    }
}
