package com.catsblock.materialex.core.permission

data class TemporaryGrant(
    val profileId: String,
    val origin: String,
    val type: PermissionType,
    val scopeId: String,
    val expiresAt: Long
)

class TemporaryGrants(
    private val clock: () -> Long = System::currentTimeMillis,
    private val lifetimeMs: Long = DEFAULT_LIFETIME_MS
) {
    private val grants = ArrayList<TemporaryGrant>()

    @Synchronized
    fun grant(profileId: String, origin: String, type: PermissionType, scopeId: String): TemporaryGrant {
        purge()
        grants.removeAll { matches(it, profileId, origin, type, scopeId) }
        val created = TemporaryGrant(profileId, origin, type, scopeId, clock() + lifetimeMs)
        grants.add(created)
        return created
    }

    @Synchronized
    fun isActive(profileId: String, origin: String, type: PermissionType, scopeId: String): Boolean {
        purge()
        return grants.any { matches(it, profileId, origin, type, scopeId) }
    }

    @Synchronized
    fun active(profileId: String, origin: String): List<TemporaryGrant> {
        purge()
        return grants.filter { it.profileId == profileId && it.origin == origin }
    }

    @Synchronized
    fun revoke(profileId: String, origin: String, type: PermissionType) {
        grants.removeAll { it.profileId == profileId && it.origin == origin && it.type == type }
    }

    @Synchronized
    fun revokeOrigin(profileId: String, origin: String) {
        grants.removeAll { it.profileId == profileId && it.origin == origin }
    }

    @Synchronized
    fun revokeScope(scopeId: String) {
        grants.removeAll { it.scopeId == scopeId }
    }

    @Synchronized
    fun clear() {
        grants.clear()
    }

    private fun purge() {
        val now = clock()
        grants.removeAll { it.expiresAt <= now }
    }

    private fun matches(grant: TemporaryGrant, profileId: String, origin: String, type: PermissionType, scopeId: String) =
        grant.profileId == profileId && grant.origin == origin && grant.type == type && grant.scopeId == scopeId

    companion object {
        const val DEFAULT_LIFETIME_MS = 15 * 60 * 1000L
    }
}
