package dev.ambitionsoftware.tymeboxed.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ambitionsoftware.tymeboxed.data.db.TymeBoxedDatabase

/** Lets non-Hilt entry points obtain the same [TymeBoxedDatabase] singleton as DI. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun database(): TymeBoxedDatabase
}
