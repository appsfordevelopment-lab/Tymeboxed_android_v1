package dev.ambitionsoftware.tymeboxed.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single package name attached to a profile's blocklist (or allowlist, if
 * [ProfileEntity.isAllowMode] is true).
 *
 * Stored as child rows instead of a JSON column so Room can query / index
 * "all profiles blocking package X" efficiently — the blocking engine (Phase 3)
 * will hit this query on every TYPE_WINDOW_STATE_CHANGED event.
 */
@Entity(
    tableName = "blocked_apps",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        // SQLite does not auto-index FK columns on the child side, so
        // [profileId] needs an explicit index to avoid a table scan when the
        // blocking engine asks "which apps does this profile block?" on every
        // TYPE_WINDOW_STATE_CHANGED event.
        Index("profileId"),
        // [packageName] alone is also useful for the inverse lookup
        // ("which profiles block this package?").
        Index("packageName"),
        // Unique composite index — guards against duplicate (profile, package)
        // rows that could otherwise be inserted by a racing save + a
        // user re-toggling the same app in [ProfileEditViewModel].
        Index(value = ["profileId", "packageName"], unique = true),
    ],
)
data class BlockedAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val packageName: String,
)
