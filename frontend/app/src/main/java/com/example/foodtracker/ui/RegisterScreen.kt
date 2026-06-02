package com.example.foodtracker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.viewmodel.AuthUiState
import com.example.foodtracker.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    authViewModel: AuthViewModel,
    onNavigateLogin: () -> Unit,
    onNavigateOnboarding: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val uiState by authViewModel.uiState.collectAsState()
    val isLoading = uiState is AuthUiState.Loading
    val firebaseErrorRes = (uiState as? AuthUiState.Error)?.messageRes
    val firebaseError = firebaseErrorRes?.let { stringResource(it) }
    val errorMessage = localError ?: firebaseError

    val strPasswordTooShort = stringResource(R.string.register_password_too_short)
    val strPasswordsMismatch = stringResource(R.string.register_passwords_mismatch)

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.NavigateOnboarding) onNavigateOnboarding()
    }

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(GlassColors.cardBackground)
                        .border(1.dp, GlassColors.cardBorder, CircleShape)
                        .clickable { onNavigateLogin() },
                    Alignment.Center
                ) {
                    Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = stringResource(R.string.action_back),
                        tint = GlassColors.textSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(stringResource(R.string.register_title), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                color = GlassColors.textPrimary)
            Text(stringResource(R.string.register_subtitle), fontSize = 13.sp,
                color = GlassColors.textSecondary)

            Spacer(Modifier.height(32.dp))

            AuthTextField(
                value = email,
                onValueChange = { email = it; localError = null; authViewModel.resetState() },
                placeholder = stringResource(R.string.login_email),
                keyboardType = KeyboardType.Email
            )
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = password,
                onValueChange = { password = it; localError = null; authViewModel.resetState() },
                placeholder = stringResource(R.string.login_password),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
            )
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; localError = null },
                placeholder = stringResource(R.string.register_confirm_password),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                passwordVisible = passwordVisible
            )

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(tween(200)), exit = fadeOut(tween(200))
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = Color(0xFFFF4444),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            AuthPrimaryButton(
                text = stringResource(R.string.register_button),
                enabled = email.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank(),
                isLoading = isLoading,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    when {
                        password.length < 6 ->
                            localError = strPasswordTooShort
                        password != confirmPassword ->
                            localError = strPasswordsMismatch
                        else ->
                            authViewModel.registerWithEmail(email, password)
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            Row {
                Text(stringResource(R.string.register_have_account), fontSize = 13.sp, color = GlassColors.textSecondary)
                Text(
                    stringResource(R.string.login_button),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassColors.accentGreen,
                    modifier = Modifier.clickable { onNavigateLogin() }
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
