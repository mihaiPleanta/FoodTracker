package com.example.foodtracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.viewmodel.AuthUiState
import com.example.foodtracker.viewmodel.AuthViewModel

@Composable
fun VerifyEmailScreen(
    authViewModel: AuthViewModel,
    onNavigateHome: () -> Unit,
    onNavigateOnboarding: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()
    val state by authViewModel.verifyEmailState.collectAsState()
    val email = authViewModel.currentUserEmail ?: ""

    LaunchedEffect(Unit) { authViewModel.resetVerifyEmailState() }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.NavigateHome       -> onNavigateHome()
            is AuthUiState.NavigateOnboarding -> onNavigateOnboarding()
            is AuthUiState.NavigateLogin      -> onLoggedOut()
            else                              -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(100.dp))
            Text("📧", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.verify_title), fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold, color = GlassColors.textPrimary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.verify_message, email), fontSize = 14.sp,
                color = GlassColors.textSecondary, textAlign = TextAlign.Center)

            AnimatedVisibility(
                visible = state.notYetVerified,
                enter = fadeIn(tween(200)), exit = fadeOut(tween(200))
            ) {
                Text(stringResource(R.string.verify_not_yet), color = Color(0xFFFF4444),
                    fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }

            Spacer(Modifier.height(32.dp))

            AnimatedActionButton(
                label = stringResource(R.string.verify_checked_button),
                phase = if (state.isChecking) ActionPhase.Loading else ActionPhase.Idle,
                successLabel = null,
                contentColor = Color.Black,
                modifier = Modifier.fillMaxWidth(),
                onClick = { authViewModel.checkEmailVerified() }
            )

            Spacer(Modifier.height(16.dp))

            val cooldown = state.resendCooldownSec
            val resendLabel = if (cooldown > 0)
                stringResource(R.string.verify_resend_cooldown, cooldown)
            else stringResource(R.string.verify_resend_button)
            Box(
                Modifier.fillMaxWidth().height(52.dp).glassCard(16)
                    .clickable(enabled = cooldown == 0 && !state.isResending) {
                        authViewModel.resendVerificationEmail()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(resendLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (cooldown > 0) GlassColors.textTertiary else GlassColors.textPrimary)
            }

            Spacer(Modifier.height(32.dp))

            Text(stringResource(R.string.verify_use_other_account), fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, color = GlassColors.accentGreen,
                modifier = Modifier.clickable { authViewModel.logout() })
        }
    }
}
