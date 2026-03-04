package com.example.foodtracker.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GlassColors.accentGreen,
    secondary = GlassColors.accentBlue,
    tertiary = GlassColors.accentPurple,
    background = GlassColors.backgroundDark,
    surface = GlassColors.cardBackground,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = GlassColors.textPrimary,
    onSurface = GlassColors.textPrimary,
)

@Composable
fun FoodTrackerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}