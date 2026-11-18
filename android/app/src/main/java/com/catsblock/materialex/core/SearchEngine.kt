package com.catsblock.materialex.core

import java.net.URI
import java.net.URLEncoder

enum class SearchEngine(val id: String, val template: String?) {
    GOOGLE("google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("duckduckgo", "https://duckduckgo.com/?q=%s"),
    BING("bing", "https://www.bing.com/search?q=%s"),
    BRAVE("brave", "https://search.brave.com/search?q=%s"),
    ECOSIA("ecosia", "https://www.ecosia.org/search?q=%s"),
    CHATGPT("chatgpt", "https://chatgpt.com/?q=%s"),
    QWANT("qwant", "https://www.qwant.com/?q=%s"),
    KAGI("kagi", "https://kagi.com/search?q=%s"),
    CUSTOM("custom", null);

    companion object {
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

object SearchTemplates {
    const val PLACEHOLDER = "%s"
    private const val SCHEME_PREFIX = "https://"

    fun isValid(template: String): Boolean {
        val trimmed = template.trim()
        if (trimmed.length > 2048 || trimmed.split(PLACEHOLDER).size != 2) return false
        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) return false
        val prefix = trimmed.substringBefore(PLACEHOLDER)
        if (!prefix.startsWith(SCHEME_PREFIX)) return false
        val afterScheme = prefix.removePrefix(SCHEME_PREFIX)
        if (!afterScheme.contains('/') && !afterScheme.contains('?')) return false
        val uri = runCatching { URI(trimmed.replace(PLACEHOLDER, "q")) }.getOrNull() ?: return false
        return !uri.host.isNullOrEmpty() && uri.userInfo == null
    }

    fun build(template: String, query: String): String =
        template.trim().replace(PLACEHOLDER, URLEncoder.encode(query, "UTF-8"))

    fun resolveUrl(engine: SearchEngine, customTemplate: String?, query: String): String? {
        val template = engine.template ?: customTemplate?.takeIf(::isValid) ?: return null
        return build(template, query)
    }
}
