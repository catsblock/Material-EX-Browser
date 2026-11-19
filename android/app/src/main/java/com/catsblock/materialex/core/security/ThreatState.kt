package com.catsblock.materialex.core.security

enum class ThreatKind { PHISHING, MALWARE, UNWANTED, HARMFUL }

data class ThreatInfo(
    val tabId: String,
    val url: String,
    val kind: ThreatKind,
    val engineCode: Int,
    val engineCodeName: String,
    val detectedAt: Long
) {
    val host: String
        get() = url.substringAfter("://", url).substringBefore('/').substringBefore('?').substringBefore('#')
}

class BypassGate {
    private var tabId: String? = null
    private var url: String? = null

    fun begin(tabId: String, url: String) {
        this.tabId = tabId
        this.url = url
    }

    fun isActiveFor(tabId: String): Boolean = this.tabId == tabId

    fun isActive(): Boolean = tabId != null

    fun targetUrl(): String? = url

    fun end(): Boolean {
        val wasActive = tabId != null
        tabId = null
        url = null
        return wasActive
    }

    fun endIfFor(tabId: String): Boolean = if (this.tabId == tabId) end() else false
}
