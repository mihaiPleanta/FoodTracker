package com.example.foodtracker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
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
import com.example.foodtracker.ui.theme.glassCard
import com.example.foodtracker.viewmodel.FoodViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── Data models ───────────────────────────────────────────────────────────────

data class CalendarDay(
    val date: Date,
    val dayOfMonth: Int,
    val dayOfWeekShort: String,
    val isToday: Boolean
)

data class NutritionGoals(
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 150,
    val carbsGoal: Int = 250,
    val fatGoal: Int = 65
)

data class MealEntry(
    val name: String,
    val grams: Int,
    val calories: Int
)

data class MealSection(
    val name: String,
    val icon: String,
    val recommendedRange: String,
    val consumedCalories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val entries: List<MealEntry>,
    val accentColor: Color
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(navController: NavController, viewModel: FoodViewModel) {
    val calendarDays = remember { generateCalendarDays() }
    val todayIndex   = calendarDays.indexOfFirst { it.isToday }
    var selectedDate by remember { mutableStateOf(calendarDays[todayIndex].date) }

    val headerTitle = remember(selectedDate) {
        val cal = Calendar.getInstance().apply { time = selectedDate }
        if (isSameDay(cal, Calendar.getInstance())) "Today"
        else SimpleDateFormat("EEEE", Locale.ENGLISH).format(selectedDate)
    }
    val headerSubtitle = remember(selectedDate) {
        SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).format(selectedDate)
    }

    val goals = remember { NutritionGoals() }

    // ── Live data from ViewModel ──────────────────────────────────────────────
    val breakfastFoods by viewModel.breakfastFoods.collectAsState()
    val lunchFoods     by viewModel.lunchFoods.collectAsState()
    val dinnerFoods    by viewModel.dinnerFoods.collectAsState()
    val snacksFoods    by viewModel.snacksFoods.collectAsState()

    val consumedCalories = remember(breakfastFoods, lunchFoods, dinnerFoods, snacksFoods) {
        (breakfastFoods + lunchFoods + dinnerFoods + snacksFoods).sumOf { it.calories }
    }
    val consumedProtein = remember(breakfastFoods, lunchFoods, dinnerFoods, snacksFoods) {
        (breakfastFoods + lunchFoods + dinnerFoods + snacksFoods).sumOf { it.protein.toDouble() }.toInt()
    }
    val consumedCarbs = remember(breakfastFoods, lunchFoods, dinnerFoods, snacksFoods) {
        (breakfastFoods + lunchFoods + dinnerFoods + snacksFoods).sumOf { it.carbs.toDouble() }.toInt()
    }
    val consumedFat = remember(breakfastFoods, lunchFoods, dinnerFoods, snacksFoods) {
        (breakfastFoods + lunchFoods + dinnerFoods + snacksFoods).sumOf { it.fat.toDouble() }.toInt()
    }

    // Meal sections built from live ViewModel data
    val mealConfigs = remember {
        listOf(
            Triple("Breakfast", "🌅", Color(0xFFFFD600)),
            Triple("Lunch",     "☀️", Color(0xFF00E676)),
            Triple("Dinner",    "🌙", Color(0xFF448AFF)),
            Triple("Snacks",    "🍎", Color(0xFFFF6D00))
        )
    }
    val mealRanges = remember {
        mapOf("Breakfast" to "600–650", "Lunch" to "700–800",
              "Dinner" to "500–600", "Snacks" to "150–200")
    }

    val meals = remember(breakfastFoods, lunchFoods, dinnerFoods, snacksFoods) {
        val foodsMap = mapOf(
            "Breakfast" to breakfastFoods,
            "Lunch"     to lunchFoods,
            "Dinner"    to dinnerFoods,
            "Snacks"    to snacksFoods
        )
        mealConfigs.map { (name, icon, color) ->
            val foods = foodsMap[name] ?: emptyList()
            MealSection(
                name             = name,
                icon             = icon,
                recommendedRange = mealRanges[name] ?: "",
                consumedCalories = foods.sumOf { it.calories },
                protein          = foods.sumOf { it.protein.toDouble() }.toInt(),
                carbs            = foods.sumOf { it.carbs.toDouble() }.toInt(),
                fat              = foods.sumOf { it.fat.toDouble() }.toInt(),
                entries          = foods.map { MealEntry(it.food.name, it.grams, it.calories) },
                accentColor      = color
            )
        }
    }

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(20.dp))

            HomeHeader(title = headerTitle, subtitle = headerSubtitle,
                modifier = Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(20.dp))

            WeekCalendar(days = calendarDays, selectedDate = selectedDate,
                onDaySelected = { selectedDate = it },
                modifier = Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(24.dp))

            CalorieSummaryCard(
                consumedCalories = consumedCalories, calorieGoal = goals.calorieGoal,
                consumedProtein = consumedProtein,   proteinGoal = goals.proteinGoal,
                consumedCarbs = consumedCarbs,       carbsGoal = goals.carbsGoal,
                consumedFat = consumedFat,           fatGoal = goals.fatGoal,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(28.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Text("Meals", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = GlassColors.textPrimary)
                Text("See all", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = GlassColors.accentGreen)
            }
            Spacer(Modifier.height(12.dp))

            meals.forEach { meal ->
                MealCard(meal = meal, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
fun HomeHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text(subtitle, fontSize = 13.sp, color = GlassColors.textSecondary)
            Spacer(Modifier.height(2.dp))
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                color = GlassColors.textPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(GlassColors.cardBackground)
                    .border(1.dp, GlassColors.cardBorder, CircleShape),
                Alignment.Center
            ) {
                Text("🔔", fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Brush.linearGradient(
                        listOf(GlassColors.accentGreen.copy(.8f), GlassColors.accentBlue.copy(.8f))
                    )),
                Alignment.Center
            ) {
                Text("M", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Week calendar ─────────────────────────────────────────────────────────────

@Composable
fun WeekCalendar(
    days: List<CalendarDay>,
    selectedDate: Date,
    onDaySelected: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    val state      = rememberLazyListState()
    val todayIndex = days.indexOfFirst { it.isToday }
    LaunchedEffect(Unit) {
        if (todayIndex >= 0) state.scrollToItem((todayIndex - 2).coerceAtLeast(0))
    }
    LazyRow(state = state, modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days) { day ->
            DayItem(day, isSameDay(day.date, selectedDate)) { onDaySelected(day.date) }
        }
    }
}

@Composable
fun DayItem(day: CalendarDay, isSelected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        when { isSelected -> GlassColors.accentGreen
            day.isToday -> GlassColors.accentGreen.copy(alpha = .10f)
            else -> GlassColors.cardBackground },
        tween(250), "bg")
    val numColor by animateColorAsState(
        if (isSelected) Color.Black else GlassColors.textPrimary, tween(250), "num")
    val lblColor by animateColorAsState(
        when { isSelected -> Color.Black.copy(.7f)
            day.isToday -> GlassColors.accentGreen
            else -> GlassColors.textTertiary }, tween(250), "lbl")

    Column(
        Modifier.width(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp,
                if (isSelected) Color.Transparent else GlassColors.cardBorder,
                RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(day.dayOfWeekShort.uppercase(), fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold, color = lblColor, letterSpacing = .8.sp)
        Spacer(Modifier.height(6.dp))
        Text(day.dayOfMonth.toString(), fontSize = 19.sp,
            fontWeight = FontWeight.Bold, color = numColor)
        if (day.isToday && !isSelected) {
            Spacer(Modifier.height(5.dp))
            Box(Modifier.size(5.dp).clip(CircleShape).background(GlassColors.accentGreen))
        }
    }
}

// ── Calorie summary card ──────────────────────────────────────────────────────

@Composable
fun CalorieSummaryCard(
    consumedCalories: Int, calorieGoal: Int,
    consumedProtein: Int,  proteinGoal: Int,
    consumedCarbs: Int,    carbsGoal: Int,
    consumedFat: Int,      fatGoal: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (calorieGoal > 0) (consumedCalories.toFloat() / calorieGoal).coerceIn(0f,1f) else 0f
    val animProg by animateFloatAsState(progress, tween(1200, easing = FastOutSlowInEasing), label = "cal")

    Box(modifier.fillMaxWidth().glassCard(24).padding(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Box(Modifier.size(190.dp).align(Alignment.CenterHorizontally),
                Alignment.Center) {
                CalorieRing(animProg, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$consumedCalories", fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold, color = GlassColors.textPrimary)
                    Text("/ $calorieGoal kcal", fontSize = 13.sp,
                        color = GlassColors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    val remaining = (calorieGoal - consumedCalories).coerceAtLeast(0)
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(GlassColors.accentGreen.copy(.12f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text("$remaining kcal left", fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, color = GlassColors.accentGreen)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                MacroPill("Protein", consumedProtein, proteinGoal, "g",
                    GlassColors.proteinColor, Modifier.weight(1f))
                MacroPill("Carbs", consumedCarbs, carbsGoal, "g",
                    GlassColors.carbsColor,  Modifier.weight(1f))
                MacroPill("Fat",   consumedFat,   fatGoal,   "g",
                    GlassColors.fatColor,    Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MacroPill(
    label: String, consumed: Int, goal: Int, unit: String,
    color: Color, modifier: Modifier = Modifier
) {
    val prog    = if (goal > 0) (consumed.toFloat() / goal).coerceIn(0f,1f) else 0f
    val animProg by animateFloatAsState(prog, tween(1200, easing = FastOutSlowInEasing), label = label)

    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(.07f))
            .border(1.dp, color.copy(.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(8.dp))
        Text("$consumed$unit", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
            color = GlassColors.textPrimary)
        Text(label, fontSize = 11.sp, color = GlassColors.textSecondary,
            fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp)
            .clip(RoundedCornerShape(2.dp)).background(color.copy(.15f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(animProg)
                .clip(RoundedCornerShape(2.dp)).background(color))
        }
        Spacer(Modifier.height(4.dp))
        Text("/ $goal$unit", fontSize = 10.sp, color = GlassColors.textTertiary)
    }
}

// ── Calorie ring ──────────────────────────────────────────────────────────────

@Composable
fun CalorieRing(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke  = 16.dp.toPx()
        val pad     = stroke / 2f
        val rect    = Size(size.width - stroke, size.height - stroke)
        val tl      = Offset(pad, pad)

        drawArc(GlassColors.ringTrack, 0f, 360f, false, tl, rect,
            style = Stroke(stroke, cap = StrokeCap.Round))

        if (progress > 0f) {
            drawArc(
                Brush.sweepGradient(
                    0f to GlassColors.accentGreen.copy(.3f),
                    .4f to GlassColors.accentBlue.copy(.3f),
                    .7f to GlassColors.accentOrange.copy(.3f),
                    1f  to GlassColors.accentGreen.copy(.3f)
                ), -90f, progress*360f, false, tl, rect,
                style = Stroke(stroke + 8.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                Brush.sweepGradient(
                    0f to GlassColors.accentGreen,
                    .4f to GlassColors.accentBlue,
                    .7f to GlassColors.accentOrange,
                    1f  to GlassColors.accentGreen
                ), -90f, progress*360f, false, tl, rect,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

// ── Meal card ─────────────────────────────────────────────────────────────────

@Composable
fun MealCard(meal: MealSection, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val arrowAngle by animateFloatAsState(
        if (expanded) 180f else 0f, tween(300), label = "arrow")
    val isEmpty = meal.entries.isEmpty()

    Box(modifier.fillMaxWidth().accentCard(meal.accentColor, 20).padding(16.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(meal.accentColor.copy(.13f))
                        .border(1.dp, meal.accentColor.copy(.25f), CircleShape),
                    Alignment.Center
                ) { Text(meal.icon, fontSize = 24.sp) }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(meal.name, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = GlassColors.textPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("Recommended: ${meal.recommendedRange} kcal",
                        fontSize = 11.sp, color = GlassColors.textTertiary)
                    Spacer(Modifier.height(6.dp))

                    if (isEmpty) {
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(meal.accentColor.copy(.10f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Tap + to add food", fontSize = 11.sp,
                                color = meal.accentColor, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${meal.consumedCalories} kcal", fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, color = meal.accentColor)
                            MacroBadge("P", meal.protein, GlassColors.proteinColor)
                            MacroBadge("C", meal.carbs,   GlassColors.carbsColor)
                            MacroBadge("F", meal.fat,     GlassColors.fatColor)
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // expand / add button
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .background(meal.accentColor.copy(.12f))
                        .border(1.dp, meal.accentColor.copy(.25f), CircleShape)
                        .then(if (!isEmpty) Modifier.clickable { expanded = !expanded }
                              else Modifier),
                    Alignment.Center
                ) {
                    if (!isEmpty) {
                        Text(
                            text = "⌄",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = meal.accentColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.rotate(arrowAngle)
                        )
                    } else {
                        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            color = meal.accentColor, textAlign = TextAlign.Center)
                    }
                }
            }

            AnimatedVisibility(
                expanded,
                enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                exit  = shrinkVertically(tween(300)) + fadeOut(tween(300))
            ) {
                Column(Modifier.padding(top = 14.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp)
                        .background(meal.accentColor.copy(.12f)))
                    Spacer(Modifier.height(10.dp))
                    meal.entries.forEachIndexed { i, entry ->
                        MealEntryRow(entry, meal.accentColor)
                        if (i < meal.entries.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MacroBadge(label: String, value: Int, color: Color) {
    Spacer(Modifier.width(8.dp))
    Text("· $label $value", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
}

@Composable
fun MealEntryRow(entry: MealEntry, accentColor: Color) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GlassColors.backgroundDark.copy(.6f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accentColor))
            Spacer(Modifier.width(10.dp))
            Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = GlassColors.textPrimary)
        }
        Text("${entry.grams}g  ·  ${entry.calories} kcal",
            fontSize = 12.sp, color = GlassColors.textSecondary)
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

fun generateCalendarDays(): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()
    val cal  = Calendar.getInstance()
    val today = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -3)
    val fmt = SimpleDateFormat("EEE", Locale.ENGLISH)
    repeat(14) {
        val date = cal.time
        days.add(CalendarDay(date, cal.get(Calendar.DAY_OF_MONTH),
            fmt.format(date), isSameDay(cal, today)))
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return days
}

fun isSameDay(c1: Calendar, c2: Calendar) =
    c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

fun isSameDay(d1: Date, d2: Date): Boolean {
    val c1 = Calendar.getInstance().apply { time = d1 }
    val c2 = Calendar.getInstance().apply { time = d2 }
    return isSameDay(c1, c2)
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 390, heightDp = 844)
@Composable
fun HomeScreenPreview() {
    FoodTrackerTheme {
        Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
            Column(
                Modifier.fillMaxSize().padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(12.dp))
                HomeHeader("Today", "4 March 2026")
                Spacer(Modifier.height(20.dp))
                CalorieSummaryCard(1247,2000,82,150,156,250,41,65)
                Spacer(Modifier.height(24.dp))
                listOf(
                    MealSection("Breakfast","🌅","600–650",535,55,30,38,
                        listOf(MealEntry("Boiled Eggs",120,180),MealEntry("Oatmeal",200,150)),
                        Color(0xFFFFD600)),
                    MealSection("Lunch","☀️","700–800",712,60,80,22,
                        listOf(MealEntry("Grilled Chicken",200,330),MealEntry("Rice",150,195)),
                        Color(0xFF00E676)),
                    MealSection("Dinner","🌙","500–600",0,0,0,0,emptyList(),Color(0xFF448AFF)),
                    MealSection("Snacks","🍎","150–200",0,0,0,0,emptyList(),Color(0xFFFF6D00))
                ).forEach { meal ->
                    MealCard(meal); Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
