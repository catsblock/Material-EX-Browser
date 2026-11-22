package com.catsblock.materialex.core.permission

class TemporaryGrants(private val ttlMs: Long = DEFAULT_TTL_MS) {
    private data class Key(val profileId: String, val scopeId: String, val origin: String, val type: PermissionType)

    private val grants = HashMap<Key, Long>()

    @Synchronized
    fun grant(profileId: String, scopeId: String, origin: String, type: PermissionType, now: Long) {
        grants[Key(profileId, scopeId, origin, type)] = now + ttlMs
    }

    @Synchronized
    fun isGranted(profileId: String, scopeId: String, origin: String, type: PermissionType, now: Long): Boolean {
        val key = Key(profileId, scopeId, origin, type)
        val until = grants[key] ?: return false
        if (now >= until) {
            grants.remove(key)
            return false
        }
        return true
    }

    @Synchronized
    fun revoke(profileId: String, scopeId: String, origin: String, type: PermissionType) {
        grants.remove(Key(profileId, scopeId, origin, type))
    }

    @Synchronized
    fun revokeOrigin(profileId: String, origin: String) {
        grants.keys.removeAll { it.profileId == profileId && it.origin == origin }
    }

    @Synchronized
    fun revokeScope(scopeId: String) {
        grants.keys.removeAll { it.scopeId == scopeId }
    }

    @Synchronized
    fun revokeScopeOutside(scopeId: String, origin: String?) {
        grants.keys.removeAll { it.scopeId == scopeId && it.origin != origin }
    }

    @Synchronized
    fun revokeProfile(profileId: String) {
        grants.keys.removeAll { it.profileId == profileId }
    }

    @Synchronized
    fun revokeAll() {
        grants.clear()
    }

    companion object {
        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
    }
}
