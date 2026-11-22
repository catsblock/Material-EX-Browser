package com.catsblock.materialex.core.settings

import java.text.Normalizer
import java.util.Locale

data class SearchEntry<T>(
    val title: String,
    val description: String,
    val category: String,
    val payload: T
)

object SettingsSearch {
    const val MAX_QUERY_LENGTH = 200

    fun <T> results(query: String, entries: List<SearchEntry<T>>): List<SearchEntry<T>> {
        val terms = normalize(query.take(MAX_QUERY_LENGTH)).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        val joined = terms.joinToString(" ")
        return entries.mapNotNull { entry ->
            val title = normalize(entry.title)
            val haystack = "$title ${normalize(entry.description)} ${normalize(entry.category)}"
            if (!terms.all { haystack.contains(it) }) return@mapNotNull null
            val rank = when {
                title == joined -> 0
                title.startsWith(joined) -> 1
                terms.all { title.contains(it) } -> 2
                else -> 3
            }
            entry to rank
        }.sortedBy { it.second }.map { it.first }
    }

    fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(WHITESPACE, " ")
            .trim()

    private val MARKS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("\\s+")
}
