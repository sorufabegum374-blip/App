package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LuminaDarkColorScheme = darkColorScheme(
    primary = PrismCyan,
    onPrimary = OnPrismCyan,
    primaryContainer = PrismCyanContainer,
    onPrimaryContainer = OnPrismCyanContainer,
    secondary = SolarAmber,
    onSecondary = OnSolarAmber,
    secondaryContainer = SolarAmberContainer,
    onSecondaryContainer = OnSolarAmberContainer,
    tertiary = RayMagenta,
    onTertiary = OnRayMagenta,
    tertiaryContainer = RayMagentaContainer,
    onTertiaryContainer = OnRayMagentaContainer,
    background = ObsidianVoid,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianContainer,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianContainer,
    surfaceContainerHigh = ObsidianContainerHigh,
    surfaceContainerHighest = ObsidianOutlineVariant,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = ErrorCoral,
    onError = OnErrorCoral,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val LuminaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LuminaShaderForgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LuminaDarkColorScheme,
        typography = AppTypography,
        shapes = LuminaShapes,
        content = content
    )
}
