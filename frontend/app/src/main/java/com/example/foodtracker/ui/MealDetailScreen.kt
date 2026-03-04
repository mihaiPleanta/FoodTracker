package com.example.foodtracker.ui

import androidx.compose.animation.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foodtracker.ui.theme.FoodTrackerTheme
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.viewmodel.FoodViewModel

// ── Hardcoded food database (to be replaced with API later) ───────────────────

data class FoodItem(
    val name: String,
    val per100g: Int,       // kcal per 100g
    val protein100g: Float,
    val carbs100g: Float,
    val fat100g: Float,
    val emoji: String = "🍽"
)

val foodDatabase = listOf(
    FoodItem("Boiled Eggs",       155, 13f, 1.1f,  11f,  "🥚"),
    FoodItem("Oatmeal",           68,  2.4f, 12f,  1.4f, "🥣"),
    FoodItem("Banana",            89,  1.1f, 23f,  0.3f, "🍌"),
    FoodItem("Greek Yogurt",      59,  10f,  3.6f, 0.4f, "🫙"),
    FoodItem("Grilled Chicken",   165, 31f,  0f,   3.6f, "🍗"),
    FoodItem("Brown Rice",        130, 2.7f, 28f,  1f,   "🍚"),
    FoodItem("Salmon",            208, 20f,  0f,   13f,  "🐟"),
    FoodItem("Avocado",           160, 2f,   9f,   15f,  "🥑"),
    FoodItem("Whole Milk",        61,  3.2f, 4.8f, 3.3f, "🥛"),
    FoodItem("Almonds",           579, 21f,  22f,  50f,  "🌰"),
    FoodItem("Apple",             52,  0.3f, 14f,  0.2f, "🍎"),
    FoodItem("Sweet Potato",      86,  1.6f, 20f,  0.1f, "🍠"),
    FoodItem("Broccoli",          34,  2.8f, 7f,   0.4f, "🥦"),
    FoodItem("Cheddar Cheese",    402, 25f,  1.3f, 33f,  "🧀"),
    FoodItem("White Bread",       265, 9f,   49f,  3.2f, "🍞"),
    FoodItem("Olive Oil",         884, 0f,   0f,   100f, "🫒"),
    FoodItem("Tuna (canned)",     116, 26f,  0f,   1f,   "🥫"),
    FoodItem("Cottage Cheese",    98,  11f,  3.4f, 4.3f, "🫙"),
    FoodItem("Blueberries",       57,  0.7f, 14f,  0.3f, "🫐"),
    FoodItem("Protein Bar",       370, 30f,  35f,  10f,  "🍫"),
)

