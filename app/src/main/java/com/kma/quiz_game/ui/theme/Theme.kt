package com.kma.quiz_game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// The container and surfaceContainer roles are spelled out on purpose: left to Material's
// defaults they come out lavender, which is what the bottom bar and the benchmark banner showed.
private val DarkColorScheme = darkColorScheme(
    primary = Green500,
    onPrimary = SurfaceLight,
    primaryContainer = Green900,
    onPrimaryContainer = Green100,
    secondary = Sky500,
    onSecondary = SurfaceLight,
    secondaryContainer = Sky900,
    onSecondaryContainer = Sky100,
    tertiary = Indigo500,
    error = Rose500,
    background = Neutral800,
    onBackground = Neutral100,
    surface = Neutral700,
    onSurface = Neutral100,
    surfaceVariant = Neutral600,
    onSurfaceVariant = Neutral200,
    surfaceContainerLowest = Neutral800,
    surfaceContainerLow = Neutral800,
    surfaceContainer = Neutral700,
    surfaceContainerHigh = Neutral700,
    surfaceContainerHighest = Neutral600,
    outline = Neutral500,
)

private val LightColorScheme = lightColorScheme(
    primary = Green500,
    onPrimary = SurfaceLight,
    primaryContainer = Green100,
    onPrimaryContainer = Green900,
    secondary = Sky500,
    onSecondary = SurfaceLight,
    secondaryContainer = Sky100,
    onSecondaryContainer = Sky900,
    tertiary = Indigo500,
    error = Rose500,
    background = BackgroundLight,
    onBackground = Neutral800,
    surface = SurfaceLight,
    onSurface = Neutral800,
    surfaceVariant = Neutral050,
    onSurfaceVariant = Neutral600,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerLow = Neutral050,
    surfaceContainer = Neutral050,
    surfaceContainerHigh = Neutral100,
    surfaceContainerHighest = Neutral100,
    outline = Neutral200,
)

@Composable
fun Quiz_gameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
