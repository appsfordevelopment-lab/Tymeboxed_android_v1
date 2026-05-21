package dev.ambitionsoftware.tymeboxed.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.ambitionsoftware.tymeboxed.data.db.ProfileWithApps
import dev.ambitionsoftware.tymeboxed.data.db.entities.BlockedAppEntity
import dev.ambitionsoftware.tymeboxed.data.db.entities.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Transaction
    @Query("SELECT * FROM profiles ORDER BY `order` ASC, createdAt DESC")
    fun observeAllWithApps(): Flow<List<ProfileWithApps>>

    @Transaction
    @Query("SELECT * FROM profiles ORDER BY `order` ASC, createdAt DESC")
    suspend fun getAllWithAppsSnapshot(): List<ProfileWithApps>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    fun observeByIdWithApps(id: String): Flow<ProfileWithApps?>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getByIdWithApps(id: String): ProfileWithApps?

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(scheduleAlarmRequestCode), 0) FROM profiles")
    suspend fun maxScheduleAlarmRequestCode(): Int

    suspend fun allocateScheduleAlarmRequestCode(): Int = maxScheduleAlarmRequestCode() + 1

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfile(profile: ProfileEntity)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun deleteAllProfiles()

    /**
     * Deletes a profile row; [blocked_apps] children are removed via FK CASCADE.
     */
    @Transaction
    suspend fun deleteProfileInTransaction(profile: ProfileEntity) {
        deleteProfile(profile)
    }

    /** Wipes all profiles (and blocked_apps via CASCADE). */
    @Transaction
    suspend fun deleteAllProfilesInTransaction() {
        deleteAllProfiles()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedApps(apps: List<BlockedAppEntity>)

    @Query("DELETE FROM blocked_apps WHERE profileId = :profileId")
    suspend fun clearBlockedApps(profileId: String)

    /**
     * Inserts or updates a profile row and atomically rewrites its
     * [BlockedAppEntity] children.
     *
     * Wrapping these three operations in a Room `@Transaction` guarantees
     * that we never end up with a saved profile pointing at an empty
     * blocked-apps table if the process is killed mid-write
     * (audit #27: `ProfileRepository.save()` not transactional).
     */
    @Transaction
    suspend fun upsertProfileWithBlockedApps(
        profile: ProfileEntity,
        existingProfile: ProfileEntity?,
        blockedApps: List<BlockedAppEntity>,
    ) {
        var profileToSave = profile
        if (profileToSave.scheduleAlarmRequestCode <= 0) {
            val preserved = existingProfile?.scheduleAlarmRequestCode ?: 0
            profileToSave = profileToSave.copy(
                scheduleAlarmRequestCode = preserved.takeIf { it > 0 }
                    ?: allocateScheduleAlarmRequestCode(),
            )
        }
        if (existingProfile == null) {
            insertProfile(profileToSave)
        } else {
            updateProfile(profileToSave)
        }
        clearBlockedApps(profileToSave.id)
        if (blockedApps.isNotEmpty()) {
            insertBlockedApps(blockedApps)
        }
    }
}
