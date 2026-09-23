package com.sahil.octacode.core.capability

import android.app.ActivityManager
import android.content.Context
import android.os.Build

// M1: device capabilities are PROBED at runtime, not assumed.
data class DeviceCapability(
    val androidSdk: Int,
    val javaVersion: String,
    val abi: String,
    val totalRamMb: Long,
    val isLowRam: Boolean
)

object DeviceProbe {
    fun probe(context: Context): DeviceCapability {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalMb = info.totalMem / (1024L * 1024L)
        return DeviceCapability(
            androidSdk = Build.VERSION.SDK_INT,
            javaVersion = System.getProperty("java.version") ?: "unknown",
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            totalRamMb = totalMb,
            isLowRam = info.lowMemory || totalMb < 2048
        )
    }
}
