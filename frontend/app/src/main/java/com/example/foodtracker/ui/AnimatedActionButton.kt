package com.example.foodtracker.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.ui.theme.GlassColors

/** Phase of an action button. Success only renders specially when a successLabel is provided. */
sealed interface ActionPhase {
    data object Idle : ActionPhase
    data object Loading : ActionPhase
    data object Success : ActionPhase
}

/**
 * Single source of truth for the button scale animation: 0.92 while pressed,
 * 1f at rest, or an explicit [overrideTarget] (e.g. 1.04 success bounce).
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    overrideTarget: Float? = null,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val target = overrideTarget ?: if (isPressed && enabled) 0.92f else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this.scale(scale)
}

/**
 * Shared primary-action button. Always press-scales + fires haptic on click.
 * Loading shows a spinner; Success (with non-null [successLabel]) shows a checkmark
 * + label and a 1.04 bounce.
 */
@Composable
fun AnimatedActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    phase: ActionPhase = ActionPhase.Idle,
    successLabel: String? = null,
    enabled: Boolean = true,
    accent: Brush = Brush.horizontalGradient(
        listOf(GlassColors.accentGreen, GlassColors.accentGreenDim)
    ),
    contentColor: Color = Color.Black,
    height: Dp = 52.dp,
    cornerRadius: Dp = 16.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current

    val isLoading = phase is ActionPhase.Loading
    val isSuccess = phase is ActionPhase.Success && successLabel != null
    val isEnabled = enabled && !isLoading && !isSuccess

    val background = if (isEnabled || isSuccess || isLoading) accent
    else Brush.horizontalGradient(listOf(GlassColors.cardBackground, GlassColors.cardBackground))

    Box(
        modifier = modifier
            .pressScale(
                interactionSource = interactionSource,
                enabled = isEnabled,
                overrideTarget = if (isSuccess) 1.04f else null,
            )
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = contentColor,
                strokeWidth = 2.5.dp,
            )
            isSuccess -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(successLabel!!, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = contentColor)
            }
            else -> Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) contentColor else GlassColors.textTertiary,
            )
        }
    }
}
