package com.example.foodtracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Theme-aware colour palette. Both the dark and light themes provide an instance
 * with the same field names, so call sites keep using `GlassColors.backgroundDark`
 * etc. unchanged — the accessor below resolves to whichever palette is active.
 *
 * Field names are kept from the original dark-only palette (e.g. `backgroundDark`)
 * to avoid touching ~350 call sites; semantically they mean "app background",
 * "card background", and so on regardless of theme.
 */
@Immutable
class GlassPalette(
    // Backgrounds
    val backgroundDark: Color,
    val backgroundSurface: Color,
    val backgroundSurface2: Color,
    // Card
    val cardBackground: Color,
    val cardBackgroundAlt: Color,
    val cardBorder: Color,
    val cardBorderSubtle: Color,
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    // Accents — vibrant neon-ish palette (shared across themes)
    val accentGreen: Color,
    val accentGreenDim: Color,
    val accentBlue: Color,
    val accentBlueDim: Color,
    val accentOrange: Color,
    val accentOrangeDim: Color,
    val accentPurple: Color,
    val accentPink: Color,
    val accentYellow: Color,
    val accentIndigo: Color,
    val accentTeal: Color,
    // Macro pill colours
    val proteinColor: Color,
    val carbsColor: Color,
    val fatColor: Color,
    // Ring track
    val ringTrack: Color,
)

// Accents are identical in both themes — keep the neon brand colours on light too.
private val AccentGreen = Color(0xFF00E676)
private val AccentGreenDim = Color(0xFF00C853)
private val AccentBlue = Color(0xFF448AFF)
private val AccentBlueDim = Color(0xFF2979FF)
private val AccentOrange = Color(0xFFFF6D00)
private val AccentOrangeDim = Color(0xFFFF9100)
private val AccentPurple = Color(0xFFD500F9)
private val AccentPink = Color(0xFFFF4081)
private val AccentYellow = Color(0xFFFFD600)
private val AccentIndigo = Color(0xFF651FFF)
private val AccentTeal = Color(0xFF1DE9B6)

val DarkGlassPalette = GlassPalette(
    backgroundDark = Color(0xFF0D0D0D),
    backgroundSurface = Color(0xFF161616),
    backgroundSurface2 = Color(0xFF1C1C1C),
    cardBackground = Color(0xFF1A1A1A),
    cardBackgroundAlt = Color(0xFF212121),
    cardBorder = Color(0xFF2C2C2C),
    cardBorderSubtle = Color(0xFF242424),
    textPrimary = Color(0xFFF0F0F0),
    textSecondary = Color(0xFF9A9A9A),
    textTertiary = Color(0xFF555555),
    accentGreen = AccentGreen,
    accentGreenDim = AccentGreenDim,
    accentBlue = AccentBlue,
    accentBlueDim = AccentBlueDim,
    accentOrange = AccentOrange,
    accentOrangeDim = AccentOrangeDim,
    accentPurple = AccentPurple,
    accentPink = AccentPink,
    accentYellow = AccentYellow,
    accentIndigo = AccentIndigo,
    accentTeal = AccentTeal,
    proteinColor = AccentGreen,
    carbsColor = AccentBlue,
    fatColor = AccentOrange,
    ringTrack = Color(0xFF262626),
)

val LightGlassPalette = GlassPalette(
    backgroundDark = Color(0xFFF2F2F7),
    backgroundSurface = Color(0xFFEDEDF2),
    backgroundSurface2 = Color(0xFFE8E8EE),
    cardBackground = Color(0xFFFFFFFF),
    cardBackgroundAlt = Color(0xFFF7F7FA),
    cardBorder = Color(0xFFE2E2E8),
    cardBorderSubtle = Color(0xFFECECF0),
    textPrimary = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF6A6A6E),
    textTertiary = Color(0xFFA0A0A6),
    accentGreen = AccentGreen,
    accentGreenDim = AccentGreenDim,
    accentBlue = AccentBlue,
    accentBlueDim = AccentBlueDim,
    accentOrange = AccentOrange,
    accentOrangeDim = AccentOrangeDim,
    accentPurple = AccentPurple,
    accentPink = AccentPink,
    accentYellow = AccentYellow,
    accentIndigo = AccentIndigo,
    accentTeal = AccentTeal,
    proteinColor = AccentGreen,
    carbsColor = AccentBlue,
    fatColor = AccentOrange,
    ringTrack = Color(0xFFE2E2E8),
)

/** The active palette, provided by [FoodTrackerTheme]. Defaults to dark. */
val LocalGlassColors = staticCompositionLocalOf { DarkGlassPalette }

/**
 * Drop-in replacement for the former `object GlassColors`: `GlassColors.backgroundDark`
 * now reads the active theme's palette. Only usable inside @Composable scope (which is
 * where every existing reference already lives).
 */
val GlassColors: GlassPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalGlassColors.current

/** Standard card with subtle border */
@Composable
fun Modifier.glassCard(cornerRadius: Int = 16): Modifier {
    val shape = RoundedCornerShape(cornerRadius.dp)
    return this
        .clip(shape)
        .background(GlassColors.cardBackground)
        .border(width = 1.dp, color = GlassColors.cardBorder, shape = shape)
}

/** Slightly lighter card variant */
@Composable
fun Modifier.glassCardAlt(cornerRadius: Int = 16): Modifier {
    val shape = RoundedCornerShape(cornerRadius.dp)
    return this
        .clip(shape)
        .background(GlassColors.cardBackgroundAlt)
        .border(width = 1.dp, color = GlassColors.cardBorderSubtle, shape = shape)
}

/** Card with a top-edge colour accent glow */
@Composable
fun Modifier.accentCard(accentColor: Color, cornerRadius: Int = 20): Modifier {
    val shape = RoundedCornerShape(cornerRadius.dp)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to accentColor.copy(alpha = 0.08f),
                1f to GlassColors.cardBackground
            )
        )
        .border(width = 1.dp, color = accentColor.copy(alpha = 0.22f), shape = shape)
}
