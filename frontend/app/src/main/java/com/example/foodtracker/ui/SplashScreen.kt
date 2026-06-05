package com.example.foodtracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.viewmodel.AuthUiState
import com.example.foodtracker.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    authViewModel: AuthViewModel,
    onNavigateLogin: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateOnboarding: () -> Unit,
    onNavigateVerifyEmail: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(600), label = "splashAlpha")
    val scale by animateFloatAsState(if (visible) 1f else 0.85f, tween(600), label = "splashScale")

    val uiState by authViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        visible = true
        delay(900)
        authViewModel.checkAuthState()
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.NavigateLogin       -> onNavigateLogin()
            is AuthUiState.NavigateHome        -> onNavigateHome()
            is AuthUiState.NavigateOnboarding  -> onNavigateOnboarding()
            is AuthUiState.NavigateVerifyEmail -> onNavigateVerifyEmail()
            else                               -> Unit
        }
    }

    Box(
        Modifier.fillMaxSize().background(GlassColors.backgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha).scale(scale)
        ) {
            Text("🥗", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "FoodTracker",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GlassColors.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.app_tagline),
                fontSize = 14.sp,
                color = GlassColors.textSecondary
            )
        }
    }
}
