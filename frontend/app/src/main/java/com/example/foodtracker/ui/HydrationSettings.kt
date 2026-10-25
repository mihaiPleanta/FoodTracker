package com.example.foodtracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.R
import com.example.foodtracker.model.ActivityLevel
import com.example.foodtracker.model.DEFAULT_HYDRATION_GOAL_L
import com.example.foodtracker.model.HydrationGoal
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.viewmodel.FoodViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_MANUAL_GOAL_L = 1.0f
private const val MAX_MANUAL_GOAL_L = 6.0f

/** Moves the manual goal by [delta], rounded to 0.1 L and clamped to [1.0, 6.0]. */
internal fun stepManualGoal(current: Float, delta: Float): Float {
    val tenths = ((current + delta) * 10f).roundToInt()
    return (tenths / 10f).coerceIn(MIN_MANUAL_GOAL_L, MAX_MANUAL_GOAL_L)
}

/** Every value the wheel picker offers: 1.0 .. 6.0 L in 0.1 L steps. */
internal fun manualGoalOptions(): List<Float> {
    val from = (MIN_MANUAL_GOAL_L * 10f).roundToInt()
    val to = (MAX_MANUAL_GOAL_L * 10f).roundToInt()
    return (from..to).map { it / 10f }
}

/** Index of [liters] in [manualGoalOptions], rounded to the nearest tenth and clamped. */
internal fun manualGoalIndex(liters: Float): Int {
    val tenths = (liters.coerceIn(MIN_MANUAL_GOAL_L, MAX_MANUAL_GOAL_L) * 10f).roundToInt()
    return tenths - (MIN_MANUAL_GOAL_L * 10f).roundToInt()
}

internal fun formatLiters(l: Float): String = "%.1f".format(Locale.ENGLISH, l) + "L"

/** One decimal when the value is a multiple of 0.1 (0.5L), otherwise two (2.45L). */
internal fun formatPartLiters(l: Float): String {
    val onTenth = abs(l * 10f - (l * 10f).roundToInt()) < 0.001f
    return "%.${if (onTenth) 1 else 2}f".format(Locale.ENGLISH, l) + "L"
}

/** Kilograms without a trailing ".0" (70 / 78.1). */
internal fun formatKg(kg: Float): String =
    if (abs(kg - kg.roundToInt()) < 0.05f) kg.roundToInt().toString()
    else "%.1f".format(Locale.ENGLISH, kg)

private const val AUTO_GOAL_MIN_L = 1.5f
private const val AUTO_GOAL_MAX_L = 5.0f

internal enum class HydrationClamp { MIN, MAX }

/**
 * Whether the automatic goal was clamped to [1.5, 5.0] L, i.e. the breakdown lines don't add up
 * to it. Null in manual mode or when the (rounded) sum of the parts already equals the goal.
 */
internal fun hydrationClamp(h: HydrationGoal): HydrationClamp? {
    if (h.isManual) return null
    val sum = h.weightPartL + h.activityBonusL + h.creatineBonusL
    return when {
        abs(h.goalL - AUTO_GOAL_MIN_L) < 0.001f && sum < AUTO_GOAL_MIN_L - 0.05f -> HydrationClamp.MIN
        abs(h.goalL - AUTO_GOAL_MAX_L) < 0.001f && sum > AUTO_GOAL_MAX_L + 0.05f -> HydrationClamp.MAX
        else -> null
    }
}

