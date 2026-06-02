package com.example.foodtracker.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.foodtracker.R
import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.util.CategoryEmojiMapper
import com.example.foodtracker.util.scaleNutrition
import com.example.foodtracker.viewmodel.FoodViewModel
import kotlinx.coroutines.launch

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun MealDetailScreen(
    mealName: String,
    mealIcon: String,
    accentColor: Color,
    navController: NavController,
    viewModel: FoodViewModel
) {
    val loggedFoods by viewModel.getFoodsFlow(mealName).collectAsState()
    val searchState by viewModel.searchState.collectAsState()

    val goBackToHome = {
        navController.navigate("home") {
            popUpTo("home") { inclusive = false }
            launchSingleTop = true
        }
    }
    BackHandler(onBack = goBackToHome)

    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var sheetTarget by remember { mutableStateOf<FoodItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(searchQuery) {
        viewModel.searchFoods(searchQuery)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearSearch() }
    }

    val totalCalories = loggedFoods.sumOf { it.calories }
    val totalProtein  = loggedFoods.sumOf { it.protein.toDouble() }.toFloat()
    val totalCarbs    = loggedFoods.sumOf { it.carbs.toDouble() }.toFloat()
    val totalFat      = loggedFoods.sumOf { it.fat.toDouble() }.toFloat()

    Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            MealDetailHeader(
                mealName    = mealName,
                mealIcon    = mealIcon,
                accentColor = accentColor,
                onBack      = goBackToHome,
            )

            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchBar(
                    query          = searchQuery,
                    onQueryChange  = { searchQuery = it; if (!searchActive) searchActive = true },
                    onClear        = { searchQuery = ""; searchActive = false; focusManager.clearFocus() },
                    accentColor    = accentColor,
                    focusRequester = focusRequester,
                    modifier       = Modifier.weight(1f),
                )

                BarcodeScanButton(
                    accentColor = accentColor,
                    onClick = {
                        coroutineScope.launch {
                            val activity = context as? android.app.Activity ?: return@launch
                            val code = try {
                                com.example.foodtracker.util.BarcodeScanner.scan(activity)
                            } catch (_: Throwable) { null }
                            if (code != null) {
                                searchActive = true
                                viewModel.lookupBarcode(code)
                            }
                        }
                    },
                )
            }

            Box(Modifier.weight(1f)) {
                if (searchActive && (searchQuery.isNotBlank() || searchState !is FoodViewModel.SearchUiState.Idle)) {
                    SearchResultsView(
                        state       = searchState,
                        accentColor = accentColor,
                        onAdd       = { food -> sheetTarget = food },
                        onRetry     = { viewModel.searchFoods(searchQuery) },
                    )
                } else {
                    LoggedFoodsList(
                        loggedFoods = loggedFoods,
                        accentColor = accentColor,
                        onRemove    = { index -> viewModel.removeFoodFromMeal(mealName, index) },
                    )
                }
            }

            MacroSummaryFooter(
                totalCalories = totalCalories,
                totalProtein  = totalProtein,
                totalCarbs    = totalCarbs,
                totalFat      = totalFat,
                accentColor   = accentColor,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )

        sheetTarget?.let { food ->
            AddFoodSheet(
                target = food,
                accentColor = accentColor,
                confirmLabel = stringResource(R.string.meal_detail_add_to, mealName),
                onDismiss = { sheetTarget = null },
                onConfirm = { confirmedFood, grams ->
                    val newLogged = LoggedFood(confirmedFood, grams)
                    val newIndex = viewModel.addFoodToMealReturningIndex(mealName, newLogged)
                    sheetTarget = null
                    val scaled = scaleNutrition(confirmedFood, grams)
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.meal_detail_added, confirmedFood.name, grams, scaled.kcal),
                            actionLabel = context.getString(R.string.action_cancel),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.removeFoodFromMeal(mealName, newIndex)
                        }
                    }
                },
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
                contentDescription = stringResource(R.string.action_back),
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
                text = stringResource(mealLabelRes(mealName)),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GlassColors.textPrimary
            )
            Text(
                text = stringResource(R.string.home_tap_to_add),
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
                        stringResource(R.string.meal_detail_search_hint),
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
                contentDescription = stringResource(R.string.meal_detail_clear),
                tint = GlassColors.textTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onClear() }
            )
        }
    }
}

