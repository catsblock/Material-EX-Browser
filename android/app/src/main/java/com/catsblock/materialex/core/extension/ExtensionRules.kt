package com.catsblock.materialex.core.extension

data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean
)

data class ExtensionPrompt(
    val extensionName: String,
    val permissions: List<String>,
    val origins: List<String>
) {
    val broadAccess: Boolean get() = ExtensionRules.hasBroadAccess(origins)
}

object ExtensionRules {
    private val broadPatterns = setOf("<all_urls>", "*://*/*", "http://*/*", "https://*/*", "*://*/", "https://*/", "http://*/")

    fun hasBroadAccess(origins: List<String>): Boolean = origins.any { it.trim() in broadPatterns }

    fun normalizeInstallUrl(raw: String): String? {
        val text = raw.trim()
        if (!text.startsWith("https://", ignoreCase = true)) return null
        val withoutScheme = text.substring(8)
        val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        if (host.isEmpty() || host.substringBefore(':').let { it.isEmpty() || !it.contains('.') }) return null
        if (text.any { it.isWhitespace() || it.isISOControl() }) return null
        val path = withoutScheme.substringAfter('/', "").substringBefore('?').substringBefore('#')
        if (!path.endsWith(".xpi", ignoreCase = true)) return null
        return text
    }

    fun sortedForDisplay(list: List<ExtensionInfo>): List<ExtensionInfo> =
        list.sortedWith(compareBy({ !it.enabled }, { it.name.lowercase() }))
}
