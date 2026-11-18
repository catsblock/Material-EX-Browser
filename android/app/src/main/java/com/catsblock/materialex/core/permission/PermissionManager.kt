package com.catsblock.materialex.core.permission

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PermissionStore {
    suspend fun get(profileId: String, origin: String, type: PermissionType): PermissionRecord
    suspend fun put(profileId: String, origin: String, type: PermissionType, record: PermissionRecord)
    suspend fun listForOrigin(profileId: String, origin: String): Map<PermissionType, PermissionRecord>
    suspend fun clearProfile(profileId: String)
}

class InMemoryPermissionStore : PermissionStore {
    private val mutex = Mutex()
    private val data = HashMap<Triple<String, String, PermissionType>, PermissionRecord>()

    override suspend fun get(profileId: String, origin: String, type: PermissionType): PermissionRecord =
        mutex.withLock { data[Triple(profileId, origin, type)] ?: PermissionRecord() }

    override suspend fun put(profileId: String, origin: String, type: PermissionType, record: PermissionRecord) {
        mutex.withLock { data[Triple(profileId, origin, type)] = record }
    }

    override suspend fun listForOrigin(profileId: String, origin: String): Map<PermissionType, PermissionRecord> =
        mutex.withLock {
            data.filterKeys { it.first == profileId && it.second == origin }.mapKeys { it.key.third }
        }

    override suspend fun clearProfile(profileId: String) {
        mutex.withLock { data.keys.removeAll { it.first == profileId } }
    }
}

class PermissionManager(private val store: PermissionStore) {
    private val mutex = Mutex()

    suspend fun decide(profileId: String, origin: String, type: PermissionType): Decision =
        PermissionPolicy.decide(store.get(profileId, origin, type))

    suspend fun choose(profileId: String, origin: String, type: PermissionType, choice: PromptChoice): PermissionRecord =
        update(profileId, origin, type) { PermissionPolicy.applyChoice(it, choice) }

    suspend fun setState(profileId: String, origin: String, type: PermissionType, state: PermissionState): PermissionRecord =
        update(profileId, origin, type) { PermissionPolicy.setState(it, state) }

    suspend fun resetCounter(profileId: String, origin: String, type: PermissionType): PermissionRecord =
        update(profileId, origin, type) { PermissionPolicy.resetCounter(it) }

    suspend fun overview(profileId: String, origin: String): Map<PermissionType, PermissionRecord> {
        val stored = store.listForOrigin(profileId, origin)
        return PermissionType.entries.associateWith { stored[it] ?: PermissionRecord() }
    }

    suspend fun clearProfile(profileId: String) = store.clearProfile(profileId)

    private suspend fun update(
        profileId: String,
        origin: String,
        type: PermissionType,
        transform: (PermissionRecord) -> PermissionRecord
    ): PermissionRecord = mutex.withLock {
        val updated = transform(store.get(profileId, origin, type))
        store.put(profileId, origin, type, updated)
        updated
    }
}
