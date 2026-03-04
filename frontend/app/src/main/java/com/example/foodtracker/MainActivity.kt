package com.example.foodtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.foodtracker.ui.*
import com.example.foodtracker.ui.theme.FoodTrackerTheme
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.viewmodel.FoodViewModel
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoodTrackerTheme {
                val navController = rememberNavController()
                val viewModel: FoodViewModel = viewModel()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                // Pop-up state lives in ViewModel so back-nav can reopen it
                val showMealSelector by viewModel.showMealSelector.collectAsState()

                val showBottomBar = currentRoute in listOf("home", "stats", "meals", "profile")

                Box(Modifier.fillMaxSize().background(GlassColors.backgroundDark)) {
                    Scaffold(
                        containerColor = GlassColors.backgroundDark,
                        bottomBar = {
                            if (showBottomBar) {
                                AppBottomBar(
                                    navController = navController,
                                    onFabClick    = { viewModel.toggleMealSelector() },
                                    fabIsOpen     = showMealSelector
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(GlassColors.backgroundDark)
                                .padding(bottom = innerPadding.calculateBottomPadding())
                        ) {
                            NavHost(
                                navController    = navController,
                                startDestination = "home"
                            ) {
                                composable("home") {
                                    HomeScreen(navController, viewModel)
                                }
                                composable("stats") {
                                    PlaceholderScreen("Stats")
                                }
                                composable("meals") {
                                    PlaceholderScreen("Meals")
                                }
                                composable("profile") {
                                    PlaceholderScreen("Profile")
                                }
                                // ── Meal detail ───────────────────────────────
                                composable("meal/{mealName}/{mealIcon}/{accentColor}") { entry ->
                                    val mealName    = entry.arguments?.getString("mealName") ?: ""
                                    val mealIcon    = URLDecoder.decode(entry.arguments?.getString("mealIcon") ?: "", "UTF-8")
                                    val colorHex    = entry.arguments?.getString("accentColor") ?: "FFD600"
                                    val accentColor = Color(android.graphics.Color.parseColor("#$colorHex"))
                                    MealDetailScreen(
                                        mealName      = mealName,
                                        mealIcon      = mealIcon,
                                        accentColor   = accentColor,
                                        navController = navController,
                                        viewModel     = viewModel
                                    )
                                }
                            }
                        }
                    }

                    // ── Meal selector overlay (on top of everything) ──────────
                    AnimatedVisibility(
                        visible = showMealSelector,
                        enter   = fadeIn(tween(200)),
                        exit    = fadeOut(tween(200))
                    ) {
                        MealSelectorOverlay(
                            onDismiss = { viewModel.closeMealSelector() },
                            onMealSelected = { meal ->
                                viewModel.closeMealSelector()
                                val encodedIcon = URLEncoder.encode(meal.icon, "UTF-8")
                                val colorHex    = meal.accentColor.value.toString(16).uppercase().takeLast(6)
                                navController.navigate("meal/${meal.name}/$encodedIcon/$colorHex")
                            }
                        )
                    }
                }
            }
        }
    }
}
