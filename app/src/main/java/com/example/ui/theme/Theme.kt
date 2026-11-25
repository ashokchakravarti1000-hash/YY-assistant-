package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val YyMindDarkColorScheme = darkColorScheme(
    primary = SaffronAmber,
    onPrimary = Color(0xFF1F1300),
    primaryContainer = SaffronContainerDark,
    onPrimaryContainer = SaffronAmberLight,
    secondary = NeuralTeal,
    onSecondary = Color(0xFF00201E),
    secondaryContainer = NeuralTealContainerDark,
    onSecondaryContainer = NeuralTealLight,
    tertiary = IndigoPulse,
    onTertiary = Color(0xFF101342),
    tertiaryContainer = IndigoContainerDark,
    onTertiaryContainer = Color(0xFFC7D2FE),
    background = ObsidianCanvas,
    onBackground = InkLight,
    surface = SlateSurface,
    onSurface = InkLight,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = InkMutedDark,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceVariant,
    surfaceContainerHigh = SlateSurfaceHigh,
    outline = SlateOutline,
    outlineVariant = SlateOutlineVariant,
    error = StatusRed,
    onError = Color.White,
    errorContainer = StatusRedContainerDark,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val YyMindLightColorScheme = lightColorScheme(
    primary = SaffronAmberDeep,
    onPrimary = Color.White,
    primaryContainer = SaffronContainerLight,
    onPrimaryContainer = Color(0xFF451A03),
    secondary = NeuralTealDeep,
    onSecondary = Color.White,
    secondaryContainer = NeuralTealContainerLight,
    onSecondaryContainer = Color(0xFF042F2E),
    tertiary = IndigoPulseDeep,
    onTertiary = Color.White,
    tertiaryContainer = IndigoContainerLight,
    onTertiaryContainer = Color(0xFF1E1B4B),
    background = AlabasterCanvas,
    onBackground = InkDark,
    surface = AlabasterSurface,
    onSurface = InkDark,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = InkMutedLight,
    surfaceContainerLowest = AlabasterSurface,
    surfaceContainerLow = AlabasterCanvas,
    surfaceContainer = AlabasterSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE2DFD5),
    outline = AlabasterOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = StatusRed,
    onError = Color.White,
    errorContainer = StatusRedContainerLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

val YyMindShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun YyMindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> YyMindDarkColorScheme
        else -> YyMindLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YyMindTypography,
        shapes = YyMindShapes,
        content = content
    )
}
