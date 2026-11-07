package com.oracleos.core.android

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import com.oracleos.core.HardwareProfile
import com.oracleos.core.HardwareProfiler

/** Reads a point-in-time profile without requesting runtime permissions. */
class AndroidHardwareProfiler(context: Context) : HardwareProfiler {
    private val appContext = context.applicationContext

    override fun snapshot(): HardwareProfile {
        val activityManager = appContext.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) {
            (level * 100 / scale).coerceIn(0, 100)
        } else {
            -1
        }
        val batteryStatus = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val powerManager = appContext.getSystemService(PowerManager::class.java)
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus ?: HardwareProfile.THERMAL_STATUS_UNKNOWN
        } else {
            HardwareProfile.THERMAL_STATUS_UNKNOWN
        }

        val storageMb = try {
            StatFs(appContext.filesDir.absolutePath).availableBytes / BYTES_PER_MB
        } catch (_: Exception) {
            0L
        }

        return HardwareProfile(
            totalRamMb = memoryInfo.totalMem / BYTES_PER_MB,
            availableRamMb = memoryInfo.availMem / BYTES_PER_MB,
            availableStorageMb = storageMb,
            batteryPercent = batteryPercent,
            isCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus == BatteryManager.BATTERY_STATUS_FULL,
            thermalStatus = thermalStatus,
            isLowRamDevice = activityManager?.isLowRamDevice ?: true,
        )
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
