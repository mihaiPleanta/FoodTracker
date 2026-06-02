package com.example.foodtracker.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import com.example.foodtracker.model.ActivityLevel
import com.example.foodtracker.model.Gender
import com.example.foodtracker.model.UserProfile
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.viewmodel.AuthViewModel
import com.example.foodtracker.viewmodel.FoodViewModel
import androidx.compose.ui.res.stringResource
import com.example.foodtracker.R
import com.example.foodtracker.viewmodel.ProfileSaveState

@Composable
fun ProfileScreen(viewModel: FoodViewModel, authViewModel: AuthViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val saveState by authViewModel.profileSaveState.collectAsState()

    var name          by remember(profile) { mutableStateOf(profile.name) }
    var age           by remember(profile) { mutableStateOf(profile.age.toString()) }
    var gender        by remember(profile) { mutableStateOf(profile.gender) }
    var heightCm      by remember(profile) { mutableStateOf(profile.heightCm.toString()) }
    var currentWeight by remember(profile) { mutableStateOf(profile.currentWeightKg.toString()) }
    var targetWeight  by remember(profile) { mutableStateOf(profile.targetWeightKg.toString()) }
    var activityLevel by remember(profile) { mutableStateOf(profile.activityLevel) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val profileSaveErrorMsg = (saveState as? ProfileSaveState.Error)?.messageRes
        ?.let { stringResource(it) }

    LaunchedEffect(saveState) {
        when (saveState) {
            is ProfileSaveState.Success -> {
                delay(1200)
                authViewModel.clearProfileSaveState()
            }
            is ProfileSaveState.Error -> {
                snackbarHostState.showSnackbar(profileSaveErrorMsg ?: "")
                authViewModel.clearProfileSaveState()
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = GlassColors.backgroundDark,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF2A1A1A),
                    contentColor = Color(0xFFFF6B6B)
                )
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(GlassColors.backgroundDark)
                .padding(innerPadding)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(28.dp))

                ProfileAvatarHeader(name = name, gender = gender, age = age)

                Spacer(Modifier.height(28.dp))

                ProfileSectionLabel(
                    stringResource(R.string.profile_personal_info),
                    Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .glassCard(16)
                ) {
                    ProfileTextField(stringResource(R.string.profile_name), name) { name = it }
                    ProfileRowDivider()
                    ProfileTextField(
                        label = stringResource(R.string.profile_age),
                        value = age,
                        suffix = stringResource(R.string.unit_years),
                        keyboard = KeyboardType.Number,
                        onValueChange = { age = it }
                    )
                    ProfileRowDivider()
                    ProfileGenderRow(selected = gender, onSelect = { gender = it })
                    ProfileRowDivider()
                    ProfileTextField(
                        label = stringResource(R.string.profile_height),
                        value = heightCm,
                        suffix = "cm",
                        keyboard = KeyboardType.Number,
                        onValueChange = { heightCm = it }
                    )
                }

                Spacer(Modifier.height(14.dp))

                ProfileSectionLabel(
                    stringResource(R.string.profile_body_goals),
                    Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .glassCard(16)
                ) {
                    ProfileTextField(
                        label = stringResource(R.string.profile_current_weight),
                        value = currentWeight,
                        suffix = "kg",
                        keyboard = KeyboardType.Decimal,
                        onValueChange = { currentWeight = it }
                    )
                    ProfileRowDivider()
                    ProfileTextField(
                        label = stringResource(R.string.profile_target_weight),
                        value = targetWeight,
                        suffix = "kg",
                        keyboard = KeyboardType.Decimal,
                        onValueChange = { targetWeight = it }
                    )
                    ProfileRowDivider()
                    ProfileActivityRow(
                        selected = activityLevel,
                        onSelect = { activityLevel = it }
                    )
                }

                Spacer(Modifier.height(28.dp))

                ProfileSaveButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    saveState = saveState,
                    onClick = {
                        val newWeight = currentWeight.replace(",", ".").toFloatOrNull()
                            ?: profile.currentWeightKg
                        val weightChanged = newWeight != profile.currentWeightKg

                        val updated = UserProfile(
                            name            = name.trim().ifBlank { profile.name },
                            age             = age.toIntOrNull() ?: profile.age,
                            gender          = gender,
                            heightCm        = heightCm.toIntOrNull() ?: profile.heightCm,
                            currentWeightKg = newWeight,
                            targetWeightKg  = targetWeight.replace(",", ".").toFloatOrNull()
                                                ?: profile.targetWeightKg,
                            activityLevel   = activityLevel
                        )
                        viewModel.updateUserProfile(updated)
                        authViewModel.saveProfileEdit(updated)

                        if (weightChanged) {
                            viewModel.addWeightCheckIn(newWeight, Date())
                        }
                    }
                )

                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

// ── Avatar header ─────────────────────────────────────────────────────────────

@Composable
fun ProfileAvatarHeader(name: String, gender: Gender, age: String) {
    val initials = name.trim()
        .split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { "?" }

    val genderLabel = when (gender) {
        Gender.MALE   -> stringResource(R.string.gender_male)
        Gender.FEMALE -> stringResource(R.string.gender_female)
        Gender.OTHER  -> stringResource(R.string.gender_other)
    }
    val profileTitle = stringResource(R.string.profile_title)
    val unitYears = stringResource(R.string.unit_years)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            GlassColors.accentGreen.copy(alpha = 0.8f),
                            GlassColors.accentBlue.copy(alpha = 0.8f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = name.trim().ifBlank { profileTitle },
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = GlassColors.textPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${age.toIntOrNull() ?: "–"} $unitYears · $genderLabel",
            fontSize = 13.sp,
            color = GlassColors.textSecondary
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
fun ProfileSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = GlassColors.textTertiary,
        letterSpacing = 1.sp,
        modifier = modifier
    )
}

@Composable
fun ProfileRowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlassColors.cardBorder)
    )
}

