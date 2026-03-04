package com.example.foodtracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object GlassColors {
    // Backgrounds
    val backgroundDark       = Color(0xFF0D0D0D)
    val backgroundSurface    = Color(0xFF161616)
    val backgroundSurface2   = Color(0xFF1C1C1C)

    // Card
    val cardBackground       = Color(0xFF1A1A1A)
    val cardBackgroundAlt    = Color(0xFF212121)
    val cardBorder           = Color(0xFF2C2C2C)
    val cardBorderSubtle     = Color(0xFF242424)

    // Text
    val textPrimary          = Color(0xFFF0F0F0)
    val textSecondary        = Color(0xFF9A9A9A)
    val textTertiary         = Color(0xFF555555)

    // Accents — vibrant neon-ish palette
    val accentGreen          = Color(0xFF00E676)   // neon green
    val accentGreenDim       = Color(0xFF00C853)
    val accentBlue           = Color(0xFF448AFF)
    val accentBlueDim        = Color(0xFF2979FF)
    val accentOrange         = Color(0xFFFF6D00)
    val accentOrangeDim      = Color(0xFFFF9100)
    val accentPurple         = Color(0xFFD500F9)
    val accentPink           = Color(0xFFFF4081)
    val accentYellow         = Color(0xFFFFD600)
    val accentIndigo         = Color(0xFF651FFF)
    val accentTeal           = Color(0xFF1DE9B6)

    // Macro pill colours
    val proteinColor         = Color(0xFF00E676)
    val carbsColor           = Color(0xFF448AFF)
    val fatColor             = Color(0xFFFF6D00)

    // Ring track
    val ringTrack            = Color(0xFF262626)
}

/** Standard dark card with subtle border */
fun Modifier.glassCard(cornerRadius: Int = 16): Modifier {
    val shape = RoundedCornerShape(cornerRadius.dp)
    return this
        .clip(shape)
        .background(GlassColors.cardBackground)
        .border(width = 1.dp, color = GlassColors.cardBorder, shape = shape)
}

/** Slightly lighter card variant */
fun Modifier.glassCardAlt(cornerRadius: Int = 16): Modifier {
    val shape = RoundedCornerShape(cornerRadius.dp)
    return this
        .clip(shape)
        .background(GlassColors.cardBackgroundAlt)
        .border(width = 1.dp, color = GlassColors.cardBorderSubtle, shape = shape)
}

/** Card with a top-edge colour accent glow */
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
