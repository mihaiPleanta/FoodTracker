package com.example.foodtracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.viewmodel.AuthUiState
import com.example.foodtracker.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateRegister: () -> Unit,
    onNavigateForgot: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateOnboarding: () -> Unit,
    onNavigateVerifyEmail: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val uiState by authViewModel.uiState.collectAsState()
    val isLoading = uiState is AuthUiState.Loading
    val errorResId = (uiState as? AuthUiState.Error)?.messageRes
    val errorMessage = errorResId?.let { stringResource(it) }

    val context = LocalContext.current
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.example.foodtracker.R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Procesăm rezultatul indiferent de resultCode: la eșec (ex. SHA-1
        // neînregistrat) activitatea întoarce RESULT_CANCELED, dar detaliul erorii
        // e tot în intent, expus prin ApiException.statusCode.
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) authViewModel.signInWithGoogle(idToken)
            else authViewModel.reportGoogleSignInError()
        } catch (e: ApiException) {
            // Doar anularea explicită a userului e silențioasă; orice altă eroare
            // (ex. SHA-1 neînregistrat / config OAuth) se afișează.
            if (e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED &&
                e.statusCode != CommonStatusCodes.CANCELED
            ) {
                authViewModel.reportGoogleSignInError()
            }
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.NavigateHome        -> onNavigateHome()
            is AuthUiState.NavigateOnboarding  -> onNavigateOnboarding()
            is AuthUiState.NavigateVerifyEmail -> onNavigateVerifyEmail()
            else                               -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            Text("🥗", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text("FoodTracker", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                color = GlassColors.textPrimary)
            Text(stringResource(R.string.login_subtitle), fontSize = 13.sp,
                color = GlassColors.textSecondary)

            Spacer(Modifier.height(40.dp))

            AuthTextField(
                value = email,
                onValueChange = { email = it; authViewModel.resetState() },
                placeholder = stringResource(R.string.login_email),
                keyboardType = KeyboardType.Email
            )
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = password,
                onValueChange = { password = it; authViewModel.resetState() },
                placeholder = stringResource(R.string.login_password),
                keyboardType = KeyboardType.Password,
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePasswordVisibility = { passwordVisible = !passwordVisible }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    stringResource(R.string.login_forgot_password),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassColors.accentGreen,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            authViewModel.setResetEmailPrefill(email)
                            onNavigateForgot()
                        }
                )
            }

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
                text = stringResource(R.string.login_button),
                enabled = email.isNotBlank() && password.isNotBlank(),
                isLoading = isLoading,
                modifier = Modifier.fillMaxWidth(),
                onClick = { authViewModel.loginWithEmail(email, password) }
            )

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Box(Modifier.weight(1f).height(1.dp).background(GlassColors.cardBorder))
                Text("  sau  ", fontSize = 12.sp, color = GlassColors.textTertiary)
                Box(Modifier.weight(1f).height(1.dp).background(GlassColors.cardBorder))
            }

            Spacer(Modifier.height(20.dp))

            Box(
                Modifier.fillMaxWidth().height(52.dp).glassCard(16)
                    .clickable {
                        googleSignInClient.signOut()
                        googleLauncher.launch(googleSignInClient.signInIntent)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.login_google), fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = GlassColors.textPrimary)
            }

            Spacer(Modifier.height(32.dp))

            Row {
                Text(stringResource(R.string.login_no_account), fontSize = 13.sp, color = GlassColors.textSecondary)
                Text(
                    stringResource(R.string.login_register_link),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassColors.accentGreen,
                    modifier = Modifier.clickable { onNavigateRegister() }
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