data class LoggedFood(
    val food: FoodItem,
    val grams: Int
) {
    val calories: Int get() = (food.per100g * grams / 100f).toInt()
    val protein:  Float get() = food.protein100g * grams / 100f
    val carbs:    Float get() = food.carbs100g * grams / 100f
    val fat:      Float get() = food.fat100g * grams / 100f
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun MealDetailScreen(
    mealName: String,
    mealIcon: String,
    accentColor: Color,
    navController: NavController,
    viewModel: FoodViewModel = viewModel()
) {
    // Read from shared ViewModel — reactive to changes
    val loggedFoods by viewModel.getFoodsFlow(mealName).collectAsState()

    var searchQuery  by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    val filteredFoods = remember(searchQuery) {
        if (searchQuery.isBlank()) foodDatabase
        else foodDatabase.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val totalCalories = loggedFoods.sumOf { it.calories }
    val totalProtein  = loggedFoods.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs    = loggedFoods.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat      = loggedFoods.sumOf { it.fat.toDouble() }.toFloat()

    Box(
        Modifier
            .fillMaxSize()
            .background(GlassColors.backgroundDark)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            MealDetailHeader(
                mealName    = mealName,
                mealIcon    = mealIcon,
                accentColor = accentColor,
                onBack      = {
                    navController.popBackStack()
                    viewModel.openMealSelector()   // reopen the pop-up after going back
                }
            )

            SearchBar(
                query          = searchQuery,
                onQueryChange  = { searchQuery = it; if (!searchActive) searchActive = true },
                onClear        = { searchQuery = ""; searchActive = false; focusManager.clearFocus() },
                accentColor    = accentColor,
                focusRequester = focusRequester,
                modifier       = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)
            )

            Box(Modifier.weight(1f)) {
                if (searchActive && searchQuery.isNotBlank()) {
                    SearchResultsList(
                        foods       = filteredFoods,
                        accentColor = accentColor,
                        onAdd       = { food ->
                            viewModel.addFoodToMeal(mealName, LoggedFood(food, 100))
                            searchQuery  = ""
                            searchActive = false
                            focusManager.clearFocus()
                        }
                    )
                } else {
                    LoggedFoodsList(
                        loggedFoods = loggedFoods,
                        accentColor = accentColor,
                        onRemove    = { index -> viewModel.removeFoodFromMeal(mealName, index) }
                    )
                }
            }

            MacroSummaryFooter(
                totalCalories = totalCalories,
                totalProtein  = totalProtein,
                totalCarbs    = totalCarbs,
                totalFat      = totalFat,
                accentColor   = accentColor
            )
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
fun MealDetailHeader(
    mealName: String,
    mealIcon: String,
    accentColor: Color,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GlassColors.cardBackground)
                .border(1.dp, GlassColors.cardBorder, CircleShape)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint = GlassColors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        // Meal icon circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.13f))
                .border(1.dp, accentColor.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(mealIcon, fontSize = 22.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = mealName,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GlassColors.textPrimary
            )
            Text(
                text = "Tap + to add food",
                fontSize = 12.sp,
                color = GlassColors.textTertiary
            )
        }
    }
}

// ── Search bar ────────────────────────────────────────────────────────────────

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    accentColor: Color,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val isFocused = query.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassColors.cardBackground)
            .border(
                width = 1.dp,
                color = if (isFocused) accentColor.copy(alpha = 0.50f) else GlassColors.cardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (isFocused) accentColor else GlassColors.textTertiary,
            modifier = Modifier.size(18.dp)
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                color     = GlassColors.textPrimary,
                fontSize  = 15.sp,
                fontWeight = FontWeight.Normal
            ),
            singleLine    = true,
            cursorBrush   = SolidColor(accentColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { /* handled by filter */ }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Search food…",
                        color    = GlassColors.textTertiary,
                        fontSize = 15.sp
                    )
                }
                inner()
            }
        )

        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear",
                tint = GlassColors.textTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onClear() }
            )
        }
    }
}

// ── Logged foods list ─────────────────────────────────────────────────────────

@Composable
fun LoggedFoodsList(
    loggedFoods: List<LoggedFood>,
    accentColor: Color,
    onRemove: (Int) -> Unit
) {
    if (loggedFoods.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🍽", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "No food logged yet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassColors.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Search above to add food",
                    fontSize = 13.sp,
                    color = GlassColors.textTertiary
                )
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            items = loggedFoods,
            key   = { index, item -> "${item.food.name}_$index" }
        ) { index, logged ->
            AnimatedVisibility(
                visible = true,
                enter   = fadeIn(tween(220)) + slideInVertically(tween(240)) { 20 }
            ) {
                LoggedFoodRow(
                    logged      = logged,
                    accentColor = accentColor,
                    onRemove    = { onRemove(index) }
                )
            }
        }
    }
}

