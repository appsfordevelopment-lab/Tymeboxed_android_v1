package dev.ambitionsoftware.tymeboxed.permissions

/**
 * Every Android permission the app cares about, with user-facing copy.
 *
 * [required] is always true for every entry: the intro wizard and session gate
 * require all of these. Devices without NFC hardware treat NFC as satisfied
 * automatically (see [PermissionsCoordinator]).
 */
enum class TymePermission(
    val key: String,
    val title: String,
    val description: String,
    val required: Boolean,
) {
    ACCESSIBILITY(
        key = "accessibility",
        title = "Accessibility",
        description = "Sends you home when blocked apps open.",
        required = true,
    ),
    USAGE_STATS(
        key = "usage_stats",
        title = "App usage access",
        description = "Detects which app is in the foreground.",
        required = true,
    ),
    NOTIFICATIONS(
        key = "notifications",
        title = "Notifications",
        description = "Shows your active focus session.",
        required = true,
    ),
    NFC(
        key = "nfc",
        title = "NFC",
        description = "Required for scanning Tyme Boxed Device.",
        required = true,
    ),
    EXACT_ALARMS(
        key = "exact_alarms",
        title = "Alarms & reminders",
        description = "Starts and ends sessions on time.",
        required = true,
    ),
    DEVICE_ADMIN(
        key = "device_admin",
        title = "Admin access",
        description = "Required to prevent bypass/uninstall during Strict mode sessions. When enabled, uninstalling Tyme Boxed requires deactivating admin first.",
        required = true,
    );

    companion object {
        val requiredPermissions: List<TymePermission> = entries.filter { it.required }
    }
}
