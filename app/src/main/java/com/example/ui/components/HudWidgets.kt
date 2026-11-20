package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.ChatMessageEntity
import com.example.data.prefs.AppLanguage
import com.example.device.DeviceTelemetry
import com.example.i18n.JarvisStrings
import com.example.ui.theme.LocalJarvisHudColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    accentColor: Color = LocalJarvisHudColors.current.glowPrimary,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.30f), shape)
            .drawBehind {
                val tick = 10.dp.toPx()
                val stroke = 2.dp.toPx()
                val tickColor = accentColor.copy(alpha = 0.75f)
                // Top-left corner bracket
                drawLine(tickColor, Offset(0f, 0f), Offset(tick, 0f), stroke)
                drawLine(tickColor, Offset(0f, 0f), Offset(0f, tick), stroke)
                // Bottom-right corner bracket
                drawLine(tickColor, Offset(size.width, size.height), Offset(size.width - tick, size.height), stroke)
                drawLine(tickColor, Offset(size.width, size.height), Offset(size.width, size.height - tick), stroke)
            }
            .padding(14.dp),
        content = content
    )
}

@Composable
fun HudTelemetryStrip(
    strings: JarvisStrings,
    telemetry: DeviceTelemetry,
    language: AppLanguage,
    localOnlyPrivacy: Boolean,
    onCycleLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hudColors = LocalJarvisHudColors.current
    val glow = hudColors.glowPrimary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (localOnlyPrivacy) hudColors.statusWarning else hudColors.statusNominal)
            )
            Text(
                text = strings.appTitle,
                style = MaterialTheme.typography.titleLarge,
                color = glow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (localOnlyPrivacy) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = strings.coreOfflinePrivacy,
                    tint = hudColors.statusWarning,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Battery pill
            HudBadgeChip(
                icon = {
                    Icon(
                        imageVector = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                        contentDescription = strings.batteryLabel,
                        tint = if (telemetry.batteryPercent <= 20) hudColors.statusCritical else glow,
                        modifier = Modifier.size(14.dp)
                    )
                },
                label = "${telemetry.batteryPercent}%"
            )

            // Network pill
            HudBadgeChip(
                icon = {
                    Icon(
                        imageVector = if (telemetry.networkStatus == "Offline") Icons.Default.WifiOff else Icons.Default.Wifi,
                        contentDescription = strings.networkLabel,
                        tint = if (telemetry.networkStatus == "Offline") hudColors.statusWarning else glow,
                        modifier = Modifier.size(14.dp)
                    )
                },
                label = telemetry.networkStatus
            )

            // Quick language switcher pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glow.copy(alpha = 0.14f),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, glow.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onCycleLanguage)
                    .testTag("language_cycle_chip")
            ) {
                Text(
                    text = language.code.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = glow,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun HudBadgeChip(
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val glow = LocalJarvisHudColors.current.glowPrimary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(1.dp, glow.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ChatMessageCard(
    message: ChatMessageEntity,
    strings: JarvisStrings,
    onSpeak: (String) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val hudColors = LocalJarvisHudColors.current
    val accent = when {
        message.isError -> hudColors.statusCritical
        isUser -> hudColors.glowSecondary
        else -> hudColors.glowPrimary
    }
    val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.90f)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(
                    if (isUser) {
                        hudColors.glowSecondary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f)
                    }
                )
                .border(
                    width = 1.dp,
                    color = accent.copy(alpha = if (isUser) 0.45f else 0.35f),
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isUser) "YOU" else "J.A.R.V.I.S.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                    if (!message.actionBadge.isNullOrBlank()) {
                        Text(
                            text = message.actionBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = hudColors.textMuted
                )
            }

            Spacer(modifier = Modifier.size(6.dp))

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isError) hudColors.statusCritical else MaterialTheme.colorScheme.onSurface
            )

            if (!isUser && !message.isError) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onSpeak(message.content) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = strings.speakReply,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onCopy(message.content) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = strings.copyReply,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
