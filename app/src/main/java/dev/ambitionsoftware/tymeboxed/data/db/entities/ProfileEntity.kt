package dev.ambitionsoftware.tymeboxed.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Row representation of a blocking profile. Mirrors the fields of
 * `TymeBoxed/Models/BlockedProfiles.swift` in the iOS app, with the
 * FamilyControls-specific types replaced by plain package-name rows in
 * [BlockedAppEntity] and FamilyActivitySelection replaced by the
 * [isAllowMode] flag on this row.
 *
 * [strategyData] holds strategy-specific JSON (e.g. timer duration).
 * It stays a String so strategies can evolve without schema migrations.
 *
 * [domains] is a comma-separated list of blocked (or allowed) domains.
 */
@Entity(
    tableName = "profiles",
    indices = [
        Index(value = ["scheduleAlarmRequestCode"], unique = true),
    ],
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val strategyId: String,
    val strategyData: String? = null,

    val enableStrictMode: Boolean = true,
    val enableLiveActivity: Boolean = false,
    val enableBreaks: Boolean = false,
    val breakTimeInMinutes: Int = 15,

    val reminderTimeSeconds: Int? = null,
    val customReminderMessage: String? = null,

    /** When null, any NFC tag can end the session. When set, only this tag ends it. */
    val physicalUnblockNfcTagId: String? = null,

    val isAllowMode: Boolean = false,
    val isAllowModeDomains: Boolean = false,
    /** When true, common adult sites are blocked in browsers during sessions. */
    val blockAdultWebsites: Boolean = false,
    val domains: String? = null, // comma-separated
    val order: Int = 0,
    val accentColorHex: String? = null,

    /** JSON [dev.ambitionsoftware.tymeboxed.domain.model.ProfileSchedule]; null = no schedule. */
    val scheduleJson: String? = null,

    /**
     * Stable [PendingIntent] request-code slot for [ProfileScheduleAlarmScheduler].
     * Assigned once at insert; never derived from [id] (hash collisions overwrite alarms).
     * 0 means unassigned (legacy rows are backfilled on migration).
     */
    val scheduleAlarmRequestCode: Int = 0,
)
