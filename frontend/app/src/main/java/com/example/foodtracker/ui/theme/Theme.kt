package com.example.foodtracker.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkGlassPalette.accentGreen,
    secondary = DarkGlassPalette.accentBlue,
    tertiary = DarkGlassPalette.accentPurple,
    background = DarkGlassPalette.backgroundDark,
    surface = DarkGlassPalette.cardBackground,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkGlassPalette.textPrimary,
    onSurface = DarkGlassPalette.textPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = LightGlassPalette.accentGreen,
    secondary = LightGlassPalette.accentBlue,
    tertiary = LightGlassPalette.accentPurple,
    background = LightGlassPalette.backgroundDark,
    surface = LightGlassPalette.cardBackground,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightGlassPalette.textPrimary,
    onSurface = LightGlassPalette.textPrimary,
)

@Composable
fun FoodTrackerTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (isDark) DarkGlassPalette else LightGlassPalette
    CompositionLocalProvider(LocalGlassColors provides palette) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            typography = Typography(),
            content = content
        )
    }
}
