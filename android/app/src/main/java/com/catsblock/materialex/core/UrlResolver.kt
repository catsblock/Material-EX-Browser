package com.catsblock.materialex.core

import java.net.URI

sealed interface Resolution {
    data class Navigate(val url: String) : Resolution
    data class Search(val query: String) : Resolution
    data object Rejected : Resolution
}

object UrlResolver {
    private val schemePattern = Regex("^([A-Za-z][A-Za-z0-9+.-]*):(.*)$")
    private val domainPattern = Regex("^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$")
    private val ipv4Pattern = Regex("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$")
    private val webSchemes = setOf("http", "https")
    private val blockedSchemes = setOf(
        "javascript", "data", "file", "content", "intent", "blob", "vbscript", "view-source", "about", "jar"
    )

    fun resolve(rawInput: String): Resolution {
        val input = rawInput.trim()
        if (input.isEmpty()) return Resolution.Rejected
        if (input.any { it.isWhitespace() || it.isISOControl() }) return Resolution.Search(input)
        val match = schemePattern.matchEntire(input) ?: return resolveBare(input)
        val scheme = match.groupValues[1].lowercase()
        val rest = match.groupValues[2]
        if (rest.startsWith("//")) return resolveExplicit(scheme, input)
        val portLike = rest.firstOrNull()?.isDigit() == true
        if (portLike && (scheme == "localhost" || domainPattern.matches(scheme) || ipv4Pattern.matches(scheme))) {
            return resolveBare(input)
        }
        return if (scheme in blockedSchemes) Resolution.Rejected else Resolution.Search(input)
    }

    private fun resolveExplicit(scheme: String, input: String): Resolution {
        if (scheme !in webSchemes) return Resolution.Rejected
        val uri = runCatching { URI(input) }.getOrNull() ?: return Resolution.Rejected
        if (uri.host.isNullOrEmpty() || uri.userInfo != null) return Resolution.Rejected
        return Resolution.Navigate(input)
    }

    private fun resolveBare(input: String): Resolution {
        val authority = input.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.contains('@')) return Resolution.Search(input)
        val hasPort = authority.contains(':')
        val host = if (hasPort) authority.substringBeforeLast(':') else authority
        if (hasPort) {
            val port = authority.substringAfterLast(':')
            val valid = port.isNotEmpty() && port.length <= 5 && port.all(Char::isDigit) && port.toInt() in 1..65535
            if (!valid) return Resolution.Search(input)
        }
        return when {
            host.equals("localhost", ignoreCase = true) || ipv4Pattern.matches(host) -> Resolution.Navigate("http://$input")
            domainPattern.matches(host) -> Resolution.Navigate("https://$input")
            else -> Resolution.Search(input)
        }
    }
}
