package com.kma.quiz_game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Green500,
    onPrimary = SurfaceLight,
    secondary = Sky500,
    onSecondary = SurfaceLight,
    tertiary = Indigo500,
    error = Rose500,
    background = Neutral800,
    onBackground = Neutral100,
    surface = Neutral700,
    onSurface = Neutral100,
    surfaceVariant = Neutral600,
    onSurfaceVariant = Neutral200,
    outline = Neutral500,
)

private val LightColorScheme = lightColorScheme(
    primary = Green500,
    onPrimary = SurfaceLight,
    secondary = Sky500,
    onSecondary = SurfaceLight,
    tertiary = Indigo500,
    error = Rose500,
    background = BackgroundLight,
    onBackground = Neutral800,
    surface = SurfaceLight,
    onSurface = Neutral800,
    surfaceVariant = Neutral050,
    onSurfaceVariant = Neutral600,
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