// ── Barcode scan button ───────────────────────────────────────────────────────

@Composable
fun BarcodeScanButton(accentColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GlassColors.cardBackground)
            .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = stringResource(R.string.meal_detail_scan),
            tint = accentColor,
            modifier = Modifier.size(22.dp),
        )
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
                    stringResource(R.string.meal_detail_no_food),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassColors.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.meal_detail_search_above),
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
        ProductThumbnail(food = logged.food, size = 40.dp)

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
                contentDescription = stringResource(R.string.meal_detail_remove),
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

// ── Search results (state-aware) ──────────────────────────────────────────────

@Composable
fun SearchResultsView(
    state: FoodViewModel.SearchUiState,
    accentColor: Color,
    onAdd: (FoodItem) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        FoodViewModel.SearchUiState.Idle -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    stringResource(R.string.meal_detail_type_2_chars),
                    color = GlassColors.textTertiary,
                    fontSize = 14.sp,
                )
            }
        }
        FoodViewModel.SearchUiState.Loading -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        }
        FoodViewModel.SearchUiState.Empty -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Niciun produs găsit",
                    color = GlassColors.textSecondary,
                    fontSize = 14.sp,
                )
            }
        }
        is FoodViewModel.SearchUiState.Error -> {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = Color(0xFFFF6B6B), fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .clickable { onRetry() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.action_retry),
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        is FoodViewModel.SearchUiState.Results -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.barcode.ifEmpty { item.name } }) { _, food ->
                    SearchResultRow(food = food, onAdd = { onAdd(food) })
                }
            }
        }
    }
}

// ── Search result row ─────────────────────────────────────────────────────────

@Composable
fun SearchResultRow(food: FoodItem, onAdd: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.97f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "rowScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(GlassColors.cardBackground)
            .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductThumbnail(food = food, size = 36.dp)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = food.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = GlassColors.textPrimary,
                maxLines = 1,
            )
            val subtitlePrefix = if (food.brand.isNullOrBlank()) "" else "${food.brand} · "
            Text(
                text = "${subtitlePrefix}per 100g · ${food.per100g} kcal · P${food.protein100g.toInt()} C${food.carbs100g.toInt()} F${food.fat100g.toInt()}",
                fontSize = 11.sp,
                color = GlassColors.textTertiary,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(GlassColors.accentGreen, GlassColors.accentGreenDim))
                )
                .clickable(interactionSource = interactionSource, indication = null) { onAdd() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.action_add),
                tint = Color.Black,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Product thumbnail (AsyncImage with emoji fallback) ────────────────────────

@Composable
fun ProductThumbnail(food: FoodItem, size: androidx.compose.ui.unit.Dp) {
    val emojiFallback = CategoryEmojiMapper.emojiFor(food.categories)
    if (food.imageUrl.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(GlassColors.cardBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(emojiFallback, fontSize = (size.value * 0.55f).sp)
        }
    } else {
        coil.compose.SubcomposeAsyncImage(
            model = food.imageUrl,
            contentDescription = food.name,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            loading = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(GlassColors.cardBackground),
                    Alignment.Center,
                ) {
                    Text(emojiFallback, fontSize = (size.value * 0.55f).sp)
                }
            },
            error = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(GlassColors.cardBackground),
                    Alignment.Center,
                ) {
                    Text(emojiFallback, fontSize = (size.value * 0.55f).sp)
                }
            },
        )
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
                stringResource(R.string.meal_detail_total),
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
                label   = stringResource(R.string.macro_protein),
                value   = totalProtein,
                goal    = 150f,
                color   = GlassColors.proteinColor,
                unit    = "g",
                modifier = Modifier.weight(1f)
            )
            FooterMacroBar(
                label   = stringResource(R.string.macro_carbs),
                value   = totalCarbs,
                goal    = 250f,
                color   = GlassColors.carbsColor,
                unit    = "g",
                modifier = Modifier.weight(1f)
            )
            FooterMacroBar(
                label   = stringResource(R.string.macro_fat),
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
                stringResource(R.string.meal_detail_save_meal),
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
