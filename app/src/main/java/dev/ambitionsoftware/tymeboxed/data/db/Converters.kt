package dev.ambitionsoftware.tymeboxed.data.db

import androidx.room.TypeConverter

/**
 * Room TypeConverters. Phase 1 entities only use primitives, so this class
 * is intentionally empty — keeping the file around lets future types (Date,
 * enums, lists) land without a schema migration that swaps in a
 * `@TypeConverters` annotation on the database class.
 */
class Converters {
    @TypeConverter
    fun noop(value: Int?): Int? = value
}
