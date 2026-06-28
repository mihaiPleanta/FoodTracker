package com.example.foodtracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.foodtracker.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.foodtracker.model.NutritionGoals
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.viewmodel.FoodViewModel
import com.example.foodtracker.viewmodel.WeightCheckIn
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

private data class MealSplit(
    val name: String,
    val calories: Int,
    val accentColor: Color
)

@Composable
fun StatsScreen(
    viewModel: FoodViewModel
) {
    val selectedDate by viewModel.selectedHomeDate.collectAsState()
    val breakfastFoods by viewModel.breakfastFoods.collectAsState()
    val lunchFoods by viewModel.lunchFoods.collectAsState()
    val dinnerFoods by viewModel.dinnerFoods.collectAsState()
    val snacksFoods by viewModel.snacksFoods.collectAsState()
    val hydrationLiters by viewModel.hydrationTodayLiters.collectAsState()
    val weightHistory by viewModel.weightHistory.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val loadingDay by viewModel.loadingDay.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadWeightHistory()
    }
    var showWeightSheet by remember { mutableStateOf(false) }

    val goals = viewModel.nutritionGoals.collectAsState().value ?: NutritionGoals()

    val totalCalories = remember(breakfastFoods, lunchFoods, dinnerFoods, snacksFoods) {
        (breakfastFoods + lunchFoods + dinnerFoods + snacksFoods).sumOf { it.calories }
    }
    val totalProtein = remember(breakfastFoods, lunchFoods, dinnerFoods, snacksFoods) {
        (breakfastFoods + lunchFoods + dinnerFoods + snacksFoods).sumOf { it.protein.toDouble() }.toInt()
    }
    val totalCarbs = remember(breakfastFoods, lunchFoods, dinnerFoods, snacksFoods) {
        (breakfastFoods + lunchFoods + dinnerFoods + snacksFoods).sumOf { it.carbs.toDouble() }.toInt()
    }
    val totalFat = remember(breakfastFoods, lunchFoods, dinnerFoods, snacksFoods) {
        (breakfastFoods + lunchFoods + dinnerFoods + snacksFoods).sumOf { it.fat.toDouble() }.toInt()
    }

    val mealSplits = remember(breakfastFoods, lunchFoods, dinnerFoods, snacksFoods) {
        listOf(
            MealSplit("Breakfast", breakfastFoods.sumOf { it.calories }, Color(0xFFFFD600)),
            MealSplit("Lunch", lunchFoods.sumOf { it.calories }, Color(0xFF00E676)),
            MealSplit("Dinner", dinnerFoods.sumOf { it.calories }, Color(0xFF448AFF)),
            MealSplit("Snacks", snacksFoods.sumOf { it.calories }, Color(0xFFFF6D00))
        )
    }

    val dateLabel = remember(selectedDate) {
        SimpleDateFormat("EEEE, d MMM yyyy", Locale.ENGLISH).format(selectedDate)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(GlassColors.backgroundDark)
    ) {
        Column(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxSize()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            if (loadingDay) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.stats_title),
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GlassColors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = dateLabel,
                fontSize = 13.sp,
                color = GlassColors.textSecondary
            )

            Spacer(Modifier.height(18.dp))

            QuickKpiRow(
                totalCalories = totalCalories,
                calorieGoal = goals.calorieGoal,
                hydrationLiters = hydrationLiters,
                hydrationGoal = viewModel.hydrationGoalLiters,
                totalProtein = totalProtein
            )

            Spacer(Modifier.height(12.dp))

            if (loadingDay) {
                // Ziua se încarcă: bara de progres de sus comunică starea,
                // nu arătăm nici empty-state, nici carduri cu zerouri.
            } else if (totalCalories == 0) {
                EmptyStateCard(
                    emoji = "🍽️",
                    title = stringResource(R.string.stats_empty_day_title),
                    subtitle = stringResource(R.string.stats_empty_day_subtitle),
                )
            } else {
                MacroProgressCard(
                    totalProtein = totalProtein,
                    proteinGoal = goals.proteinGoal,
                    totalCarbs = totalCarbs,
                    carbsGoal = goals.carbsGoal,
                    totalFat = totalFat,
                    fatGoal = goals.fatGoal
                )

                Spacer(Modifier.height(12.dp))

                MealDistributionCard(
                    meals = mealSplits,
                    totalCalories = totalCalories
                )
            }

            Spacer(Modifier.height(12.dp))

            if (weightHistory.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(20)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.stats_no_checkins),
                        fontSize = 13.sp,
                        color = GlassColors.textSecondary
                    )
                    Button(onClick = { showWeightSheet = true }) {
                        Text(stringResource(R.string.stats_add_first_checkin))
                    }
                }
            } else {
                WeightTrendCard(weightHistory = weightHistory)
            }

            Spacer(Modifier.height(100.dp))
        }

        if (showWeightSheet) {
            WeightCheckInSheet(
                initialWeightKg = userProfile.currentWeightKg,
                onDismiss = { showWeightSheet = false },
                onSave = { kg ->
                    viewModel.addWeightCheckIn(kg)
                    showWeightSheet = false
                },
            )
        }
    }
}

