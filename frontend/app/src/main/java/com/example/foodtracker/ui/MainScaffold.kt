package com.example.foodtracker.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.foodtracker.ui.theme.GlassColors
import kotlinx.coroutines.delay

// ── Nav item model ────────────────────────────────────────────────────────────

data class NavItem(
    val route: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
)

val navItems = listOf(
    NavItem("home",    "Home",    Icons.Filled.Home,        Icons.Outlined.Home),
    NavItem("stats",   "Stats",   Icons.Rounded.BarChart,   Icons.Rounded.BarChart),
    NavItem("meals",   "Meals",   Icons.Rounded.Restaurant, Icons.Rounded.Restaurant),
    NavItem("profile", "Profile", Icons.Filled.Person,      Icons.Outlined.Person)
)

// Routes that are actually implemented
val enabledRoutes = setOf("home")

// ── Bottom bar ────────────────────────────────────────────────────────────────

@Composable
fun AppBottomBar(
    navController: NavController,
    onFabClick: () -> Unit,
    fabIsOpen: Boolean = false
) {
    val backStack    by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // ── Bar pill ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(GlassColors.cardBackground)
                .border(1.dp, GlassColors.cardBorder, RoundedCornerShape(32.dp))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left two items
            navItems.take(2).forEach { item ->
                val enabled = item.route in enabledRoutes
                NavBarItem(
                    item = item,
                    isSelected = currentRoute == item.route,
                    enabled = enabled,
                    onClick = {
                        if (enabled) navController.navigate(item.route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }

            // Centre FAB spacer
            Spacer(Modifier.width(64.dp))

            // Right two items
            navItems.drop(2).forEach { item ->
                val enabled = item.route in enabledRoutes
                NavBarItem(
                    item = item,
                    isSelected = currentRoute == item.route,
                    enabled = enabled,
                    onClick = {
                        if (enabled) navController.navigate(item.route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
        }

        // ── Floating FAB (centred, elevated above bar) ────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-28).dp),
            contentAlignment = Alignment.Center
        ) {
            FabButton(onClick = onFabClick, isOpen = fabIsOpen)
        }
    }
}

// ── FAB ───────────────────────────────────────────────────────────────────────

@Composable
fun FabButton(onClick: () -> Unit, isOpen: Boolean = false) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "fabRotation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "fabScale"
    )

    // Pulse glow when closed
    val glowAlpha by animateFloatAsState(
        targetValue = if (isOpen) 0f else 0.5f,
        animationSpec = tween(400),
        label = "fabGlow"
    )

    Box(
        modifier = Modifier
            .size(60.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(GlassColors.accentGreen, GlassColors.accentGreenDim)
                )
            )
            .border(
                width = 3.dp,
                brush = Brush.linearGradient(
                    listOf(
                        GlassColors.accentGreen.copy(alpha = glowAlpha),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Add food",
            tint = Color.Black,
            modifier = Modifier
                .size(28.dp)
                .rotate(rotation)
        )
    }
}

// ── Individual nav item ───────────────────────────────────────────────────────

@Composable
fun NavBarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "indicator"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconScale"
    )

    // Disabled items are visually dimmed
    val alpha = if (enabled) 1f else 0.35f

    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) item.iconSelected else item.iconUnselected,
            contentDescription = item.label,
            tint = if (isSelected) GlassColors.accentGreen
                   else GlassColors.textTertiary.copy(alpha = alpha),
            modifier = Modifier.size(22.dp).scale(iconScale)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) GlassColors.accentGreen
                    else GlassColors.textTertiary.copy(alpha = alpha),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GlassColors.accentGreen)
        )
    }
}

// ── Meal selector pop-up overlay ──────────────────────────────────────────────

data class MealOption(
    val name: String,
    val icon: String,
    val timeRange: String,
    val accentColor: Color
)

val mealOptions = listOf(
    MealOption("Breakfast", "🌅", "7:00 – 10:00",  Color(0xFFFFD600)),
    MealOption("Lunch",     "☀️", "12:00 – 14:00", Color(0xFF00E676)),
    MealOption("Dinner",    "🌙", "18:00 – 21:00", Color(0xFF448AFF)),
    MealOption("Snacks",    "🍎", "Anytime",        Color(0xFFFF6D00))
)

@Composable
fun MealSelectorOverlay(
    onDismiss: () -> Unit,
    onMealSelected: (MealOption) -> Unit
) {
    // Backdrop fades in quickly
    val backdropAlpha by animateFloatAsState(
        targetValue = 0.72f,
        animationSpec = tween(durationMillis = 180),
        label = "backdrop"
    )

    // Sheet slides up from a small offset with a gentle spring — no bounce
    val sheetTranslation by animateFloatAsState(
        targetValue = 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "sheetTranslation"
    )

    // Content (title + cards) fades in as one unit, slightly after sheet starts
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60); contentVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = sheetTranslation }
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(GlassColors.backgroundSurface)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(GlassColors.cardBorder, Color.Transparent)
                    ),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                )
                .pointerInput(Unit) { detectTapGestures { /* consume */ } }
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 40.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GlassColors.cardBorder)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(22.dp))

            // Title + cards appear together as one fade+slide unit
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(200)) +
                        slideInVertically(tween(220, easing = FastOutSlowInEasing)) { 24 }
            ) {
                Column {
                    Text(
                        text = "Add Food",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GlassColors.textPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Select a meal to log into",
                        fontSize = 13.sp,
                        color = GlassColors.textSecondary
                    )
                    Spacer(Modifier.height(24.dp))

                    // 2×2 grid — all cards appear simultaneously
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        mealOptions.chunked(2).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { meal ->
                                    MealOptionCard(
                                        meal = meal,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onMealSelected(meal) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Meal option card ──────────────────────────────────────────────────────────

@Composable
fun MealOptionCard(
    meal: MealOption,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessHigh
        ),
        label = "cardScale"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 0.30f,
        animationSpec = tween(150),
        label = "borderAlpha"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        meal.accentColor.copy(alpha = 0.12f),
                        GlassColors.cardBackground
                    )
                )
            )
            .border(1.dp, meal.accentColor.copy(alpha = borderAlpha), RoundedCornerShape(20.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(18.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(meal.accentColor.copy(alpha = 0.15f))
                    .border(1.dp, meal.accentColor.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(meal.icon, fontSize = 26.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = meal.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GlassColors.textPrimary
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = meal.timeRange,
                fontSize = 11.sp,
                color = GlassColors.textTertiary
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(meal.accentColor)
            )
        }
    }
}

