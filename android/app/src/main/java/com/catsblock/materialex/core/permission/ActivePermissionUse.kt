package com.catsblock.materialex.core.permission

data class Revocation(val profileId: String, val origin: String?, val type: PermissionType?)

class ActivePermissionUse {
    private data class Use(val tabId: String, val profileId: String, val origin: String, val type: PermissionType)

    private val uses = LinkedHashSet<Use>()

    @Synchronized
    fun record(tabId: String, profileId: String, origin: String, type: PermissionType) {
        uses.add(Use(tabId, profileId, origin, type))
    }

    @Synchronized
    fun dropTab(tabId: String) {
        uses.removeAll { it.tabId == tabId }
    }

    @Synchronized
    fun dropOutside(tabId: String, origin: String?) {
        uses.removeAll { it.tabId == tabId && it.origin != origin }
    }

    @Synchronized
    fun take(revocation: Revocation): Set<String> {
        val hit = uses.filter {
            it.profileId == revocation.profileId &&
                (revocation.origin == null || it.origin == revocation.origin) &&
                (revocation.type == null || it.type == revocation.type)
        }
        uses.removeAll(hit.toSet())
        return hit.mapTo(LinkedHashSet()) { it.tabId }
    }
}
