package com.catsblock.materialex.core.siteedit

import java.net.URI
import java.util.Locale

enum class CssIssue {
    TOO_LARGE,
    UNBALANCED_BRACES,
    IMPORT_NOT_ALLOWED
}

object SiteStyleRules {
    const val MAX_BYTES = 64 * 1024
    const val MAX_SITES = 200

    fun hostOf(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        return uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
    }

    fun validate(css: String): CssIssue? {
        if (css.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return CssIssue.TOO_LARGE
        val stripped = stripComments(css)
        if (IMPORT_RULE.containsMatchIn(stripped)) return CssIssue.IMPORT_NOT_ALLOWED
        var depth = 0
        var quote = '\u0000'
        var i = 0
        while (i < stripped.length) {
            val c = stripped[i]
            if (quote != '\u0000') {
                if (c == '\\') i++ else if (c == quote) quote = '\u0000'
            } else when (c) {
                '"', '\'' -> quote = c
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth < 0) return CssIssue.UNBALANCED_BRACES
                }
            }
            i++
        }
        return if (depth == 0) null else CssIssue.UNBALANCED_BRACES
    }

    fun encode(styles: Map<String, String>): String =
        styles.entries.joinToString("\n") { (host, css) -> escape(host) + "\t" + escape(css) }

    fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (line in raw.split('\n')) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val host = unescape(line.substring(0, tab))
            val css = unescape(line.substring(tab + 1))
            if (host.isNotEmpty() && css.isNotEmpty()) out[host] = css
        }
        return out
    }

    fun withStyle(styles: Map<String, String>, host: String, css: String): Map<String, String> {
        val next = LinkedHashMap(styles)
        if (css.isBlank()) next.remove(host) else {
            next.remove(host)
            next[host] = css
        }
        while (next.size > MAX_SITES) next.remove(next.keys.first())
        return next
    }

    private fun stripComments(css: String): String = css.replace(COMMENT, "")

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        return sb.toString()
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                i++
                when (s[i]) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(s[i])
                }
            } else sb.append(c)
            i++
        }
        return sb.toString()
    }

    private val COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val IMPORT_RULE = Regex("@import", RegexOption.IGNORE_CASE)
}
