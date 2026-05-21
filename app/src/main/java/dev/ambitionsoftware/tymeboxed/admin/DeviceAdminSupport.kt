package dev.ambitionsoftware.tymeboxed.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

object DeviceAdminSupport {
    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, TymeBoxedDeviceAdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return runCatching { dpm.isAdminActive(adminComponent(context)) }.getOrDefault(false)
    }

    fun removeAdmin(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return runCatching {
            dpm.removeActiveAdmin(adminComponent(context))
            true
        }.getOrDefault(false)
    }
}

