package dev.ambitionsoftware.tymeboxed.data.repository

import androidx.room.withTransaction
import dev.ambitionsoftware.tymeboxed.data.db.TymeBoxedDatabase
import dev.ambitionsoftware.tymeboxed.data.db.dao.ProfileDao
import dev.ambitionsoftware.tymeboxed.data.db.entities.BlockedAppEntity
import dev.ambitionsoftware.tymeboxed.domain.model.Profile
import dev.ambitionsoftware.tymeboxed.domain.model.withBreakFlagsFromStrategy
import dev.ambitionsoftware.tymeboxed.domain.model.toDomain
import dev.ambitionsoftware.tymeboxed.domain.model.toEntity
import dev.ambitionsoftware.tymeboxed.service.ProfileScheduleAlarmScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for [Profile] + its blocked-apps rows.
 *
 * All writes go through [save], which handles insert-or-update and rewrites
 * the child `blocked_apps` rows atomically on the Room side (via the DAO's
 * cascade delete + re-insert).
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val database: TymeBoxedDatabase,
    private val profileDao: ProfileDao,
    private val sessionRepository: SessionRepository,
    private val scheduleAlarmScheduler: ProfileScheduleAlarmScheduler,
) {
    fun observeAll(): Flow<List<Profile>> =
        profileDao.observeAllWithApps().map { list ->
            list.map { it.toDomain() }
        }

    fun observeById(id: String): Flow<Profile?> =
        profileDao.observeByIdWithApps(id).map { it?.toDomain() }

    suspend fun findById(id: String): Profile? =
        profileDao.getByIdWithApps(id)?.toDomain()

    suspend fun count(): Int = profileDao.count()

    suspend fun save(profile: Profile) {
        val normalized = profile.withBreakFlagsFromStrategy()
        val existing = profileDao.findById(normalized.id)
        val entity = normalized.toEntity().copy(
            updatedAt = System.currentTimeMillis(),
            createdAt = existing?.createdAt ?: normalized.createdAt,
            scheduleAlarmRequestCode = existing?.scheduleAlarmRequestCode ?: 0,
        )
        val children = normalized.blockedPackages.map { pkg ->
            BlockedAppEntity(profileId = normalized.id, packageName = pkg)
        }
        // Single Room transaction → no half-written profile / orphaned children
        // if the process is killed between the insert and the child rewrite.
        profileDao.upsertProfileWithBlockedApps(
            profile = entity,
            existingProfile = existing,
            blockedApps = children,
        )
        scheduleAlarmScheduler.rescheduleAll()
    }

    suspend fun delete(id: String) {
        val active = sessionRepository.findActive()
        if (active != null && active.profileId == id) {
            error(
                "Cannot delete this profile while its focus session is active. End the session first.",
            )
        }
        val entity = profileDao.findById(id) ?: return
        scheduleAlarmScheduler.cancelAlarmsForProfile(id, entity.scheduleAlarmRequestCode)
        database.withTransaction {
            profileDao.deleteProfileInTransaction(entity)
        }
        scheduleAlarmScheduler.rescheduleAll()
    }

    suspend fun deleteAll() {
        val profiles = profileDao.getAllWithAppsSnapshot()
        profiles.forEach {
            scheduleAlarmScheduler.cancelAlarmsForProfile(
                it.profile.id,
                it.profile.scheduleAlarmRequestCode,
            )
        }
        database.withTransaction {
            profileDao.deleteAllProfilesInTransaction()
        }
        scheduleAlarmScheduler.rescheduleAll()
    }
}
