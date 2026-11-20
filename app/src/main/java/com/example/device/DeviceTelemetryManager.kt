package com.example.device

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

data class DeviceTelemetry(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float = 0f,
    val batteryVoltageMv: Int = 0,
    val batteryHealthLabel: String = "Nominal",
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val storageFreeGb: Float = 0f,
    val storageTotalGb: Float = 0f,
    val networkStatus: String = "Online",
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val hardwareAbi: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "ARM64"
) {
    fun toPromptSummary(): String = buildString {
        append("Battery: $batteryPercent% (${if (isCharging) "Charging" else "Discharging"}, ")
        append("${String.format(Locale.US, "%.1f", batteryTempCelsius)}°C), ")
        append("RAM: ${ramUsedMb}MB/${ramTotalMb}MB, ")
        append("Storage Free: ${String.format(Locale.US, "%.1f", storageFreeGb)}/${String.format(Locale.US, "%.1f", storageTotalGb)} GB, ")
        append("Network: $networkStatus, Device: $deviceModel, OS: $androidVersion")
    }
}

class DeviceTelemetryManager(private val context: Context) {

    fun observeTelemetry(): Flow<DeviceTelemetry> = callbackFlow {
        val sendUpdate = {
            trySend(captureCurrentTelemetry())
        }

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                sendUpdate()
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(batteryReceiver, filter)
        }

        sendUpdate()

        awaitClose {
            runCatching { context.unregisterReceiver(batteryReceiver) }
        }
    }

    fun captureCurrentTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 100
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3900) ?: 3900
        val healthCode = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
            ?: BatteryManager.BATTERY_HEALTH_GOOD
        val healthLabel = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Nominal"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Depleted"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Good"
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0L)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalStorageBytes = statFs.blockCountLong * statFs.blockSizeLong
        val freeStorageBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val gbDivisor = 1024f * 1024f * 1024f

        val networkStatus = readNetworkStatus()

        return DeviceTelemetry(
            batteryPercent = batteryPct.coerceIn(0, 100),
            isCharging = isCharging,
            batteryTempCelsius = tempTenths / 10f,
            batteryVoltageMv = voltageMv,
            batteryHealthLabel = healthLabel,
            ramUsedMb = usedRamMb,
            ramTotalMb = totalRamMb,
            storageFreeGb = freeStorageBytes / gbDivisor,
            storageTotalGb = totalStorageBytes / gbDivisor,
            networkStatus = networkStatus
        )
    }

    private fun readNetworkStatus(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Unknown"
        val network = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(network) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
    }
}
