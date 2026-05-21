package dev.ambitionsoftware.tymeboxed.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ambitionsoftware.tymeboxed.service.SessionTimerHandler

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceBridgeEntryPoint {
    fun sessionTimerHandler(): SessionTimerHandler
}
