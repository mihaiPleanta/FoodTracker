package com.example.foodtracker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.foodtracker.ui.theme.FoodTrackerTheme
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.ui.theme.accentCard

// ── Data ──────────────────────────────────────────────────────────────────────

data class MealTypeInfo(
    val name: String,
    val icon: String,
    val timeRange: String,
    val recommendedRange: String,
    val accentColor: Color,
    val consumedCalories: Int,
    val calorieGoal: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val entries: List<MealEntry>
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun AddMealScreen(navController: NavController) {

    val meals = remember {
        listOf(
            MealTypeInfo(
                name = "Breakfast",
                icon = "🌅",
                timeRange = "7:00 – 10:00",
                recommendedRange = "600–650",
                accentColor = Color(0xFFFFD600),
                consumedCalories = 535,
                calorieGoal = 625,
                protein = 55,
                carbs = 30,
                fat = 38,
                entries = listOf(
                    MealEntry("Boiled Eggs", 120, 180),
                    MealEntry("Oatmeal", 200, 150),
                    MealEntry("Banana", 118, 105),
                    MealEntry("Greek Yogurt", 150, 100)
                )
            ),
            MealTypeInfo(
                name = "Lunch",
                icon = "☀️",
                timeRange = "12:00 – 14:00",
                recommendedRange = "700–800",
                accentColor = Color(0xFF00E676),
                consumedCalories = 712,
                calorieGoal = 750,
                protein = 60,
                carbs = 80,
                fat = 22,
                entries = listOf(
                    MealEntry("Grilled Chicken", 200, 330),
                    MealEntry("Brown Rice", 150, 195),
                    MealEntry("Salad", 100, 45),
                    MealEntry("Olive Oil", 15, 132)
                )
            ),
            MealTypeInfo(
                name = "Dinner",
                icon = "🌙",
                timeRange = "18:00 – 21:00",
                recommendedRange = "500–600",
                accentColor = Color(0xFF448AFF),
                consumedCalories = 0,
                calorieGoal = 550,
                protein = 0,
                carbs = 0,
                fat = 0,
                entries = emptyList()
            ),
            MealTypeInfo(
                name = "Snacks",
                icon = "🍎",
                timeRange = "Anytime",
                recommendedRange = "150–200",
                accentColor = Color(0xFFFF6D00),
                consumedCalories = 0,
                calorieGoal = 175,
                protein = 0,
                carbs = 0,
                fat = 0,
                entries = emptyList()
            )
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(GlassColors.backgroundDark)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Header ────────────────────────────────────────────────────────
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "Add Food",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlassColors.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choose a meal to log food into",
                    fontSize = 14.sp,
                    color = GlassColors.textSecondary
                )
            }

            Spacer(Modifier.height(28.dp))

            // ── Daily summary strip ───────────────────────────────────────────
            DailySummaryStrip(meals = meals, modifier = Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(28.dp))

            // ── Meals label ───────────────────────────────────────────────────
            Text(
                text = "Select Meal",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = GlassColors.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(14.dp))

            // ── Meal cards ────────────────────────────────────────────────────
            meals.forEach { meal ->
                AddMealCard(
                    meal = meal,
                    onAddClick = {
                        // TODO: navigate to food search screen
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

// ── Daily summary strip ───────────────────────────────────────────────────────

@Composable
fun DailySummaryStrip(meals: List<MealTypeInfo>, modifier: Modifier = Modifier) {
    val totalConsumed = meals.sumOf { it.consumedCalories }
    val totalGoal     = meals.sumOf { it.calorieGoal }
    val totalProtein  = meals.sumOf { it.protein }
    val totalCarbs    = meals.sumOf { it.carbs }
    val totalFat      = meals.sumOf { it.fat }
    val progress      = if (totalGoal > 0) (totalConsumed.toFloat() / totalGoal).coerceIn(0f, 1f) else 0f
    val animProg by animateFloatAsState(progress, tween(1000), label = "stripProg")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassColors.cardBackground)
            .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mini ring
        Box(Modifier.size(64.dp), Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val pad    = stroke / 2f
                val rect   = Size(size.width - stroke, size.height - stroke)
                val tl     = Offset(pad, pad)
                drawArc(GlassColors.ringTrack, 0f, 360f, false, tl, rect,
                    style = Stroke(stroke, cap = StrokeCap.Round))
                if (animProg > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to GlassColors.accentGreen,
                            .5f to GlassColors.accentBlue,
                            1f  to GlassColors.accentGreen
                        ),
                        startAngle = -90f, sweepAngle = animProg * 360f,
                        useCenter = false, topLeft = tl, size = rect,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$totalConsumed",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlassColors.textPrimary
                )
                Text(
                    "kcal",
                    fontSize = 9.sp,
                    color = GlassColors.textTertiary
                )
            }
        }

        // Macro columns
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "$totalConsumed / $totalGoal kcal",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GlassColors.textPrimary
            )
            // Progress bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(GlassColors.ringTrack)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animProg)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(GlassColors.accentGreen, GlassColors.accentBlue)
                            )
                        )
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroChip("P", totalProtein, GlassColors.proteinColor)
                MacroChip("C", totalCarbs,   GlassColors.carbsColor)
                MacroChip("F", totalFat,     GlassColors.fatColor)
            }
        }
    }
}

