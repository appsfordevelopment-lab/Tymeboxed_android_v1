package dev.ambitionsoftware.tymeboxed.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object AppInfo {

    fun versionName(context: Context): String =
        runCatching {
            val packageInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            packageInfo.versionName.orEmpty()
        }.getOrDefault("")
}
