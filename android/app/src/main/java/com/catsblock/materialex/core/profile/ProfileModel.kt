package com.catsblock.materialex.core.profile

import java.util.UUID

data class Profile(val id: String, val name: String)

object ProfileRules {
    const val DEFAULT_ID = "default"
    const val GUEST_ID = "guest"
    const val MAX_NAME = 32
    const val MAX_PROFILES = 12
    private val idPattern = Regex("^p[0-9a-f]{32}$")

    fun normalizeName(raw: String): String? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty() || text.length > MAX_NAME) return null
        if (text.any { it.isISOControl() }) return null
        return text
    }

    fun newId(): String = "p" + UUID.randomUUID().toString().replace("-", "")

    fun isRealProfile(id: String): Boolean = id == DEFAULT_ID || idPattern.matches(id)

    fun canDelete(id: String): Boolean = idPattern.matches(id)

    fun contextId(profileId: String, isPrivate: Boolean): String? =
        if (isPrivate || !idPattern.matches(profileId)) null else profileId

    fun encode(profiles: List<Profile>): String =
        profiles.joinToString("\n") { "${it.id}\t${it.name}" }

    fun decode(raw: String?): List<Profile> {
        if (raw.isNullOrEmpty()) return emptyList()
        val seen = HashSet<String>()
        return raw.lines().mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size != 2 || !idPattern.matches(parts[0])) return@mapNotNull null
            val name = normalizeName(parts[1]) ?: return@mapNotNull null
            if (seen.add(parts[0])) Profile(parts[0], name) else null
        }.take(MAX_PROFILES)
    }

    fun nameTaken(profiles: List<Profile>, name: String, exceptId: String? = null): Boolean =
        profiles.any { it.id != exceptId && it.name.equals(name, ignoreCase = true) }
}
