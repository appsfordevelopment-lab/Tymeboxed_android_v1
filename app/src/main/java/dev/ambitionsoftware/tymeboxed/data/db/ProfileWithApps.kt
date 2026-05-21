package dev.ambitionsoftware.tymeboxed.data.db

import androidx.room.Embedded
import androidx.room.Relation
import dev.ambitionsoftware.tymeboxed.data.db.entities.BlockedAppEntity
import dev.ambitionsoftware.tymeboxed.data.db.entities.ProfileEntity

/**
 * Room @Relation holder for a profile plus its child [BlockedAppEntity] rows.
 * Used by [dev.ambitionsoftware.tymeboxed.data.db.dao.ProfileDao.observeAllWithApps].
 */
data class ProfileWithApps(
    @Embedded val profile: ProfileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "profileId",
    )
    val blockedApps: List<BlockedAppEntity>,
)