@Composable
fun ProfileTextField(
    label: String,
    value: String,
    suffix: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = GlassColors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = GlassColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text("–", color = GlassColors.textTertiary, fontSize = 14.sp)
                        }
                        inner()
                    }
                }
            )
            if (suffix.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(suffix, fontSize = 12.sp, color = GlassColors.textTertiary)
            }
        }
    }
}

@Composable
fun ProfileGenderRow(selected: Gender, onSelect: (Gender) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.profile_gender),
            fontSize = 14.sp,
            color = GlassColors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gender.entries.forEach { g ->
                val isSelected = g == selected
                val label = when (g) {
                    Gender.MALE   -> "M"
                    Gender.FEMALE -> "F"
                    Gender.OTHER  -> stringResource(R.string.gender_other)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) GlassColors.accentGreen
                            else GlassColors.cardBackground
                        )
                        .border(
                            1.dp,
                            if (isSelected) GlassColors.accentGreen else GlassColors.cardBorder,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelect(g) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.Black else GlassColors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileActivityRow(selected: ActivityLevel, onSelect: (ActivityLevel) -> Unit) {
    val labels = mapOf(
        ActivityLevel.SEDENTARY   to stringResource(R.string.activity_sedentary),
        ActivityLevel.LIGHT       to stringResource(R.string.activity_light),
        ActivityLevel.MODERATE    to stringResource(R.string.activity_moderate),
        ActivityLevel.ACTIVE      to stringResource(R.string.activity_active),
        ActivityLevel.VERY_ACTIVE to stringResource(R.string.activity_very_active)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            stringResource(R.string.profile_activity),
            fontSize = 14.sp,
            color = GlassColors.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActivityLevel.entries.forEach { level ->
                val isSelected = level == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) GlassColors.accentGreen
                            else GlassColors.cardBackground
                        )
                        .border(
                            1.dp,
                            if (isSelected) GlassColors.accentGreen else GlassColors.cardBorder,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelect(level) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        labels[level] ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.Black else GlassColors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileSaveButton(
    modifier: Modifier = Modifier,
    saveState: ProfileSaveState = ProfileSaveState.Idle,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val isSaving = saveState is ProfileSaveState.Saving
    val isSaved  = saveState is ProfileSaveState.Success
    val isEnabled = !isSaving

    val scale by animateFloatAsState(
        targetValue = when {
            isSaved  -> 1.04f
            isPressed && isEnabled -> 0.92f
            else     -> 1f
        },
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "saveScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(GlassColors.accentGreen, GlassColors.accentGreenDim)
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            isSaving -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.Black,
                strokeWidth = 2.5.dp
            )
            isSaved -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.profile_saved), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            else -> Text(
                stringResource(R.string.profile_save_button),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}
