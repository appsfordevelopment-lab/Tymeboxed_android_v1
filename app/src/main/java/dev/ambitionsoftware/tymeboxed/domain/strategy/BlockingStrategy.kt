package dev.ambitionsoftware.tymeboxed.domain.strategy

import dev.ambitionsoftware.tymeboxed.domain.model.BlockingStrategyId

/**
 * Catalog of blocking strategies surfaced to the user. Phase 1 declares the
 * metadata so the profile editor and the home carousel can render strategy
 * rows; actual start/stop execution lands in Phase 3 via StrategyCoordinator.
 *
 * Ordering of [pickable] controls the profile-editor list order — matches
 * iOS `StrategyManager.availableStrategies` from
 * `TymeBoxed/Utils/StrategyManager.swift`.
 */
sealed class BlockingStrategy(
    val id: String,
    val name: String,
    val description: String,
    val usesNfc: Boolean,
    val hasTimer: Boolean,
    val hasManualStart: Boolean,
    val hidden: Boolean = false,
) {
    /** Manual start/stop from within the app. Hidden from the picker (automation-only). */
    object Manual : BlockingStrategy(
        id = BlockingStrategyId.MANUAL,
        name = "Manual",
        description = "Start and stop manually from inside the app.",
        usesNfc = false,
        hasTimer = false,
        hasManualStart = true,
        hidden = true,
    )

    /** Lock and unlock by scanning a tymeboxed device. */
    object NfcUnlock : BlockingStrategy(
        id = BlockingStrategyId.NFC_UNLOCK,
        name = "Tyme Boxed Mode",
        description = "Lock and unlock using your device.",
        usesNfc = true,
        hasTimer = false,
        hasManualStart = false,
    )

    /** Start in-app; unlock by scanning a device. */
    object NfcManualStart : BlockingStrategy(
        id = BlockingStrategyId.NFC_MANUAL_START,
        name = "Tyme Boxed + Manual Start",
        description = "Lock manually, then scan the device to unlock.",
        usesNfc = true,
        hasTimer = false,
        hasManualStart = true,
    )

    /** Pick a duration; end early by scanning a device. */
    object FocusTimer : BlockingStrategy(
        id = BlockingStrategyId.FOCUS_TIMER,
        name = "Focus Session",
        description = "Set a focus duration, then scan the device to end early.",
        usesNfc = true,
        hasTimer = true,
        hasManualStart = true,
    )

    /** Timer with break support; scan once for break, scan again to stop. */
    object FocusTimerBreak : BlockingStrategy(
        id = BlockingStrategyId.FOCUS_TIMER_BREAK,
        name = "Focus session with Break",
        description = "Set a break duration, scan device for break, scan again to fully stop.",
        usesNfc = true,
        hasTimer = true,
        hasManualStart = false,
    )

    companion object {
        val all: List<BlockingStrategy> = listOf(
            Manual, NfcUnlock, NfcManualStart, FocusTimer, FocusTimerBreak,
        )

        /** Strategies that show up in the profile editor picker. */
        val pickable: List<BlockingStrategy> = all.filterNot { it.hidden }

        fun fromId(id: String): BlockingStrategy =
            all.firstOrNull { it.id == id } ?: NfcUnlock
    }
}
