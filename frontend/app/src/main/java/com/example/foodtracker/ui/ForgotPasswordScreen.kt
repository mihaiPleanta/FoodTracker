package com.example.foodtracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.viewmodel.AuthViewModel
import com.example.foodtracker.viewmodel.PasswordResetState

@Composable
fun ForgotPasswordScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val prefill by authViewModel.resetEmailPrefill.collectAsState()
    var email by remember { mutableStateOf(prefill) }
    val state by authViewModel.passwordResetState.collectAsState()

    LaunchedEffect(Unit) { authViewModel.clearPasswordResetState() }

    val errorMessage = (state as? PasswordResetState.Error)?.messageRes?.let { stringResource(it) }
    val isSending = state is PasswordResetState.Sending
    val isSent = state is PasswordResetState.Sent

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(GlassColors.cardBackground)
                        .border(1.dp, GlassColors.cardBorder, CircleShape)
                        .clickable { onBack() },
                    Alignment.Center
                ) {
                    Icon(Icons.Rounded.ArrowBackIosNew,
                        contentDescription = stringResource(R.string.action_back),
                        tint = GlassColors.textSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(40.dp))

            if (isSent) {
                Text("✅", fontSize = 56.sp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.forgot_sent_title), fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold, color = GlassColors.textPrimary)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.forgot_sent_message, email), fontSize = 14.sp,
                    color = GlassColors.textSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                Text(stringResource(R.string.forgot_back_to_login), fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, color = GlassColors.accentGreen,
                    modifier = Modifier.clickable { onBack() })
            } else {
                Text(stringResource(R.string.forgot_title), fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold, color = GlassColors.textPrimary)
                Text(stringResource(R.string.forgot_subtitle), fontSize = 13.sp,
                    color = GlassColors.textSecondary)
                Spacer(Modifier.height(32.dp))
                AuthTextField(
                    value = email,
                    onValueChange = { email = it; authViewModel.clearPasswordResetState() },
                    placeholder = stringResource(R.string.login_email),
                    keyboardType = KeyboardType.Email
                )
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(tween(200)), exit = fadeOut(tween(200))
                ) {
                    Text(errorMessage ?: "", color = Color(0xFFFF4444), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth())
                }
                Spacer(Modifier.height(20.dp))
                AuthPrimaryButton(
                    text = stringResource(R.string.forgot_send_button),
                    enabled = email.isNotBlank(),
                    isLoading = isSending,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { authViewModel.sendPasswordReset(email) }
                )
            }
        }
    }
}
