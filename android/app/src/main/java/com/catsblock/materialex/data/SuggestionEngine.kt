package com.catsblock.materialex.data

import com.catsblock.materialex.core.SuggestionParser
import com.catsblock.materialex.core.SuggestionPolicy
import com.catsblock.materialex.core.SuggestionProvider

data class Suggestion(val text: String, val fromHistory: Boolean)

class SuggestionEngine(
    private val library: LibraryRepository,
    private val client: SuggestionClient
) {
    suspend fun load(query: String, provider: SuggestionProvider, isPrivate: Boolean): List<Suggestion> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val local = if (isPrivate) emptyList() else library.matchSearches(trimmed).map { Suggestion(it, true) }
        if (!SuggestionPolicy.shouldRequest(trimmed, provider, isPrivate)) return local
        val url = provider.urlFor(trimmed) ?: return local
        val remote = client.fetch(url)?.let(SuggestionParser::parse).orEmpty()
            .filter { candidate -> local.none { it.text.equals(candidate, ignoreCase = true) } }
            .map { Suggestion(it, false) }
        return (local + remote).take(SuggestionPolicy.MAX_RESULTS)
    }
}
