package dev.ambitionsoftware.tymeboxed.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ambitionsoftware.tymeboxed.ui.theme.ThemeController

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ThemeEntryPoint {
    fun themeController(): ThemeController
}

