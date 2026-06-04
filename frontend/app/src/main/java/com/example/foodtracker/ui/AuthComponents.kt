package com.example.foodtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .glassCard(14)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = GlassColors.textPrimary, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword && !passwordVisible)
                PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner ->
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = GlassColors.textTertiary, fontSize = 15.sp)
                    inner()
                }
            },
            modifier = Modifier.weight(1f)
        )
        if (isPassword && onTogglePasswordVisibility != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (passwordVisible) stringResource(R.string.field_hide) else stringResource(R.string.field_show),
                fontSize = 11.sp,
                color = GlassColors.accentGreen,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onTogglePasswordVisibility() }
            )
        }
    }
}

@Composable
fun AuthPrimaryButton(
    text: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AnimatedActionButton(
        label = text,
        phase = if (isLoading) ActionPhase.Loading else ActionPhase.Idle,
        successLabel = null,
        enabled = enabled,
        contentColor = Color.Black,
        modifier = modifier,
        onClick = onClick,
    )
}
