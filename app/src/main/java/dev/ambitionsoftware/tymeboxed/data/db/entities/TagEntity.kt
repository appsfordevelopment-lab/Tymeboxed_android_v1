package dev.ambitionsoftware.tymeboxed.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Legacy on-device NFC tag row (optional / future use). Session flows do not
 * require registration in this table.
 */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val tagUid: String,
    val registeredAt: Long,
    val label: String? = null,
)
