package com.example.ui.screens

import android.os.Build
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.data.prefs.AppLanguage
import com.example.data.prefs.HudTheme
import com.example.data.prefs.JarvisSettings
import com.example.i18n.JarvisStrings
import com.example.ui.components.HudPanel
import com.example.ui.theme.LocalJarvisHudColors
import com.example.viewmodel.PermissionStatus
import java.util.Locale

@Composable
fun SettingsPrivacyScreen(
    strings: JarvisStrings,
    settings: JarvisSettings,
    permissionStatus: PermissionStatus,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (HudTheme) -> Unit,
    onSetAutoSpeak: (Boolean) -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onSetVoicePitch: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onRequestNotifPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSetLocalOnlyPrivacy: (Boolean) -> Unit,
    onSetSaveChatHistory: (Boolean) -> Unit,
    onSetShareTelemetry: (Boolean) -> Unit,
    onSetHighIntensityAnimations: (Boolean) -> Unit,
    onPurgeAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPurgeDialog by remember { mutableStateOf(false) }
    val hudColors = LocalJarvisHudColors.current
    val glow = hudColors.glowPrimary

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
            // Language Selector
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(Icons.Default.Language, strings.languageSectionTitle)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { lang ->
                            val selected = settings.language == lang
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) glow else MaterialTheme.colorScheme.surface)
                                    .border(1.dp, glow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .clickable { onSelectLanguage(lang) }
                                    .padding(vertical = 10.dp)
                                    .testTag("lang_option_${lang.code}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lang.nativeLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Voice Settings
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(Icons.AutoMirrored.Filled.VolumeUp, strings.voiceSectionTitle)
                    Spacer(modifier = Modifier.height(10.dp))

                    SwitchRow(
                        label = strings.autoSpeakLabel,
                        checked = settings.autoSpeakResponses,
                        onCheckedChange = onSetAutoSpeak
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${strings.speechRateLabel}: ${String.format(Locale.US, "%.2fx", settings.speechRate)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = settings.speechRate,
                        onValueChange = onSetSpeechRate,
                        valueRange = 0.6f..1.6f
                    )
                    Text(
                        text = "${strings.voicePitchLabel}: ${String.format(Locale.US, "%.2fx", settings.voicePitch)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = settings.voicePitch,
                        onValueChange = onSetVoicePitch,
                        valueRange = 0.7f..1.4f
                    )
                    OutlinedButton(
                        onClick = onTestVoice,
                        modifier = Modifier.fillMaxWidth().testTag("test_voice_button")
                    ) {
                        Text(strings.testVoiceLabel, color = glow)
                    }
                }
            }

            // Theme Settings
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(Icons.Default.Palette, strings.themeSectionTitle)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HudTheme.entries.forEach { theme ->
                            val selected = settings.hudTheme == theme
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) glow else MaterialTheme.colorScheme.surface)
                                    .border(1.dp, glow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .clickable { onSelectTheme(theme) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                                    .testTag("theme_option_${theme.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = theme.label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SwitchRow(
                        label = strings.hudEffectsTitle,
                        subtitle = strings.hudEffectsDesc,
                        checked = settings.highIntensityAnimations,
                        onCheckedChange = onSetHighIntensityAnimations
                    )
                }
            }

            // Permissions Management
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(Icons.Default.Security, strings.permissionsSectionTitle)
                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionControlRow(
                        icon = Icons.Default.Mic,
                        title = strings.micPermissionTitle,
                        description = strings.micPermissionDesc,
                        granted = permissionStatus.microphoneGranted,
                        grantedLabel = strings.grantedBadge,
                        deniedLabel = strings.deniedBadge,
                        grantButtonLabel = strings.grantButton,
                        onGrantClick = onRequestMicPermission
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(modifier = Modifier.height(10.dp))
                        PermissionControlRow(
                            icon = Icons.Default.Notifications,
                            title = strings.notifPermissionTitle,
                            description = strings.notifPermissionDesc,
                            granted = permissionStatus.notificationsGranted,
                            grantedLabel = strings.grantedBadge,
                            deniedLabel = strings.deniedBadge,
                            grantButtonLabel = strings.grantButton,
                            onGrantClick = onRequestNotifPermission
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenAppSettings,
                        modifier = Modifier.fillMaxWidth().testTag("open_app_permissions_button")
                    ) {
                        Text(strings.appSettingsButton, color = glow)
                    }
                }
            }

            // Privacy Controls
            item {
                HudPanel(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(Icons.Default.Shield, strings.privacySectionTitle)
                    Spacer(modifier = Modifier.height(10.dp))

                    SwitchRow(
                        label = strings.localOnlyModeTitle,
                        subtitle = strings.localOnlyModeDesc,
                        checked = settings.localOnlyPrivacyMode,
                        onCheckedChange = onSetLocalOnlyPrivacy
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchRow(
                        label = strings.saveHistoryTitle,
                        subtitle = strings.saveHistoryDesc,
                        checked = settings.saveChatHistory,
                        onCheckedChange = onSetSaveChatHistory
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchRow(
                        label = strings.shareTelemetryTitle,
                        subtitle = strings.shareTelemetryDesc,
                        checked = settings.shareTelemetryWithAi,
                        onCheckedChange = onSetShareTelemetry
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showPurgeDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = hudColors.statusCritical,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("purge_data_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(strings.purgeDataButton)
                    }
                }
            }
        }
    }

    if (showPurgeDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeDialog = false },
            title = { Text(strings.purgeDataConfirmTitle) },
            text = { Text(strings.purgeDataConfirmBody) },
            confirmButton = {
                Button(
                    onClick = {
                        onPurgeAllData()
                        showPurgeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = hudColors.statusCritical)
                ) {
                    Text(strings.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeDialog = false }) {
                    Text(strings.cancelLabel)
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    val glow = LocalJarvisHudColors.current.glowPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = glow)
        Text(title, style = MaterialTheme.typography.titleLarge, color = glow)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionControlRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    grantedLabel: String,
    deniedLabel: String,
    grantButtonLabel: String,
    onGrantClick: () -> Unit
) {
    val hudColors = LocalJarvisHudColors.current
    val badgeColor = if (granted) hudColors.statusNominal else hudColors.statusWarning

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = hudColors.glowPrimary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = if (granted) grantedLabel else deniedLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!granted) {
            Button(onClick = onGrantClick) {
                Text(grantButtonLabel)
            }
        }
    }
}