@Composable
fun MacroChip(label: String, value: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            "$label $value g",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = GlassColors.textSecondary
        )
    }
}

// ── Add meal card ─────────────────────────────────────────────────────────────

@Composable
fun AddMealCard(
    meal: MealTypeInfo,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isEmpty  = meal.entries.isEmpty()
    val progress = if (meal.calorieGoal > 0)
        (meal.consumedCalories.toFloat() / meal.calorieGoal).coerceIn(0f, 1f) else 0f
    val animProg by animateFloatAsState(progress, tween(1000), label = "mealProg${meal.name}")

    Box(
        modifier
            .fillMaxWidth()
            .accentCard(meal.accentColor, 20)
            .padding(18.dp)
    ) {
        Column {
            // ── Top row ───────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(meal.accentColor.copy(.13f))
                        .border(1.dp, meal.accentColor.copy(.30f), CircleShape),
                    Alignment.Center
                ) {
                    Text(meal.icon, fontSize = 26.sp)
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        meal.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlassColors.textPrimary
                    )
                    Text(
                        meal.timeRange,
                        fontSize = 11.sp,
                        color = GlassColors.textTertiary
                    )
                    Spacer(Modifier.height(6.dp))

                    if (isEmpty) {
                        Text(
                            "Recommended: ${meal.recommendedRange} kcal",
                            fontSize = 12.sp,
                            color = GlassColors.textTertiary
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${meal.consumedCalories} kcal",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = meal.accentColor
                            )
                            Text("  ·  ", fontSize = 11.sp, color = GlassColors.textTertiary)
                            Text("P ${meal.protein}", fontSize = 11.sp,
                                color = GlassColors.proteinColor, fontWeight = FontWeight.Medium)
                            Text("  C ${meal.carbs}", fontSize = 11.sp,
                                color = GlassColors.carbsColor, fontWeight = FontWeight.Medium)
                            Text("  F ${meal.fat}", fontSize = 11.sp,
                                color = GlassColors.fatColor, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Add button (FAB style)
                val btnScale by animateFloatAsState(1f, tween(150), label = "addBtn")
                Box(
                    Modifier
                        .size(40.dp)
                        .scale(btnScale)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(GlassColors.accentGreen, GlassColors.accentGreenDim)
                            )
                        )
                        .clickable { onAddClick() },
                    Alignment.Center
                ) {
                    Text(
                        "+",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Progress bar ──────────────────────────────────────────────────
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween
                ) {
                    Text(
                        if (isEmpty) "No food logged yet"
                        else "${meal.consumedCalories} / ${meal.calorieGoal} kcal",
                        fontSize = 11.sp,
                        color = GlassColors.textTertiary
                    )
                    Text(
                        "${(animProg * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = meal.accentColor
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(meal.accentColor.copy(.12f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animProg)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(meal.accentColor.copy(.7f), meal.accentColor)
                                )
                            )
                    )
                }
            }

            // ── Expandable entries ────────────────────────────────────────────
            if (!isEmpty) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    Arrangement.Center,
                    Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "Hide items ▲" else "Show ${meal.entries.size} items ▼",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = meal.accentColor.copy(.8f)
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit  = shrinkVertically(tween(300)) + fadeOut(tween(300))
                ) {
                    Column(Modifier.padding(top = 8.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(1.dp)
                                .background(meal.accentColor.copy(.12f))
                        )
                        Spacer(Modifier.height(10.dp))
                        meal.entries.forEachIndexed { i, entry ->
                            AddMealEntryRow(entry = entry, accentColor = meal.accentColor)
                            if (i < meal.entries.lastIndex) Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddMealEntryRow(entry: MealEntry, accentColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GlassColors.backgroundDark.copy(.5f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accentColor))
            Spacer(Modifier.width(10.dp))
            Text(entry.name, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = GlassColors.textPrimary)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${entry.grams}g", fontSize = 12.sp, color = GlassColors.textTertiary)
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("${entry.calories} kcal", fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, color = accentColor)
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 390, heightDp = 844)
@Composable
fun AddMealScreenPreview() {
    FoodTrackerTheme {
        // Simplified preview without NavController
        Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(12.dp))
                Text("Add Food", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    color = GlassColors.textPrimary)
                Text("Choose a meal to log food into", fontSize = 14.sp,
                    color = GlassColors.textSecondary)
                Spacer(Modifier.height(20.dp))
                listOf(
                    MealTypeInfo("Breakfast","🌅","7:00 – 10:00","600–650",
                        Color(0xFFFFD600),535,625,55,30,38,
                        listOf(MealEntry("Boiled Eggs",120,180),MealEntry("Oatmeal",200,150))),
                    MealTypeInfo("Lunch","☀️","12:00 – 14:00","700–800",
                        Color(0xFF00E676),712,750,60,80,22,
                        listOf(MealEntry("Grilled Chicken",200,330))),
                    MealTypeInfo("Dinner","🌙","18:00 – 21:00","500–600",
                        Color(0xFF448AFF),0,550,0,0,0, emptyList()),
                    MealTypeInfo("Snacks","🍎","Anytime","150–200",
                        Color(0xFFFF6D00),0,175,0,0,0, emptyList())
                ).forEach { meal ->
                    AddMealCard(meal = meal, onAddClick = {})
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}



