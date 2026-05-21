package dev.ambitionsoftware.tymeboxed.service.inapp

import android.content.Context
import dev.ambitionsoftware.tymeboxed.service.ActiveBlockingState

/**
 * Standalone in-app blocking: when [InAppToggleKeys.KEY_BLOCK_INAPP] is on, surface rules
 * (Shorts, Reels, etc.) apply without an active focus session. When it is off, behavior
 * matches the original product — in-app surfaces are enforced only during a session.
 */
object InAppBlockingEnforcementGate {

    fun isStandaloneModeActive(context: Context): Boolean =
        InAppBlockingPreferencesReader.isEnabled(context, InAppToggleKeys.KEY_BLOCK_INAPP, false)

    fun shouldEnforceInAppSurfaces(context: Context): Boolean {
        val snap = ActiveBlockingState.current
        if (snap.isBlocking && !snap.isPauseActive) return true
        return isStandaloneModeActive(context)
    }
}
