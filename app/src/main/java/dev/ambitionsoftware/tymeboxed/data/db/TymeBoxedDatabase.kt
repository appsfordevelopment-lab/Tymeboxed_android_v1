package dev.ambitionsoftware.tymeboxed.data.db

import android.app.Application
import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dagger.hilt.android.EntryPointAccessors
import dev.ambitionsoftware.tymeboxed.data.db.dao.ProfileDao
import dev.ambitionsoftware.tymeboxed.di.DatabaseEntryPoint
import dev.ambitionsoftware.tymeboxed.data.db.dao.SessionDao
import dev.ambitionsoftware.tymeboxed.data.db.dao.TagDao
import dev.ambitionsoftware.tymeboxed.data.db.entities.BlockedAppEntity
import dev.ambitionsoftware.tymeboxed.data.db.entities.ProfileEntity
import dev.ambitionsoftware.tymeboxed.data.db.entities.SessionEntity
import dev.ambitionsoftware.tymeboxed.data.db.entities.TagEntity

@Database(
    entities = [
        ProfileEntity::class,
        BlockedAppEntity::class,
        SessionEntity::class,
        TagEntity::class,
    ],
    version = 6,
    // Schemas are exported to app/schemas (configured via the
    // room.schemaLocation KSP arg in app/build.gradle.kts) and committed to
    // git so migrations can be reviewed and tested per version bump.
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TymeBoxedDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sessionDao(): SessionDao
    abstract fun tagDao(): TagDao

    companion object {
        const val DB_NAME = "tymeboxed.db"

        /**
         * Returns the Hilt-provided singleton. Multiple [RoomDatabase] instances for the
         * same file corrupt SQLite and can prevent the app from starting until data is cleared.
         */
        fun getInstance(context: Context): TymeBoxedDatabase =
            EntryPointAccessors.fromApplication(
                context.applicationContext as Application,
                DatabaseEntryPoint::class.java,
            ).database()
    }
}
