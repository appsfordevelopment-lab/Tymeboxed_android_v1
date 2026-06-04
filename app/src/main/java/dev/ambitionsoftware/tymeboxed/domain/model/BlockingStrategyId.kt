package dev.ambitionsoftware.tymeboxed.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Canonical string identifiers for blocking strategies. These values are
 * persisted in [dev.ambitionsoftware.tymeboxed.data.db.entities.ProfileEntity.strategyId],
 * so treat them as an append-only contract — renaming breaks existing
 * profiles on-device.
 *
 * Mirrors the iOS strategy `id` pattern from
 * `TymeBoxed/Models/Strategies/BlockingStrategy.swift`, NFC strategies only.
 */
object BlockingStrategyId {
    const val MANUAL = "manual"
    const val NFC_UNLOCK = "nfc_unlock"
    const val NFC_MANUAL_START = "nfc_manual_start"
    const val FOCUS_TIMER = "focus_timer"
    const val FOCUS_TIMER_BREAK = "focus_timer_break"

    /** Default strategy for a brand-new profile. */
    const val DEFAULT: String = NFC_MANUAL_START

    val all: List<String> = listOf(
        NFC_UNLOCK, NFC_MANUAL_START, FOCUS_TIMER, FOCUS_TIMER_BREAK, MANUAL,
    )
}

/** Tag labels shown as capsules on each strategy row, matching iOS. */
enum class StrategyTag(val label: String) {
    DEVICE("Device"),
    MANUAL("Manual"),
    TIMER("Timer"),
    FOREVER("Forever"),
    BREAK("Break"),
}

/**
 * Display metadata for a blocking strategy — icon, color, name, tags, and
 * description. Matches iOS strategy presentation in the profile editor.
 */
data class StrategyInfo(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val color: Color,
    val tags: List<StrategyTag> = emptyList(),
    val hidden: Boolean = false,
)

/** All strategies — visible ones appear in the profile editor picker. */
val availableStrategies: List<StrategyInfo> = listOf(
    StrategyInfo(
        id = BlockingStrategyId.NFC_UNLOCK,
        name = "Tyme Boxed Mode",
        description = "Lock and unlock using your device.",
        icon = "nfc",
        color = Color(0xFFF5A623), // yellow
        tags = listOf(StrategyTag.DEVICE),
        hidden = true,
    ),
    StrategyInfo(
        id = BlockingStrategyId.NFC_MANUAL_START,
        name = "Manual start, tap to unlock",
        description = "Start a session from the app. Scan your device to end it.",
        icon = "nfc",
        color = Color(0xFFF5A623), // yellow
        tags = listOf(StrategyTag.MANUAL),
    ),
    StrategyInfo(
        id = BlockingStrategyId.FOCUS_TIMER,
        name = "Focus Session",
        description = "Set a focus duration. Blocks end automatically - scan to end early.",
        icon = "timer",
        color = Color(0xFF4CD9AC), // mint
        tags = listOf(StrategyTag.TIMER),
    ),
    StrategyInfo(
        id = BlockingStrategyId.FOCUS_TIMER_BREAK,
        name = "Forever session with break",
        description = "Forever session. Scan once for break, scan again to stop.",
        icon = "pause",
        color = Color(0xFFFF9500), // orange
        tags = listOf(StrategyTag.FOREVER, StrategyTag.BREAK),
    ),
    StrategyInfo(
        id = BlockingStrategyId.MANUAL,
        name = "Manual",
        description = "Start and stop blocking directly in the app.",
        icon = "touch_app",
        color = Color(0xFF888888),
        hidden = true,
    ),
)

/** Lookup a strategy's display info by ID. */
fun strategyInfoById(id: String): StrategyInfo? =
    availableStrategies.find { it.id == id }