@Composable
private fun QuickKpiRow(
    totalCalories: Int,
    calorieGoal: Int,
    hydrationLiters: Float,
    hydrationGoal: Float,
    totalProtein: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        KpiCard(
            title = stringResource(R.string.stats_calories),
            value = "$totalCalories",
            subtitle = "/ $calorieGoal kcal",
            accentColor = GlassColors.accentGreen,
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            title = stringResource(R.string.stats_hydration),
            value = "${"%.1f".format(Locale.ENGLISH, hydrationLiters)}L",
            subtitle = "/ ${"%.1f".format(Locale.ENGLISH, hydrationGoal)}L",
            accentColor = GlassColors.accentBlue,
            modifier = Modifier.weight(1f)
        )
        KpiCard(
            title = stringResource(R.string.stats_protein),
            value = "${totalProtein}g",
            subtitle = stringResource(R.string.stats_today),
            accentColor = GlassColors.proteinColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .glassCard(16)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = GlassColors.textSecondary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            fontSize = 18.sp,
            color = GlassColors.textPrimary,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = accentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MacroProgressCard(
    totalProtein: Int,
    proteinGoal: Int,
    totalCarbs: Int,
    carbsGoal: Int,
    totalFat: Int,
    fatGoal: Int
) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(20)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_macro_progress),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = GlassColors.textPrimary
        )
        Spacer(Modifier.height(10.dp))
        MacroLine(label = stringResource(R.string.stats_protein), value = totalProtein, goal = proteinGoal, color = GlassColors.proteinColor)
        Spacer(Modifier.height(10.dp))
        MacroLine(label = stringResource(R.string.stats_carbs), value = totalCarbs, goal = carbsGoal, color = GlassColors.carbsColor)
        Spacer(Modifier.height(10.dp))
        MacroLine(label = stringResource(R.string.stats_fat), value = totalFat, goal = fatGoal, color = GlassColors.fatColor)
    }
}

@Composable
private fun MacroLine(label: String, value: Int, goal: Int, color: Color) {
    val progress = if (goal > 0) (value.toFloat() / goal).coerceIn(0f, 1f) else 0f

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = GlassColors.textSecondary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$value / $goal g",
                fontSize = 12.sp,
                color = GlassColors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(GlassColors.ringTrack, RoundedCornerShape(4.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun MealDistributionCard(meals: List<MealSplit>, totalCalories: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(20)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_meal_distribution),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = GlassColors.textPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (totalCalories > 0) "$totalCalories kcal consumed" else stringResource(R.string.stats_no_calories),
            fontSize = 12.sp,
            color = GlassColors.textSecondary
        )

        Spacer(Modifier.height(12.dp))

        meals.forEach { meal ->
            val share = if (totalCalories > 0) meal.calories.toFloat() / totalCalories else 0f
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(meal.accentColor, CircleShape)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(mealLabelRes(meal.name)),
                    fontSize = 12.sp,
                    color = GlassColors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${meal.calories} kcal",
                    fontSize = 12.sp,
                    color = GlassColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(GlassColors.ringTrack, RoundedCornerShape(4.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(share)
                        .height(6.dp)
                        .background(meal.accentColor, RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun WeightTrendCard(weightHistory: List<WeightCheckIn>) {
    val recent = remember(weightHistory) { weightHistory.takeLast(7) }
    val lastWeight = recent.lastOrNull()?.weightKg
    val firstWeight = recent.firstOrNull()?.weightKg
    val delta = if (lastWeight != null && firstWeight != null) lastWeight - firstWeight else null

    Column(
        Modifier
            .fillMaxWidth()
            .glassCard(20)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_weight_trend),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = GlassColors.textPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                lastWeight == null -> stringResource(R.string.stats_no_checkins_available)
                delta == null -> stringResource(R.string.stats_one_checkin)
                delta > 0f -> stringResource(R.string.stats_change_up, "%.1f".format(Locale.ENGLISH, delta))
                delta < 0f -> stringResource(R.string.stats_change_down, "%.1f".format(Locale.ENGLISH, delta))
                else -> stringResource(R.string.stats_change_stable)
            },
            fontSize = 12.sp,
            color = if ((delta ?: 0f) <= 0f) GlassColors.accentGreen else GlassColors.accentOrange,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))

        if (recent.size < 2) {
            Text(
                text = stringResource(R.string.stats_need_two_checkins),
                fontSize = 12.sp,
                color = GlassColors.textTertiary
            )
        } else {
            Sparkline(weights = recent.map { it.weightKg })
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${"%.1f".format(Locale.ENGLISH, firstWeight)} -> ${"%.1f".format(Locale.ENGLISH, lastWeight)} kg",
                fontSize = 11.sp,
                color = GlassColors.textSecondary
            )
        }
    }
}

@Composable
private fun Sparkline(weights: List<Float>) {
    val minValue = weights.minOrNull() ?: 0f
    val maxValue = weights.maxOrNull() ?: 0f
    val range = max(0.1f, maxValue - minValue)

    // Resolve theme colours outside the (non-composable) DrawScope lambda.
    val gridColor = GlassColors.cardBorder
    val lineStart = GlassColors.accentBlue
    val lineEnd = GlassColors.accentGreen
    val dotLast = GlassColors.accentGreen
    val dotOther = GlassColors.textSecondary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(GlassColors.backgroundDark.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(14.dp))
            .padding(PaddingValues(horizontal = 10.dp, vertical = 14.dp))
    ) {
        val stepX = if (weights.size > 1) size.width / (weights.size - 1) else 0f
        val points = weights.mapIndexed { index, value ->
            val x = index * stepX
            val normalized = (value - minValue) / range
            val y = size.height - (normalized * size.height)
            Offset(x, y)
        }

        // Grid lines make small weight changes easier to read.
        repeat(3) { idx ->
            val y = (idx + 1) * (size.height / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (point in points.drop(1)) {
                lineTo(point.x, point.y)
            }
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(lineStart, lineEnd)
            ),
            style = Stroke(width = 3.dp.toPx())
        )

        points.forEachIndexed { index, point ->
            val isLast = index == points.lastIndex
            drawCircle(
                color = if (isLast) dotLast else dotOther,
                radius = if (isLast) 4.dp.toPx() else 3.dp.toPx(),
                center = point
            )
        }

    }
}



