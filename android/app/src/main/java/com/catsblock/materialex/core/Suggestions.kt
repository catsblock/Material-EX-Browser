package com.catsblock.materialex.core

import java.net.URLEncoder

enum class SuggestionProvider(val id: String, private val template: String?) {
    NONE("none", null),
    DUCKDUCKGO("duckduckgo", "https://duckduckgo.com/ac/?type=list&q=%s"),
    GOOGLE("google", "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"),
    BRAVE("brave", "https://search.brave.com/api/suggest?q=%s"),
    ECOSIA("ecosia", "https://ac.ecosia.org/autocomplete?type=list&q=%s"),
    KAGI("kagi", "https://kagi.com/api/autosuggest?q=%s");

    fun urlFor(query: String): String? = template?.replace("%s", URLEncoder.encode(query, "UTF-8"))

    companion object {
        fun fromId(id: String?): SuggestionProvider = entries.firstOrNull { it.id == id } ?: DUCKDUCKGO
    }
}

object SuggestionPolicy {
    const val MIN_CHARACTERS = 3
    const val MAX_RESULTS = 8

    fun shouldRequest(query: String, provider: SuggestionProvider, isPrivate: Boolean): Boolean =
        !isPrivate && provider != SuggestionProvider.NONE && query.trim().length >= MIN_CHARACTERS
}

object SuggestionParser {
    private const val MAX_BODY = 65_536
    private const val MAX_ITEM = 200

    fun parse(body: String): List<String> {
        if (body.length > MAX_BODY) return emptyList()
        val root = runCatching { JsonReader(body).value(0) }.getOrNull() as? List<*> ?: return emptyList()
        val items: List<*> = when {
            root.size >= 2 && root[1] is List<*> -> root[1] as List<*>
            root.isNotEmpty() && root.all { it is Map<*, *> } -> root
            else -> emptyList<Any>()
        }
        return items
            .mapNotNull {
                when (it) {
                    is String -> it
                    is Map<*, *> -> (it["phrase"] ?: it["t"]) as? String
                    else -> null
                }
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_ITEM }
            .distinct()
            .take(SuggestionPolicy.MAX_RESULTS)
    }

    private class JsonReader(private val text: String) {
        private var index = 0

        fun value(depth: Int): Any? {
            require(depth < 8)
            skip()
            return when (text[index]) {
                '[' -> array(depth)
                '{' -> obj(depth)
                '"' -> string()
                else -> literal()
            }
        }

        private fun array(depth: Int): List<Any?> {
            index++
            val out = ArrayList<Any?>()
            skip()
            if (text[index] == ']') {
                index++
                return out
            }
            while (true) {
                out.add(value(depth + 1))
                skip()
                if (text[index] == ',') index++ else break
            }
            expect(']')
            return out
        }

        private fun obj(depth: Int): Map<String, Any?> {
            index++
            val out = LinkedHashMap<String, Any?>()
            skip()
            if (text[index] == '}') {
                index++
                return out
            }
            while (true) {
                skip()
                val key = string()
                skip()
                expect(':')
                out[key] = value(depth + 1)
                skip()
                if (text[index] == ',') index++ else break
            }
            expect('}')
            return out
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val c = text[index++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> when (val e = text[index++]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            out.append(text.substring(index, index + 4).toInt(16).toChar())
                            index += 4
                        }
                        else -> out.append(e)
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun literal(): Any? {
            val start = index
            while (index < text.length && text[index] !in ",]} \n\r\t") index++
            require(index > start)
            return null
        }

        private fun expect(c: Char) {
            require(text[index] == c)
            index++
        }

        private fun skip() {
            while (index < text.length && text[index].isWhitespace()) index++
        }
    }
}
