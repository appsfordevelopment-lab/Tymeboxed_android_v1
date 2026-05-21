package dev.ambitionsoftware.tymeboxed.data.repository

import android.database.sqlite.SQLiteConstraintException
import dev.ambitionsoftware.tymeboxed.data.db.dao.SessionDao
import dev.ambitionsoftware.tymeboxed.domain.model.Session
import dev.ambitionsoftware.tymeboxed.domain.model.toDomain
import dev.ambitionsoftware.tymeboxed.domain.model.toEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
) {
    val activeSession: Flow<Session?> =
        sessionDao.observeActive().map { it?.toDomain() }

    suspend fun findActive(): Session? = sessionDao.findActive()?.toDomain()

    suspend fun insert(session: Session) {
        try {
            sessionDao.insert(session.toEntity())
        } catch (e: SQLiteConstraintException) {
            throw IllegalStateException(
                "Session id '${session.id}' already exists; use update() instead of insert().",
                e,
            )
        }
    }

    suspend fun update(session: Session) {
        sessionDao.update(session.toEntity())
    }

    /**
     * Closes any lingering active session (end-time set to now). Used by
     * the Settings > Troubleshooting > Reset Blocking State action. Phase 3
     * will also tear down the foreground service and AccessibilityService
     * state on the same call.
     */
    suspend fun resetActive() {
        sessionDao.endAllActive(System.currentTimeMillis())
    }

    /** Wipes all session history (activity). Used by Settings > Delete Account. */
    suspend fun deleteAll() {
        sessionDao.deleteAll()
    }

    suspend fun countCompletedForProfile(profileId: String): Int =
        sessionDao.countCompletedForProfile(profileId)

    fun observeCompletedSessionsForProfileBetween(
        profileId: String,
        startMs: Long,
        endMs: Long,
    ): Flow<List<Session>> =
        sessionDao.observeCompletedSessionsForProfileBetween(profileId, startMs, endMs)
            .map { list -> list.map { it.toDomain() } }

    fun observeSessionsForProfile(profileId: String): Flow<List<Session>> =
        sessionDao.observeForProfile(profileId).map { list -> list.map { it.toDomain() } }

    /**
     * Completed sessions (all profiles) with [Session.startTime] &gt;= [sinceMs], for home Activity charts.
     */
    fun observeCompletedSince(sinceMs: Long): Flow<List<Session>> =
        sessionDao.observeCompletedSince(sinceMs).map { list -> list.map { it.toDomain() } }
}
