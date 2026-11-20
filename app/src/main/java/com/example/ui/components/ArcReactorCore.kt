package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalJarvisHudColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CoreVisualMode {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun ArcReactorCore(
    mode: CoreVisualMode,
    rmsLevel: Float,
    statusLabel: String,
    highIntensity: Boolean,
    modifier: Modifier = Modifier,
    coreSize: Dp = 152.dp
) {
    val hudColors = LocalJarvisHudColors.current
    val primaryGlow = hudColors.glowPrimary
    val secondaryGlow = hudColors.glowSecondary

    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    CoreVisualMode.THINKING -> 2800
                    CoreVisualMode.LISTENING -> 4500
                    CoreVisualMode.SPEAKING -> 5000
                    CoreVisualMode.IDLE -> 11000
                },
                easing = LinearEasing
            )
        ),
        label = "outer_rotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    CoreVisualMode.THINKING -> 2000
                    CoreVisualMode.LISTENING -> 3400
                    CoreVisualMode.SPEAKING -> 3800
                    CoreVisualMode.IDLE -> 8000
                },
                easing = LinearEasing
            )
        ),
        label = "inner_rotation"
    )

    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "wave_phase"
    )

    val smoothRms by animateFloatAsState(
        targetValue = when (mode) {
            CoreVisualMode.LISTENING -> rmsLevel.coerceIn(0.15f, 1f)
            CoreVisualMode.SPEAKING -> 0.55f
            CoreVisualMode.THINKING -> 0.4f
            CoreVisualMode.IDLE -> 0.08f
        },
        animationSpec = spring(stiffness = 380f),
        label = "smooth_rms"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(coreSize)
                .semantics { contentDescription = "Jarvis AI Core: $statusLabel" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val minDim = size.minDimension
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = minDim / 2f

                // Ambient glow halo
                val glowRadius = maxRadius * (0.85f + smoothRms * 0.15f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = if (highIntensity) 0.28f else 0.14f),
                            secondaryGlow.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )

                // Radial voice / telemetry waveform spokes
                if (highIntensity || mode != CoreVisualMode.IDLE) {
                    val spokeCount = 36
                    val baseR = maxRadius * 0.72f
                    for (i in 0 until spokeCount) {
                        val angle = (i.toFloat() / spokeCount) * 2f * PI.toFloat()
                        val harmonic = (sin(angle * 4f + wavePhase) * 0.5f + 0.5f)
                        val spokeLen = maxRadius * (0.04f + smoothRms * 0.18f * harmonic)
                        val start = Offset(
                            x = center.x + cos(angle) * baseR,
                            y = center.y + sin(angle) * baseR
                        )
                        val end = Offset(
                            x = center.x + cos(angle) * (baseR + spokeLen),
                            y = center.y + sin(angle) * (baseR + spokeLen)
                        )
                        drawLine(
                            color = primaryGlow.copy(alpha = 0.45f + smoothRms * 0.45f),
                            start = start,
                            end = end,
                            strokeWidth = 2.2f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Outer segmented HUD ring
                rotate(degrees = outerRotation, pivot = center) {
                    val outerRadius = maxRadius * 0.70f
                    val arcTopLeft = Offset(center.x - outerRadius, center.y - outerRadius)
                    val arcSize = Size(outerRadius * 2f, outerRadius * 2f)

                    drawCircle(
                        color = secondaryGlow.copy(alpha = 0.25f),
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = 2f)
                    )

                    for (segment in 0 until 4) {
                        drawArc(
                            color = primaryGlow.copy(alpha = 0.85f),
                            startAngle = segment * 90f + 10f,
                            sweepAngle = 52f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                        )
                    }
                }

                // Counter-rotating inner technical ring
                rotate(degrees = innerRotation, pivot = center) {
                    val innerRadius = maxRadius * 0.53f
                    val innerTopLeft = Offset(center.x - innerRadius, center.y - innerRadius)
                    val innerSize = Size(innerRadius * 2f, innerRadius * 2f)

                    for (segment in 0 until 6) {
                        drawArc(
                            color = secondaryGlow.copy(alpha = 0.9f),
                            startAngle = segment * 60f + 6f,
                            sweepAngle = 36f,
                            useCenter = false,
                            topLeft = innerTopLeft,
                            size = innerSize,
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                        )
                    }
                }

                // Pulsing inner reactor node
                val nodeRadius = maxRadius * 0.30f * breathScale * (1f + smoothRms * 0.14f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            primaryGlow.copy(alpha = 0.9f),
                            secondaryGlow.copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = nodeRadius
                    ),
                    radius = nodeRadius,
                    center = center
                )

                drawCircle(
                    color = primaryGlow,
                    radius = maxRadius * 0.34f,
                    center = center,
                    style = Stroke(width = 2f)
                )
            }
        }

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = primaryGlow
        )
    }
}

@Composable
fun VoiceWaveformMicButton(
    isListening: Boolean,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hudColors = LocalJarvisHudColors.current
    val glow = hudColors.glowPrimary

    val infiniteTransition = rememberInfiniteTransition(label = "mic_wave")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "mic_pulse"
    )

    val activeAmp = if (isListening) rmsLevel.coerceIn(0.25f, 1f) else 0f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isListening) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(28.dp)
            ) {
                for (i in 0 until 5) {
                    val barFactor = (sin(pulse + i * 0.9f) * 0.5f + 0.5f)
                    val heightDp = (8f + 18f * activeAmp * barFactor).dp
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(heightDp)
                            .clip(CircleShape)
                            .background(glow)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = if (isListening) {
                            listOf(glow, hudColors.glowSecondary)
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surface
                            )
                        }
                    )
                )
                .border(
                    width = if (isListening) 2.dp else 1.5.dp,
                    color = glow,
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
                .testTag("mic_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isListening -> Icons.Default.Stop
                    else -> Icons.Default.Mic
                },
                contentDescription = if (isListening) "Stop listening" else "Start voice input",
                tint = if (isListening) MaterialTheme.colorScheme.onPrimary else glow,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun MiniSpeakingWaveBadge(
    label: String,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glow = LocalJarvisHudColors.current.glowPrimary
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(glow.copy(alpha = 0.14f))
            .border(1.dp, glow.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onStop)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("stop_speaking_badge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = label,
            tint = glow,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = glow
        )
    }
}
