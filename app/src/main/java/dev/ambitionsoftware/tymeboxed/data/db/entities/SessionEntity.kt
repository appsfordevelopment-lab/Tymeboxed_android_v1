package dev.ambitionsoftware.tymeboxed.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single focus session. While [endTime] is null the session is active and
 * the blocking engine is running. Timestamps are stored as epoch milliseconds
 * to sidestep Room TypeConverters in Phase 1.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId"), Index("endTime")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val isPauseActive: Boolean = false,
    val pauseStartTime: Long? = null,
) {
    val isActive: Boolean get() = endTime == null
}
