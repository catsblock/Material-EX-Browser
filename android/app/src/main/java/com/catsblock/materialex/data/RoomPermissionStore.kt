package com.catsblock.materialex.data

import com.catsblock.materialex.core.permission.PermissionRecord
import com.catsblock.materialex.core.permission.PermissionState
import com.catsblock.materialex.core.permission.PermissionStore
import com.catsblock.materialex.core.permission.PermissionType
import com.catsblock.materialex.data.db.SitePermission
import com.catsblock.materialex.data.db.SitePermissionDao

class RoomPermissionStore(private val dao: SitePermissionDao) : PermissionStore {
    override suspend fun get(profileId: String, origin: String, type: PermissionType): PermissionRecord =
        dao.find(profileId, origin, type.id)?.toRecord() ?: PermissionRecord()

    override suspend fun put(profileId: String, origin: String, type: PermissionType, record: PermissionRecord) {
        dao.upsert(SitePermission(profileId, origin, type.id, record.state.name, record.denialCount))
    }

    override suspend fun listForOrigin(profileId: String, origin: String): Map<PermissionType, PermissionRecord> =
        dao.forOrigin(profileId, origin).mapNotNull { row ->
            PermissionType.fromId(row.type)?.let { it to row.toRecord() }
        }.toMap()

    override suspend fun clearProfile(profileId: String) = dao.clearProfile(profileId)

    private fun SitePermission.toRecord(): PermissionRecord =
        PermissionRecord(
            state = runCatching { PermissionState.valueOf(state) }.getOrDefault(PermissionState.ASK),
            denialCount = denialCount.coerceAtLeast(0)
        )
}
