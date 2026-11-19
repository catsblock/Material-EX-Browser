package com.catsblock.materialex.core.favicon

object FaviconPolicy {
    const val MAX_BYTES = 262144
    const val MAX_EDGE = 128

    fun hostKey(url: String): String? {
        val lower = url.trim()
        val rest = when {
            lower.startsWith("https://", ignoreCase = true) -> lower.substring(8)
            lower.startsWith("http://", ignoreCase = true) -> lower.substring(7)
            else -> return null
        }
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        if (host.isEmpty() || host == "localhost") return null
        if (host.all { it.isDigit() || it == '.' } || host.contains('[')) return null
        if (!host.contains('.')) return null
        return host
    }

    fun iconUrl(host: String): String = "https://$host/favicon.ico"

    fun sampleSize(width: Int, height: Int): Int {
        var size = 1
        var edge = maxOf(width, height)
        while (edge / 2 >= MAX_EDGE) {
            edge /= 2
            size *= 2
        }
        return size
    }
}
