package com.example.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

sealed interface LaunchResult {
    data class Launched(val targetName: String) : LaunchResult
    data class NotFound(val query: String) : LaunchResult
    data class Failed(val reason: String) : LaunchResult
}

class AppLauncherManager(private val context: Context) {

    fun loadLaunchableApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        return resolved
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                InstalledAppInfo(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launchAppByPackage(packageName: String, label: String = packageName): LaunchResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return LaunchResult.NotFound(label)
        return startIntent(launchIntent, label)
    }

    fun launchAppByQuery(query: String): LaunchResult {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return LaunchResult.NotFound(query)

        // Common system aliases
        if (normalized in setOf("camera", "ক্যামেরা", "कैमरा")) return openCamera()
        if (normalized in setOf("settings", "setting", "সেটিংস", "सेटिंग्स", "সেটিং")) return openSettings()
        if (normalized in setOf("maps", "google maps", "ম্যাপস", "গুগল ম্যাপস", "मैप्स", "गूगल मैप्स")) return openMaps("")

        val apps = loadLaunchableApps()
        val exactMatch = apps.firstOrNull { it.label.lowercase() == normalized }
        val startsWithMatch = apps.firstOrNull { it.label.lowercase().startsWith(normalized) }
        val containsMatch = apps.firstOrNull {
            it.label.lowercase().contains(normalized) || it.packageName.lowercase().contains(normalized)
        }
        val match = exactMatch ?: startsWithMatch ?: containsMatch ?: return LaunchResult.NotFound(query)
        return launchAppByPackage(match.packageName, match.label)
    }

    fun openCamera(): LaunchResult {
        val stillIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val target = if (stillIntent.resolveActivity(context.packageManager) != null) {
            stillIntent
        } else {
            captureIntent
        }
        return startIntent(target, "Camera")
    }

    fun openSettings(section: String = Settings.ACTION_SETTINGS): LaunchResult {
        val intent = Intent(section).apply {
            if (section == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        return startIntent(intent, "Settings")
    }

    fun openWifiSettings(): LaunchResult = openSettings(Settings.ACTION_WIFI_SETTINGS)

    fun openAppPermissionsSettings(): LaunchResult =
        openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)

    fun openMaps(query: String): LaunchResult {
        val encoded = Uri.encode(query.trim())
        val geoUri = if (encoded.isNotEmpty()) {
            Uri.parse("geo:0,0?q=$encoded")
        } else {
            Uri.parse("geo:0,0?q=")
        }

        val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (mapsIntent.resolveActivity(context.packageManager) != null) {
            return startIntent(mapsIntent, "Google Maps")
        }

        val genericGeo = Intent(Intent.ACTION_VIEW, geoUri)
        if (genericGeo.resolveActivity(context.packageManager) != null) {
            return startIntent(genericGeo, "Maps")
        }

        val webUrl = if (encoded.isNotEmpty()) {
            "https://maps.google.com/?q=$encoded"
        } else {
            "https://maps.google.com/"
        }
        return startIntent(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)), "Google Maps (Web)")
    }

    private fun startIntent(intent: Intent, targetName: String): LaunchResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LaunchResult.Launched(targetName)
        } catch (e: Exception) {
            LaunchResult.Failed(e.localizedMessage ?: "Unable to open $targetName")
        }
    }
}