@Composable
fun HydrationSettingsSection(viewModel: FoodViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val goals by viewModel.nutritionGoals.collectAsState()
    val hydration = goals?.hydration

    val isManual = profile.hydrationGoalOverrideL != null
    val autoFallback = hydration?.autoL ?: DEFAULT_HYDRATION_GOAL_L
    val displayed = profile.hydrationGoalOverrideL ?: (hydration?.goalL ?: DEFAULT_HYDRATION_GOAL_L)
    val takes = profile.takesCreatine
    var showPicker by remember { mutableStateOf(false) }

    ProfileSectionLabel(
        stringResource(R.string.settings_hydration),
        Modifier.padding(horizontal = 20.dp)
    )
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .glassCard(16)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                HydrationModePill(stringResource(R.string.hydration_mode_auto), !isManual) {
                    if (isManual) viewModel.updateHydrationPrefs(takes, null)
                }
                HydrationModePill(stringResource(R.string.hydration_mode_manual), isManual) {
                    if (!isManual) viewModel.updateHydrationPrefs(takes, autoFallback)
                }
            }
            Text(
                formatLiters(displayed),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GlassColors.accentBlue,
                textDecoration = if (isManual) TextDecoration.Underline else null,
                modifier = if (isManual) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showPicker = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                } else Modifier
            )
        }

        if (showPicker) {
            LitersPickerDialog(
                initial = displayed,
                onDismiss = { showPicker = false },
                onConfirm = {
                    showPicker = false
                    viewModel.updateHydrationPrefs(takes, it)
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        if (isManual) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.hydration_manual_fixed),
                        fontSize = 13.sp,
                        color = GlassColors.textSecondary
                    )
                    if (hydration != null) {
                        Text(
                            stringResource(R.string.hydration_auto_would_be, formatLiters(hydration.autoL)),
                            fontSize = 12.sp,
                            color = GlassColors.textTertiary
                        )
                    }
                }
                StepButton("−") {
                    viewModel.updateHydrationPrefs(takes, stepManualGoal(displayed, -0.1f), debounce = true)
                }
                Spacer(Modifier.width(8.dp))
                StepButton("+") {
                    viewModel.updateHydrationPrefs(takes, stepManualGoal(displayed, 0.1f), debounce = true)
                }
            }
        } else if (hydration != null) {
            val activityLabel = when (profile.activityLevel) {
                ActivityLevel.SEDENTARY -> ""   // bonus 0 → line hidden
                ActivityLevel.LIGHT -> stringResource(R.string.hydration_activity_light)
                ActivityLevel.MODERATE -> stringResource(R.string.hydration_activity_moderate)
                ActivityLevel.ACTIVE -> stringResource(R.string.hydration_activity_active)
                ActivityLevel.VERY_ACTIVE -> stringResource(R.string.hydration_activity_very_active)
            }
            BreakdownLine(
                stringResource(
                    R.string.hydration_breakdown_weight,
                    formatKg(profile.currentWeightKg),
                    formatPartLiters(hydration.weightPartL)
                )
            )
            if (hydration.activityBonusL > 0f) {
                BreakdownLine(
                    stringResource(
                        R.string.hydration_breakdown_activity,
                        activityLabel,
                        formatPartLiters(hydration.activityBonusL)
                    )
                )
            }
            if (takes) {
                BreakdownLine(
                    stringResource(
                        R.string.hydration_breakdown_creatine,
                        formatPartLiters(hydration.creatineBonusL)
                    )
                )
            }
            when (hydrationClamp(hydration)) {
                HydrationClamp.MIN -> BreakdownLine(
                    stringResource(R.string.hydration_clamp_min, formatLiters(hydration.goalL))
                )
                HydrationClamp.MAX -> BreakdownLine(
                    stringResource(R.string.hydration_clamp_max, formatLiters(hydration.goalL))
                )
                null -> {}
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isManual) 0.4f else 1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.hydration_creatine),
                    fontSize = 15.sp,
                    color = GlassColors.textPrimary
                )
                Text(
                    stringResource(
                        if (isManual) R.string.hydration_creatine_manual_sub
                        else R.string.hydration_creatine_sub
                    ),
                    fontSize = 12.sp,
                    color = GlassColors.textTertiary
                )
            }
            Switch(
                checked = takes,
                onCheckedChange = { viewModel.updateHydrationPrefs(it, profile.hydrationGoalOverrideL) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = GlassColors.accentGreen,
                    uncheckedThumbColor = GlassColors.textSecondary,
                    uncheckedTrackColor = GlassColors.cardBorder
                )
            )
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun BreakdownLine(text: String) {
    Text(text, fontSize = 13.sp, color = GlassColors.textSecondary, modifier = Modifier.padding(vertical = 1.dp))
}

@Composable
private fun HydrationModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) GlassColors.accentGreen else GlassColors.cardBackground)
            .border(1.dp, if (selected) GlassColors.accentGreen else GlassColors.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.Black else GlassColors.textSecondary
        )
    }
}

@Composable
private fun LitersPickerDialog(
    initial: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    val options = remember { manualGoalOptions() }
    var selected by remember { mutableIntStateOf(manualGoalIndex(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hydration_picker_title), color = GlassColors.textPrimary) },
        text = { LitersWheel(options, initialIndex = selected, onSelectedChange = { selected = it }) },
        confirmButton = {
            TextButton(onClick = { onConfirm(options[selected]) }) {
                Text(
                    stringResource(R.string.action_save),
                    color = GlassColors.accentGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = GlassColors.textSecondary)
            }
        },
        containerColor = GlassColors.cardBackground,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Snapping wheel: the item in the middle slot is the selection. Two items of padding above
 * and below let the first/last values reach the middle.
 */
@Composable
private fun LitersWheel(options: List<Float>, initialIndex: Int, onSelectedChange: (Int) -> Unit) {
    val itemHeight = 44.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val centered by remember {
        derivedStateOf {
            val pastHalf = listState.firstVisibleItemScrollOffset > itemHeightPx / 2
            (listState.firstVisibleItemIndex + if (pastHalf) 1 else 0).coerceIn(options.indices)
        }
    }
    LaunchedEffect(centered) { onSelectedChange(centered) }

    val accent = GlassColors.accentBlue
    val primary = GlassColors.textPrimary
    val tertiary = GlassColors.textTertiary
    Box(Modifier.fillMaxWidth().height(itemHeight * 5)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * 2),
            modifier = Modifier.fillMaxSize()
        ) {
            items(options.size) { i ->
                val isCentered = i == centered
                Box(Modifier.fillMaxWidth().height(itemHeight), contentAlignment = Alignment.Center) {
                    Text(
                        formatLiters(options[i]),
                        fontSize = if (isCentered) 22.sp else 16.sp,
                        fontWeight = if (isCentered) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isCentered) primary else tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(GlassColors.cardBackground)
            .border(1.dp, GlassColors.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlassColors.textPrimary)
    }
}
