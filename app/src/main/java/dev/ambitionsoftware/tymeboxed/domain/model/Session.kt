package dev.ambitionsoftware.tymeboxed.domain.model

import dev.ambitionsoftware.tymeboxed.data.db.entities.SessionEntity

/**
 * Domain model for a focus session. Kept thin — the strategy coordinator
 * (Phase 3) will enrich this with computed state like elapsed time.
 */
data class Session(
    val id: String,
    val profileId: String,
    val startTime: Long,
    val endTime: Long? = null,
    val isPauseActive: Boolean = false,
    val pauseStartTime: Long? = null,
) {
    val isActive: Boolean get() = endTime == null
}

fun SessionEntity.toDomain(): Session = Session(
    id = id,
    profileId = profileId,
    startTime = startTime,
    endTime = endTime,
    isPauseActive = isPauseActive,
    pauseStartTime = pauseStartTime,
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    profileId = profileId,
    startTime = startTime,
    endTime = endTime,
    isPauseActive = isPauseActive,
    pauseStartTime = pauseStartTime,
)
