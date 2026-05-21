package dev.ambitionsoftware.tymeboxed.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for [TymeBoxedDatabase].
 *
 * Schema JSONs are exported by KSP to `app/schemas/<db-class>/<version>.json`
 * (configured in `app/build.gradle.kts`) and committed to git. Every time the
 * `@Database(version = N)` is bumped, a new `MIGRATION_<N-1>_<N>` must be
 * added here and registered in [ALL].
 *
 * Historical note: versions 1 → 3 shipped without migration objects, so
 * upgrades from those versions are handled via
 * `fallbackToDestructiveMigrationFrom(1, 2)` in [DatabaseModule]. All future
 * version bumps MUST ship a real migration.
 */
object Migrations {

    /**
     * v3 → v4: add a UNIQUE composite index on `(profileId, packageName)` in
     * `blocked_apps` to prevent duplicate rows that could otherwise be
     * inserted by a racing profile save + user re-toggling the same app.
     *
     * Pre-deduplicate any duplicates that snuck in on v3 before creating the
     * unique index — otherwise the index creation will fail at install time.
     */
    private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                DELETE FROM blocked_apps
                WHERE id NOT IN (
                    SELECT MIN(id) FROM blocked_apps
                    GROUP BY profileId, packageName
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_blocked_apps_profileId_packageName` " +
                    "ON `blocked_apps` (`profileId`, `packageName`)"
            )
        }
    }

    /**
     * v4 → v5: assign each profile a unique, persistent alarm [PendingIntent] request code
     * so scheduled start/end alarms cannot collide via `profileId.hashCode()`.
     */
    private val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE profiles ADD COLUMN scheduleAlarmRequestCode INTEGER NOT NULL DEFAULT 0",
            )
            db.query(
                "SELECT id FROM profiles ORDER BY createdAt ASC, id ASC",
            ).use { cursor ->
                var code = 1
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    db.execSQL(
                        "UPDATE profiles SET scheduleAlarmRequestCode = $code WHERE id = ?",
                        arrayOf(id),
                    )
                    code++
                }
            }
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_profiles_scheduleAlarmRequestCode " +
                    "ON profiles (scheduleAlarmRequestCode)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_3_4, MIGRATION_4_5)
}
