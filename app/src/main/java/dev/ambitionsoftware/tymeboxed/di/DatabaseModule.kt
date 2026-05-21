package dev.ambitionsoftware.tymeboxed.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ambitionsoftware.tymeboxed.data.db.Migrations
import dev.ambitionsoftware.tymeboxed.data.db.TymeBoxedDatabase
import dev.ambitionsoftware.tymeboxed.data.db.dao.ProfileDao
import dev.ambitionsoftware.tymeboxed.data.db.dao.SessionDao
import dev.ambitionsoftware.tymeboxed.data.db.dao.TagDao
import javax.inject.Singleton

/**
 * Provides the Room database and its DAOs to the rest of the graph.
 *
 * Repositories (`ProfileRepository`, `SessionRepository`) have `@Inject
 * constructor` annotations, so Hilt can build them automatically once the
 * DAOs are available here — no extra `@Binds` module needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): TymeBoxedDatabase =
        Room.databaseBuilder(
            context,
            TymeBoxedDatabase::class.java,
            TymeBoxedDatabase.DB_NAME,
        )
            // Every future schema bump MUST add a Migration to Migrations.ALL.
            .addMigrations(*Migrations.ALL)
            // Versions 1 and 2 shipped without migrations, so allow a wipe only
            // when upgrading from those legacy versions (small dev / TestFlight
            // surface). For released versions ≥ 3, missing migrations should
            // crash loudly rather than silently destroy user data.
            .fallbackToDestructiveMigrationFrom(1, 2)
            // Wipe on downgrade as well — downgrades happen only via sideloads
            // and never via the Play Store, so this is preferable to a crash.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideProfileDao(db: TymeBoxedDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideSessionDao(db: TymeBoxedDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideTagDao(db: TymeBoxedDatabase): TagDao = db.tagDao()
}