@Composable
fun LoggedFoodRow(
    logged: LoggedFood,
    accentColor: Color,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassColors.cardBackground)
            .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Food emoji
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Text(logged.food.emoji, fontSize = 18.sp)
        }

        Spacer(Modifier.width(12.dp))

        // Name + macros
        Column(Modifier.weight(1f)) {
            Text(
                text = logged.food.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = GlassColors.textPrimary
            )
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniMacroTag("P ${logged.protein.toInt()}g", GlassColors.proteinColor)
                MiniMacroTag("C ${logged.carbs.toInt()}g",  GlassColors.carbsColor)
                MiniMacroTag("F ${logged.fat.toInt()}g",    GlassColors.fatColor)
            }
        }

        Spacer(Modifier.width(10.dp))

        // Grams + calories column
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${logged.grams}g",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GlassColors.textSecondary
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${logged.calories} kcal",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Remove button
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF4444).copy(alpha = 0.12f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun MiniMacroTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

// ── Search results list ───────────────────────────────────────────────────────

@Composable
fun SearchResultsList(
    foods: List<FoodItem>,
    accentColor: Color,
    onAdd: (FoodItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(foods, key = { _, item -> item.name }) { _, food ->
            SearchResultRow(food = food, onAdd = { onAdd(food) })
        }
    }
}

@Composable
fun SearchResultRow(food: FoodItem, onAdd: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.97f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "rowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(GlassColors.cardBackground)
            .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emoji
        Text(food.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))

        // Name + per 100g info
        Column(Modifier.weight(1f)) {
            Text(
                text = food.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = GlassColors.textPrimary
            )
            Text(
                text = "per 100g  ·  ${food.per100g} kcal  ·  P${food.protein100g.toInt()} C${food.carbs100g.toInt()} F${food.fat100g.toInt()}",
                fontSize = 11.sp,
                color = GlassColors.textTertiary
            )
        }

        Spacer(Modifier.width(10.dp))

        // Add button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(GlassColors.accentGreen, GlassColors.accentGreenDim)
                    )
                )
                .clickable(interactionSource = interactionSource, indication = null) { onAdd() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add",
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Macro summary footer ──────────────────────────────────────────────────────

@Composable
fun MacroSummaryFooter(
    totalCalories: Int,
    totalProtein: Float,
    totalCarbs: Float,
    totalFat: Float,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(GlassColors.cardBackground)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(GlassColors.cardBorder, Color.Transparent)
                ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .navigationBarsPadding()
    ) {
        // Total calories row
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(
                "Total",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = GlassColors.textSecondary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$totalCalories",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlassColors.textPrimary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "kcal",
                    fontSize = 13.sp,
                    color = GlassColors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Macro bars row
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.spacedBy(10.dp)
        ) {
            FooterMacroBar(
                label   = "Protein",
                value   = totalProtein,
                goal    = 150f,
                color   = GlassColors.proteinColor,
                unit    = "g",
                modifier = Modifier.weight(1f)
            )
            FooterMacroBar(
                label   = "Carbs",
                value   = totalCarbs,
                goal    = 250f,
                color   = GlassColors.carbsColor,
                unit    = "g",
                modifier = Modifier.weight(1f)
            )
            FooterMacroBar(
                label   = "Fat",
                value   = totalFat,
                goal    = 65f,
                color   = GlassColors.fatColor,
                unit    = "g",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Save / Done button
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            if (isPressed) 0.96f else 1f,
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
            label = "doneScale"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(GlassColors.accentGreen, GlassColors.accentGreenDim)
                    )
                )
                .clickable(interactionSource = interactionSource, indication = null) { },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Save Meal",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

@Composable
fun FooterMacroBar(
    label: String,
    value: Float,
    goal: Float,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier
) {
    val progress = (value / goal).coerceIn(0f, 1f)
    val animProg by animateFloatAsState(progress, tween(800), label = "${label}fp")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${value.toInt()}$unit",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = GlassColors.textPrimary
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color.copy(alpha = 0.15f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animProg)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = GlassColors.textTertiary,
            textAlign = TextAlign.Center
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D, widthDp = 390, heightDp = 844)
@Composable
fun MealDetailPreview() {
    FoodTrackerTheme {
        Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
            Column(Modifier.fillMaxSize()) {
                MealDetailHeader("Breakfast", "🌅", Color(0xFFFFD600)) {}
                SearchBar("", {}, {}, Color(0xFFFFD600), remember { FocusRequester() },
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp))
                Box(Modifier.weight(1f)) {
                    LoggedFoodsList(
                        loggedFoods = listOf(
                            LoggedFood(foodDatabase[0], 120),
                            LoggedFood(foodDatabase[1], 200),
                            LoggedFood(foodDatabase[2], 118),
                            LoggedFood(foodDatabase[3], 150),
                        ),
                        accentColor = Color(0xFFFFD600),
                        onRemove = {}
                    )
                }
                MacroSummaryFooter(535, 55f, 30f, 38f, Color(0xFFFFD600))
            }
        }
    }
}












