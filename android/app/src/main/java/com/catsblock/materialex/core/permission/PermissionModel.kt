package com.catsblock.materialex.core.permission

import java.net.URI

enum class PermissionType(val id: String, val requiresSecureOrigin: Boolean) {
    CAMERA("camera", true),
    MICROPHONE("microphone", true),
    LOCATION("location", true),
    NOTIFICATIONS("notifications", true),
    PERSISTENT_STORAGE("persistent_storage", false);

    companion object {
        fun fromId(id: String): PermissionType? = entries.firstOrNull { it.id == id }
    }
}

enum class PermissionState { ASK, ALLOW, DENY, AUTO_BLOCKED }

enum class PromptChoice { ALWAYS_ASK, DENY, ALLOW, ALLOW_ONCE }

enum class Decision { GRANT, REJECT, PROMPT }

data class PermissionRecord(val state: PermissionState = PermissionState.ASK, val denialCount: Int = 0)

object PermissionPolicy {
    const val AUTO_BLOCK_THRESHOLD = 3

    fun decide(record: PermissionRecord): Decision = when (record.state) {
        PermissionState.ALLOW -> Decision.GRANT
        PermissionState.DENY, PermissionState.AUTO_BLOCKED -> Decision.REJECT
        PermissionState.ASK -> Decision.PROMPT
    }

    fun applyChoice(record: PermissionRecord, choice: PromptChoice): PermissionRecord {
        if (record.state != PermissionState.ASK) return record
        return when (choice) {
            PromptChoice.ALWAYS_ASK, PromptChoice.ALLOW_ONCE -> record
            PromptChoice.ALLOW -> record.copy(state = PermissionState.ALLOW)
            PromptChoice.DENY -> {
                val count = record.denialCount + 1
                val state = if (count >= AUTO_BLOCK_THRESHOLD) PermissionState.AUTO_BLOCKED else PermissionState.ASK
                PermissionRecord(state, count)
            }
        }
    }

    fun setState(record: PermissionRecord, state: PermissionState): PermissionRecord {
        require(state != PermissionState.AUTO_BLOCKED) { "AUTO_BLOCKED cannot be set manually" }
        val wasBlocked = record.state == PermissionState.AUTO_BLOCKED
        val count = if (wasBlocked && state != PermissionState.DENY) 0 else record.denialCount
        return PermissionRecord(state, count)
    }

    fun resetCounter(record: PermissionRecord): PermissionRecord =
        PermissionRecord(
            state = if (record.state == PermissionState.AUTO_BLOCKED) PermissionState.ASK else record.state,
            denialCount = 0
        )
}

object Origin {
    fun normalize(url: String?): String? {
        if (url == null) return null
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val defaultPort = if (scheme == "https") 443 else 80
        return if (uri.port == -1 || uri.port == defaultPort) "$scheme://$host" else "$scheme://$host:${uri.port}"
    }

    fun isSecure(origin: String): Boolean {
        if (origin.startsWith("https://")) return true
        val host = origin.removePrefix("http://").substringBefore(':')
        return host == "localhost" || host == "127.0.0.1"
    }

    fun host(origin: String): String = origin.substringAfter("://").substringBefore(':')
}
