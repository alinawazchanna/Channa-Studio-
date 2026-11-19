package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StreamPulseDarkColors = darkColorScheme(
    primary = LiveCrimson,
    onPrimary = Color.White,
    primaryContainer = LiveCrimsonContainer,
    onPrimaryContainer = OnLiveCrimsonContainer,
    secondary = SignalCyan,
    onSecondary = Color(0xFF002026),
    secondaryContainer = SignalCyanContainer,
    onSecondaryContainer = OnSignalCyanContainer,
    tertiary = SuperChatGold,
    onTertiary = Color(0xFF2B1D00),
    tertiaryContainer = SuperChatGoldContainer,
    onTertiaryContainer = OnSuperChatGoldContainer,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerLowest = StudioObsidian,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = Color(0xFFFF6B81),
    onError = Color(0xFF3E0011)
)

private val StreamPulseLightColors = lightColorScheme(
    primary = DaylightCrimson,
    onPrimary = Color.White,
    primaryContainer = DaylightCrimsonContainer,
    onPrimaryContainer = OnDaylightCrimsonContainer,
    secondary = DaylightCyan,
    onSecondary = Color.White,
    secondaryContainer = DaylightCyanContainer,
    onSecondaryContainer = OnDaylightCyanContainer,
    tertiary = DaylightGold,
    onTertiary = Color.White,
    tertiaryContainer = DaylightGoldContainer,
    onTertiaryContainer = OnDaylightGoldContainer,
    background = DaylightCanvas,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceElevated,
    onSurfaceVariant = DaylightTextSecondary,
    surfaceContainerLowest = DaylightSurface,
    surfaceContainerLow = DaylightCanvas,
    surfaceContainer = DaylightSurfaceElevated,
    surfaceContainerHigh = DaylightSurfaceHighest,
    outline = DaylightOutline,
    outlineVariant = Color(0xFFE2E7F4),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

val StreamPulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StreamPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) StreamPulseDarkColors else StreamPulseLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = StreamPulseTypography,
        shapes = StreamPulseShapes,
        content = content
    )
}
