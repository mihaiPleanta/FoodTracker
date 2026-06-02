package com.example.foodtracker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.model.ActivityLevel
import com.example.foodtracker.model.Gender
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.viewmodel.AuthUiState
import com.example.foodtracker.viewmodel.AuthViewModel

@Composable
fun OnboardingScreen(
    authViewModel: AuthViewModel,
    onNavigateHome: () -> Unit
) {
    val step by authViewModel.onboardingStep.collectAsState()
    val data by authViewModel.onboardingData.collectAsState()
    val uiState by authViewModel.uiState.collectAsState()
    val isSubmitting = uiState is AuthUiState.Loading

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.NavigateHome) onNavigateHome()
    }

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(28.dp))

            // Progress indicator
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier.weight(1f).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index <= step) GlassColors.accentGreen
                                else GlassColors.cardBorder
                            )
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)) togetherWith
                    slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
                },
                label = "onboardingStep"
            ) { currentStep ->
                when (currentStep) {
                    0 -> OnboardingStep1(
                        name = data.name,
                        onNameChange = { authViewModel.updateOnboardingData { copy(name = it) } },
                        onNext = { if (data.name.isNotBlank()) authViewModel.nextStep() }
                    )
                    1 -> OnboardingStep2(
                        age = data.age,
                        gender = data.gender,
                        heightCm = data.heightCm,
                        currentWeightKg = data.currentWeightKg,
                        onAgeChange = { authViewModel.updateOnboardingData { copy(age = it) } },
                        onGenderChange = { authViewModel.updateOnboardingData { copy(gender = it) } },
                        onHeightChange = { authViewModel.updateOnboardingData { copy(heightCm = it) } },
                        onWeightChange = { authViewModel.updateOnboardingData { copy(currentWeightKg = it) } },
                        onBack = { authViewModel.prevStep() },
                        onNext = {
                            val valid = data.age.toIntOrNull() != null &&
                                data.heightCm.toIntOrNull() != null &&
                                data.currentWeightKg.replace(",", ".").toFloatOrNull() != null
                            if (valid) authViewModel.nextStep()
                        }
                    )
                    else -> OnboardingStep3(
                        targetWeightKg = data.targetWeightKg,
                        activityLevel = data.activityLevel,
                        calculatedCalorieGoal = data.calculatedCalorieGoal,
                        isTdeeLoading = data.isTdeeLoading,
                        isSubmitting = isSubmitting,
                        onTargetWeightChange = { authViewModel.updateOnboardingData { copy(targetWeightKg = it) } },
                        onActivityChange = { authViewModel.updateOnboardingData { copy(activityLevel = it) } },
                        onCalculate = { authViewModel.calculateTdee() },
                        onBack = { authViewModel.prevStep() },
                        onFinish = { authViewModel.submitProfile() }
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun OnboardingStep1(
    name: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column {
        Text(stringResource(R.string.onboarding_welcome), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            color = GlassColors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.onboarding_name_q), fontSize = 14.sp, color = GlassColors.textSecondary)
        Spacer(Modifier.height(28.dp))
        AuthTextField(value = name, onValueChange = onNameChange, placeholder = stringResource(R.string.onboarding_name_hint))
        Spacer(Modifier.height(24.dp))
        AuthPrimaryButton(
            text = stringResource(R.string.action_continue),
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = onNext
        )
    }
}

@Composable
private fun OnboardingStep2(
    age: String,
    gender: Gender,
    heightCm: String,
    currentWeightKg: String,
    onAgeChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column {
        Text(stringResource(R.string.onboarding_details_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            color = GlassColors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.onboarding_details_subtitle), fontSize = 14.sp, color = GlassColors.textSecondary)
        Spacer(Modifier.height(28.dp))

        AuthTextField(value = age, onValueChange = onAgeChange,
            placeholder = stringResource(R.string.onboarding_age), keyboardType = KeyboardType.Number)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Gender.MALE to stringResource(R.string.gender_male), Gender.FEMALE to stringResource(R.string.gender_female), Gender.OTHER to stringResource(R.string.gender_other))
                .forEach { (g, label) ->
                    val selected = g == gender
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) GlassColors.accentGreen else GlassColors.cardBackground)
                            .border(1.dp, if (selected) GlassColors.accentGreen else GlassColors.cardBorder, RoundedCornerShape(12.dp))
                            .clickable { onGenderChange(g) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.Black else GlassColors.textSecondary)
                    }
                }
        }
        Spacer(Modifier.height(12.dp))

        AuthTextField(value = heightCm, onValueChange = onHeightChange,
            placeholder = stringResource(R.string.onboarding_height), keyboardType = KeyboardType.Number)
        Spacer(Modifier.height(12.dp))
        AuthTextField(value = currentWeightKg, onValueChange = onWeightChange,
            placeholder = stringResource(R.string.onboarding_current_weight), keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).height(52.dp).glassCard(16).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.action_back), fontSize = 15.sp, color = GlassColors.textSecondary)
            }
            Box(
                Modifier.weight(2f).height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(
                        listOf(GlassColors.accentGreen, GlassColors.accentGreenDim)))
                    .clickable { onNext() },
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.action_continue), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
private fun OnboardingStep3(
    targetWeightKg: String,
    activityLevel: ActivityLevel,
    calculatedCalorieGoal: Int?,
    isTdeeLoading: Boolean,
    isSubmitting: Boolean,
    onTargetWeightChange: (String) -> Unit,
    onActivityChange: (ActivityLevel) -> Unit,
    onCalculate: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val strSedentary = stringResource(R.string.activity_sedentary)
    val strLight = stringResource(R.string.activity_light)
    val strModerate = stringResource(R.string.activity_moderate)
    val strActive = stringResource(R.string.activity_active)
    val strVeryActive = stringResource(R.string.activity_very_active)
    val activityLabels = mapOf(
        ActivityLevel.SEDENTARY to strSedentary,
        ActivityLevel.LIGHT to strLight,
        ActivityLevel.MODERATE to strModerate,
        ActivityLevel.ACTIVE to strActive,
        ActivityLevel.VERY_ACTIVE to strVeryActive
    )

    Column {
        Text(stringResource(R.string.onboarding_goal_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            color = GlassColors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.onboarding_goal_subtitle), fontSize = 14.sp, color = GlassColors.textSecondary)
        Spacer(Modifier.height(28.dp))

        AuthTextField(value = targetWeightKg, onValueChange = onTargetWeightChange,
            placeholder = stringResource(R.string.onboarding_target_weight), keyboardType = KeyboardType.Decimal)
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.onboarding_activity), fontSize = 13.sp, color = GlassColors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActivityLevel.entries.forEach { level ->
                val selected = level == activityLevel
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (selected) GlassColors.accentGreen else GlassColors.cardBackground)
                        .border(1.dp, if (selected) GlassColors.accentGreen else GlassColors.cardBorder, RoundedCornerShape(20.dp))
                        .clickable { onActivityChange(level) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(activityLabels[level] ?: "", fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.Black else GlassColors.textSecondary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            Modifier.fillMaxWidth().height(48.dp).glassCard(14)
                .border(1.dp, GlassColors.accentGreen.copy(0.4f), RoundedCornerShape(14.dp))
                .clickable(enabled = !isTdeeLoading) { onCalculate() },
            contentAlignment = Alignment.Center
        ) {
            if (isTdeeLoading) {
                CircularProgressIndicator(color = GlassColors.accentGreen,
                    modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.onboarding_calculate), fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, color = GlassColors.accentGreen)
            }
        }

        AnimatedVisibility(
            visible = calculatedCalorieGoal != null,
            enter = fadeIn(tween(300)) + expandVertically(tween(300))
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().glassCard(16)
                        .border(1.dp, GlassColors.accentGreen.copy(0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.onboarding_calculated), fontSize = 12.sp,
                            color = GlassColors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("${calculatedCalorieGoal ?: 0} kcal/zi", fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold, color = GlassColors.accentGreen)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).height(52.dp).glassCard(16).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.action_back), fontSize = 15.sp, color = GlassColors.textSecondary)
            }
            AuthPrimaryButton(
                text = stringResource(R.string.onboarding_start),
                isLoading = isSubmitting,
                modifier = Modifier.weight(2f),
                onClick = onFinish
            )
        }
    }
}
