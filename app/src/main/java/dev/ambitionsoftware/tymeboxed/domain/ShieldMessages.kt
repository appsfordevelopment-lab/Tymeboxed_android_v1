package dev.ambitionsoftware.tymeboxed.domain

import java.util.Calendar

/**
 * Curated shield copy for the blocker overlay — mirrors
 * [Foqos ShieldConfigurationExtension](https://github.com/awaseem/foqos/blob/main/FoqosShieldConfig/ShieldConfigurationExtension.swift).
 */
data class ShieldBlockMessage(
    val emoji: String,
    val title: String,
    val subtitle: String,
    val buttonText: String,
)

object ShieldMessages {

    private data class Template(
        val emoji: String,
        val title: String,
        val subtitleTemplate: String,
        val buttonText: String,
    )

    private val templates = listOf(
        Template("📵", "Not right now", "%s can wait. You’re choosing your time on purpose.", "Back"),
        Template("🧠", "Brain check", "Do you actually want %s… or was it autopilot?", "Return"),
        Template("🎯", "Stay on target", "One small step toward your goal first. Then decide on %s.", "Continue"),
        Template("⏳", "Give it 2 minutes", "Finish the next tiny thing. %s will still be there after.", "Keep going"),
        Template("🛡️", "Shield up", "Focus is protected. You’ve got this.", "Onward"),
        Template("🔒", "Locked in", "This block is temporary. Your momentum isn’t.", "Stay here"),
        Template("🧱", "Boundary set", "You made a plan. This is you sticking to it.", "Back"),
        Template("✨", "Glow mode", "You’re building attention — that’s the real flex.", "Nice"),
        Template("🫶", "Be kind to you", "No shame. Just a gentle nudge back to what matters.", "Got it"),
        Template("🌐", "Not this detour", "%s isn’t part of the mission right now.", "Return"),
        Template("🕸️", "Avoid the trap", "One click turns into twenty. Let’s not.", "Back"),
        Template("🛡️", "Protected zone", "We’re keeping your attention where you wanted it.", "Got it"),
        Template("🔒", "Locked in", "This is a temporary block for a long-term win.", "Return"),
        Template("🎯", "Back to the task", "Close the detour. Finish the task. Then come back on purpose.", "Back to work"),
        Template("⏳", "Protect the time", "A few minutes can become an hour. Keep your momentum.", "Stay focused"),
        Template("📵", "Not missing anything", "You’re not missing anything important right now.", "Back"),
        Template("✨", "Momentum mode", "Tiny choices like this add up fast.", "Continue"),
    )

    fun forApp(appTitle: String): ShieldBlockMessage = pick(appTitle.ifBlank { "App" })

    fun forWebsite(domain: String): ShieldBlockMessage = pick(domain.ifBlank { "Website" })

    private fun pick(title: String): ShieldBlockMessage {
        if (templates.isEmpty()) {
            return ShieldBlockMessage("🧠", "Quick pause", "Not right now.", "Back")
        }
        val dayKey = Calendar.getInstance().run {
            (get(Calendar.YEAR) * 10_000) +
                (get(Calendar.MONTH) + 1) * 100 +
                get(Calendar.DAY_OF_MONTH)
        }
        val seed = (stableSeed(title) % Int.MAX_VALUE.toLong()).toInt() xor dayKey
        // abs(Int.MIN_VALUE) stays negative; Kotlin % can be negative — mask instead.
        val template = templates[(seed and Int.MAX_VALUE) % templates.size]
        val subtitle = if (template.subtitleTemplate.contains("%s")) {
            template.subtitleTemplate.format(title)
        } else {
            template.subtitleTemplate
        }
        return ShieldBlockMessage(
            emoji = template.emoji,
            title = template.title,
            subtitle = subtitle,
            buttonText = template.buttonText,
        )
    }

    /** FNV-1a 64-bit — same approach as Foqos [stableSeed]. */
    private fun stableSeed(title: String): Long {
        var hash = 14695998103946656037uL
        for (ch in title) {
            hash = hash xor ch.code.toULong()
            hash *= 1099511628211uL
        }
        return hash.toLong()
    }
}
