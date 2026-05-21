package dev.ambitionsoftware.tymeboxed.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.admin.DevicePolicyManager
import dev.ambitionsoftware.tymeboxed.admin.DeviceAdminSupport

/**
 * Deep-link intents for every [TymePermission]. Each returns the Intent that,
 * when launched, lands the user on the correct Android system settings page
 * to grant the permission in question.
 *
 * POST_NOTIFICATIONS is the exception: on Android 13+ it uses the runtime
 * permission dialog (handled in the Compose layer with ActivityResultContracts),
 * so [intentFor] returns the app's notification settings page as a fallback
 * for users who previously denied the prompt.
 */
object PermissionIntents {

    fun intentFor(context: Context, perm: TymePermission): Intent {
        return intentsFor(context, perm).first()
    }

    fun intentsFor(context: Context, perm: TymePermission): List<Intent> {
        return when (perm) {
            TymePermission.ACCESSIBILITY ->
                listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).forLaunch(context))

            TymePermission.USAGE_STATS ->
                listOf(usageAccessSettingsIntent(context))

            TymePermission.NOTIFICATIONS ->
                listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    forLaunch(context)
                })

            TymePermission.NFC ->
                listOf(Intent(Settings.ACTION_NFC_SETTINGS).forLaunch(context))

            TymePermission.EXACT_ALARMS ->
                listOf(exactAlarmSettingsIntent(context))

            TymePermission.DEVICE_ADMIN -> {
                val active = DeviceAdminSupport.isAdminActive(context)
                if (active) {
                    listOf(
                        Intent("android.settings.DEVICE_ADMIN_SETTINGS").forLaunch(context),
                        Intent().setClassName(
                            "com.android.settings",
                            "com.android.settings.Settings\$DeviceAdminSettingsActivity",
                        ).forLaunch(context),
                        Intent().setClassName(
                            "com.android.settings",
                            "com.android.settings.DeviceAdminSettings",
                        ).forLaunch(context),
                        Intent(Settings.ACTION_SECURITY_SETTINGS).forLaunch(context),
                        Intent(Settings.ACTION_SETTINGS).forLaunch(context),
                        appDetailsIntent(context),
                    )
                } else {
                    listOf(
                        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(
                                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                DeviceAdminSupport.adminComponent(context),
                            )
                            putExtra(
                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Enabling admin access helps prevent uninstall bypass during Strict mode sessions.",
                            )
                            forLaunch(context)
                        },
                        Intent("android.settings.DEVICE_ADMIN_SETTINGS").forLaunch(context),
                        Intent().setClassName(
                            "com.android.settings",
                            "com.android.settings.Settings\$DeviceAdminSettingsActivity",
                        ).forLaunch(context),
                        Intent().setClassName(
                            "com.android.settings",
                            "com.android.settings.DeviceAdminSettings",
                        ).forLaunch(context),
                        Intent(Settings.ACTION_SECURITY_SETTINGS).forLaunch(context),
                        Intent(Settings.ACTION_SETTINGS).forLaunch(context),
                        appDetailsIntent(context),
                    )
                }
            }
        }
    }

    data class StartResult(
        val started: Boolean,
        val lastError: Throwable? = null,
        val attemptedActions: List<String> = emptyList(),
    )

    fun tryStart(context: Context, perm: TymePermission): StartResult {
        var last: Throwable? = null
        val attempted = ArrayList<String>(6)
        for (intent in intentsFor(context, perm)) {
            attempted.add(intent.action ?: "${intent.component?.packageName}/${intent.component?.className}")
            try {
                // Some OEM builds / Android versions reject special permission flows
                // if launched with FLAG_ACTIVITY_NEW_TASK from an Activity context.
                // We normalize flags at the last moment to keep callers simple.
                intent.forLaunch(context)
                context.startActivity(intent)
                return StartResult(started = true, attemptedActions = attempted)
            } catch (t: Throwable) {
                last = t
                // keep trying fallbacks
            }
        }
        return StartResult(started = false, lastError = last, attemptedActions = attempted)
    }

    private fun Intent.forLaunch(context: Context): Intent {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return this
    }

    private fun appDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            forLaunch(context)
        }
    }

    /**
     * Opens the “App usage access” / special-access screen. Many devices support
     * `package:` [Uri] so the user lands on the right row for this app; if no
     * activity resolves, fall back to the generic usage-access page.
     */
    private fun usageAccessSettingsIntent(context: Context): Intent {
        val withPkg = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            forLaunch(context)
        }
        return if (withPkg.resolveActivity(context.packageManager) != null) {
            withPkg
        } else {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).forLaunch(context)
        }
    }

    private fun exactAlarmSettingsIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return appDetailsIntent(context)
        }
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            forLaunch(context)
        }
    }
}
