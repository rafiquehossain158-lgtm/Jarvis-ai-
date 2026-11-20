package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.device.DeviceTelemetry
import com.example.device.InstalledAppInfo
import com.example.i18n.JarvisStrings
import com.example.ui.components.HudPanel
import com.example.ui.theme.LocalJarvisHudColors
import java.util.Locale

@Composable
fun AppsSystemsScreen(
    strings: JarvisStrings,
    telemetry: DeviceTelemetry,
    installedApps: List<InstalledAppInfo>,
    onOpenCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenMaps: (String) -> Unit,
    onLaunchApp: (InstalledAppInfo) -> Unit,
    onRefreshApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    var appFilter by remember { mutableStateOf("") }
    var mapsQuery by remember { mutableStateOf("") }
    val hudColors = LocalJarvisHudColors.current
    val glow = hudColors.glowPrimary

    val filteredApps = remember(installedApps, appFilter) {
        val q = appFilter.trim().lowercase()
        if (q.isEmpty()) installedApps else installedApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Direct Hardware Directives
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = strings.quickLaunchTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = glow
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DirectiveTile(
                            icon = Icons.Default.CameraAlt,
                            label = strings.openCamera,
                            onClick = onOpenCamera,
                            modifier = Modifier.weight(1f).testTag("btn_open_camera")
                        )
                        DirectiveTile(
                            icon = Icons.Default.Settings,
                            label = strings.openSettings,
                            onClick = onOpenSettings,
                            modifier = Modifier.weight(1f).testTag("btn_open_settings")
                        )
                        DirectiveTile(
                            icon = Icons.Default.Wifi,
                            label = strings.openWifi,
                            onClick = onOpenWifi,
                            modifier = Modifier.weight(1f).testTag("btn_open_wifi")
                        )
                        DirectiveTile(
                            icon = Icons.Default.Map,
                            label = strings.openMaps,
                            onClick = { onOpenMaps(mapsQuery) },
                            modifier = Modifier.weight(1f).testTag("btn_open_maps")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = mapsQuery,
                        onValueChange = { mapsQuery = it },
                        placeholder = { Text("Maps destination or search...") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onOpenMaps(mapsQuery) }) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = strings.openMaps,
                                    tint = glow
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Live Device Telemetry
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = strings.deviceDiagnosticsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = glow
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val ramFraction = if (telemetry.ramTotalMb > 0L) {
                        (telemetry.ramUsedMb.toFloat() / telemetry.ramTotalMb.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    val storageUsedGb = (telemetry.storageTotalGb - telemetry.storageFreeGb).coerceAtLeast(0f)
                    val storageFraction = if (telemetry.storageTotalGb > 0f) {
                        (storageUsedGb / telemetry.storageTotalGb).coerceIn(0f, 1f)
                    } else 0f

                    TelemetryRow(
                        icon = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        label = strings.batteryLabel,
                        value = "${telemetry.batteryPercent}% • ${if (telemetry.isCharging) strings.chargingLabel else strings.dischargingLabel} • ${String.format(Locale.US, "%.1f", telemetry.batteryTempCelsius)}°C",
                        progress = telemetry.batteryPercent / 100f
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TelemetryRow(
                        icon = Icons.Default.Memory,
                        label = strings.memoryLabel,
                        value = "${telemetry.ramUsedMb} MB / ${telemetry.ramTotalMb} MB",
                        progress = ramFraction
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TelemetryRow(
                        icon = Icons.Default.Storage,
                        label = strings.storageLabel,
                        value = "${String.format(Locale.US, "%.1f", telemetry.storageFreeGb)} GB free / ${String.format(Locale.US, "%.1f", telemetry.storageTotalGb)} GB",
                        progress = storageFraction
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.deviceModelLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = hudColors.textMuted
                            )
                            Text(
                                text = telemetry.deviceModel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.osVersionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = hudColors.textMuted
                            )
                            Text(
                                text = telemetry.androidVersion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Installed Apps Launcher Header & Search
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Apps, contentDescription = null, tint = glow)
                            Text(
                                text = "${strings.installedAppsTitle} (${filteredApps.size})",
                                style = MaterialTheme.typography.titleLarge,
                                color = glow
                            )
                        }
                        IconButton(onClick = onRefreshApps) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh apps",
                                tint = glow
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = appFilter,
                        onValueChange = { appFilter = it },
                        placeholder = { Text(strings.searchAppsHint) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("app_search_input")
                    )
                }
            }

            items(filteredApps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        .border(1.dp, glow.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                        .clickable { onLaunchApp(app) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("installed_app_${app.packageName}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = glow,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = hudColors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = "Launch ${app.label}",
                        tint = glow,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectiveTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glow = LocalJarvisHudColors.current.glowPrimary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(glow.copy(alpha = 0.10f))
            .border(1.dp, glow.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = glow,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TelemetryRow(
    icon: ImageVector,
    label: String,
    value: String,
    progress: Float
) {
    val glow = LocalJarvisHudColors.current.glowPrimary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = null, tint = glow, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        LinearProgressIndicator(
            progress = { progress },
            color = glow,
            trackColor = glow.copy(alpha = 0.15f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}
