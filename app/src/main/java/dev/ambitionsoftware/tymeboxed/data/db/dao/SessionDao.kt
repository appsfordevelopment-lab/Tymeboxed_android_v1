package dev.ambitionsoftware.tymeboxed.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.ambitionsoftware.tymeboxed.data.db.entities.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions WHERE endTime IS NULL LIMIT 1")
    suspend fun findActive(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE endTime IS NULL LIMIT 1")
    fun observeActive(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE profileId = :profileId ORDER BY startTime DESC")
    fun observeForProfile(profileId: String): Flow<List<SessionEntity>>

    @Query(
        """
        SELECT * FROM sessions
        WHERE profileId = :profileId
        AND endTime IS NOT NULL
        AND startTime >= :startMs AND startTime < :endMs
        ORDER BY startTime ASC
        """,
    )
    fun observeCompletedSessionsForProfileBetween(
        profileId: String,
        startMs: Long,
        endMs: Long,
    ): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE endTime IS NOT NULL ORDER BY endTime DESC LIMIT 50")
    fun observeRecentCompleted(): Flow<List<SessionEntity>>

    /**
     * All completed sessions since [sinceMs] (inclusive of start), any profile.
     * Used for home Activity chart aggregation.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE endTime IS NOT NULL
        AND endTime > startTime
        AND startTime >= :sinceMs
        ORDER BY startTime ASC
        """,
    )
    fun observeCompletedSince(sinceMs: Long): Flow<List<SessionEntity>>

    /** New sessions only — updates use [update]. ABORT surfaces accidental UUID reuse. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    /** Clears any stray active session. Used by Settings > Troubleshooting > Reset Blocking State. */
    @Query("UPDATE sessions SET endTime = :endTime WHERE endTime IS NULL")
    suspend fun endAllActive(endTime: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query(
        "SELECT COUNT(*) FROM sessions WHERE profileId = :profileId AND endTime IS NOT NULL",
    )
    suspend fun countCompletedForProfile(profileId: String): Int
}
