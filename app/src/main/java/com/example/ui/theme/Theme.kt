package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.prefs.HudTheme

@Immutable
data class JarvisHudColors(
    val glowPrimary: Color = ArcCyan,
    val glowSecondary: Color = ArcCobalt,
    val panelBorder: Color = BorderGlass,
    val statusNominal: Color = NominalGreen,
    val statusWarning: Color = WarningAmber,
    val statusCritical: Color = AlertCrimson,
    val textMuted: Color = TextHudMuted
)

val LocalJarvisHudColors = staticCompositionLocalOf { JarvisHudColors() }

@Composable
fun JarvisAiTheme(
    hudTheme: HudTheme = HudTheme.ARC_CYAN,
    content: @Composable () -> Unit
) {
    val (primaryGlow, secondaryGlow) = when (hudTheme) {
        HudTheme.ARC_CYAN -> ArcCyan to ArcCobalt
        HudTheme.STARK_COBALT -> CobaltPrimary to CobaltSecondary
        HudTheme.STEALTH_EMERALD -> EmeraldPrimary to EmeraldSecondary
    }

    val colorScheme = darkColorScheme(
        primary = primaryGlow,
        onPrimary = VoidBlack,
        primaryContainer = PanelElevated,
        onPrimaryContainer = primaryGlow,
        secondary = secondaryGlow,
        onSecondary = VoidBlack,
        secondaryContainer = DeepSpace,
        onSecondaryContainer = TextHudPrimary,
        tertiary = ArcCyanSoft,
        onTertiary = VoidBlack,
        background = VoidBlack,
        onBackground = TextHudPrimary,
        surface = DeepSpace,
        onSurface = TextHudPrimary,
        surfaceVariant = PanelSurface,
        onSurfaceVariant = TextHudSecondary,
        surfaceContainerLowest = VoidBlack,
        surfaceContainerLow = DeepSpace,
        surfaceContainer = PanelSurface,
        surfaceContainerHigh = PanelElevated,
        outline = BorderGlass,
        outlineVariant = BorderGlass.copy(alpha = 0.5f),
        error = AlertCrimson,
        onError = VoidBlack
    )

    val hudColors = JarvisHudColors(
        glowPrimary = primaryGlow,
        glowSecondary = secondaryGlow,
        panelBorder = primaryGlow.copy(alpha = 0.32f)
    )

    CompositionLocalProvider(LocalJarvisHudColors provides hudColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JarvisTypography,
            content = content
        )
    }
}
