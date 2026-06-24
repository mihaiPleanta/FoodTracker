package com.example.foodtracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.foodtracker.ui.*
import com.example.foodtracker.ui.theme.FoodTrackerTheme
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.data.DataStoreSettingsRepository
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.MealLogTracker
import com.example.foodtracker.data.cache.FoodCacheDb
import com.example.foodtracker.data.cache.RoomFoodCache
import com.example.foodtracker.data.mealLogTrackerDataStore
import com.example.foodtracker.data.settingsDataStore
import com.example.foodtracker.util.notifications.MealReminderScheduler
import com.example.foodtracker.util.notifications.MealReminderWorker
import com.example.foodtracker.util.notifications.NotificationChannels
import com.example.foodtracker.util.AppLocale
import com.example.foodtracker.viewmodel.AuthViewModel
import com.example.foodtracker.viewmodel.FoodViewModel
import com.example.foodtracker.viewmodel.FoodViewModelFactory
import com.example.foodtracker.viewmodel.RecipeViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private var pendingDeepLink by mutableStateOf<DeepLink?>(null)

    data class DeepLink(
        val mealName: String,
        val mealIcon: String,
        val accentHex: String,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationChannels.ensureCreated(applicationContext)
        pendingDeepLink = readDeepLinkFromIntent(intent)
        setContent {
            val foodViewModelFactory = remember {
                FoodViewModelFactory(
                    settingsRepository = DataStoreSettingsRepository(applicationContext.settingsDataStore),
                    mealLogTracker = MealLogTracker(applicationContext.mealLogTrackerDataStore),
                    foodRepository = FoodRepository(
                        api = RetrofitInstance.api,
                        cache = RoomFoodCache(FoodCacheDb.get(applicationContext).foodCacheDao()),
                    ),
                )
            }
            val viewModel: FoodViewModel = viewModel(factory = foodViewModelFactory)
            val authViewModel: AuthViewModel = viewModel()
            // Activity-scoped so logout can reset it (see resetUserState); otherwise the
            // bottom bar's saveState/restoreState keeps the previous account's recipes.
            val recipeViewModel: RecipeViewModel = viewModel()
            val appSettings by viewModel.appSettings.collectAsState()
            // enableEdgeToEdge()'s auto() follows the *system* dark mode; keep the
            // status/nav bar icon contrast in sync with the in-app theme toggle.
            LaunchedEffect(appSettings.isDarkTheme) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !appSettings.isDarkTheme
                    isAppearanceLightNavigationBars = !appSettings.isDarkTheme
                }
            }
            FoodTrackerTheme(isDark = appSettings.isDarkTheme) {
                AppLocale(language = appSettings.language) {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                val showMealSelector by viewModel.showMealSelector.collectAsState()

                val loadedProfile by authViewModel.loadedProfile.collectAsState()
                LaunchedEffect(loadedProfile) {
                    loadedProfile?.let { viewModel.updateUserProfile(it) }
                }

                LaunchedEffect(Unit) {
                    authViewModel.profileUpdates.collect {
                        viewModel.loadGoals()
                    }
                }

                // Reschedule/cancel meal reminders whenever notificationsEnabled changes
                // (also fires once on initial collect with the persisted value).
                LaunchedEffect(Unit) {
                    viewModel.appSettings
                        .map { it.notificationsEnabled }
                        .distinctUntilChanged()
                        .collect { enabled ->
                            if (enabled) {
                                MealReminderScheduler.scheduleAll(applicationContext)
                            } else {
                                MealReminderScheduler.cancelAll(applicationContext)
                            }
                        }
                }

                // Consume notification deep-link — only after we reach a logged-in route.
                // On cold start the NavHost begins at "splash"; we wait for "home"/"stats"/"profile".
                LaunchedEffect(pendingDeepLink, currentRoute) {
                    val dl = pendingDeepLink ?: return@LaunchedEffect
                    if (currentRoute !in setOf("home", "stats", "profile")) return@LaunchedEffect
                    navController.navigate("meal/${dl.mealName}/${dl.mealIcon}/${dl.accentHex}") {
                        launchSingleTop = true
                    }
                    pendingDeepLink = null
                }

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
                                startDestination = "splash"
                            ) {
                                composable("splash") {
                                    SplashScreen(
                                        authViewModel     = authViewModel,
                                        onNavigateLogin   = {
                                            navController.navigate("login") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        },
                                        onNavigateHome    = {
                                            navController.navigate("home") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        },
                                        onNavigateOnboarding = {
                                            navController.navigate("onboarding") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        },
                                        onNavigateVerifyEmail = {
                                            navController.navigate("verify-email") {
                                                popUpTo("splash") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                                composable("login") {
                                    LoginScreen(
                                        authViewModel        = authViewModel,
                                        onNavigateRegister   = { navController.navigate("register") },
                                        onNavigateForgot     = { navController.navigate("forgot-password") },
                                        onNavigateHome       = {
                                            navController.navigate("home") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        },
                                        onNavigateOnboarding = {
                                            navController.navigate("onboarding") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        },
                                        onNavigateVerifyEmail = {
                                            navController.navigate("verify-email") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                                composable("register") {
                                    RegisterScreen(
                                        authViewModel         = authViewModel,
                                        onNavigateLogin       = { navController.popBackStack() },
                                        onNavigateVerifyEmail = {
                                            navController.navigate("verify-email") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                                composable("verify-email") {
                                    VerifyEmailScreen(
                                        authViewModel     = authViewModel,
                                        onNavigateHome    = {
                                            navController.navigate("home") {
                                                popUpTo("verify-email") { inclusive = true }
                                            }
                                        },
                                        onNavigateOnboarding = {
                                            navController.navigate("onboarding") {
                                                popUpTo("verify-email") { inclusive = true }
                                            }
                                        },
                                        onLoggedOut       = {
                                            navController.navigate("login") {
                                                popUpTo("verify-email") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                                composable("forgot-password") {
                                    ForgotPasswordScreen(
                                        authViewModel = authViewModel,
                                        onBack        = { navController.popBackStack() }
                                    )
                                }
                                composable("onboarding") {
                                    OnboardingScreen(
                                        authViewModel  = authViewModel,
                                        onNavigateHome = {
                                            navController.navigate("home") {
                                                popUpTo("onboarding") { inclusive = true }
                                            }
                                        }
                                    )
                                }
                                composable("home") {
                                    HomeScreen(navController, viewModel)
                                }
                                composable("stats") {
                                    StatsScreen(viewModel)
                                }
                                composable("meals") {
                                    MealsScreen(foodViewModel = viewModel, viewModel = recipeViewModel)
                                }
                                composable("profile") {
                                    ProfileScreen(viewModel, authViewModel)
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        navController = navController,
                                        viewModel = viewModel,
                                        authViewModel = authViewModel,
                                        onLogout = {
                                            authViewModel.logout()
                                            viewModel.resetUserState()
                                            recipeViewModel.resetUserState()
                                            navController.navigate("login") {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        },
                                        onAccountDeleted = {
                                            viewModel.resetUserState()
                                            recipeViewModel.resetUserState()
                                            navController.navigate("login") {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    )
                                }
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
                                val colorHex    = String.format("%06X", meal.accentColor.toArgb() and 0xFFFFFF)
                                navController.navigate("meal/${meal.name}/$encodedIcon/$colorHex")
                            }
                        )
                    }
                }
                } // end AppLocale
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = readDeepLinkFromIntent(intent)
    }

    private fun readDeepLinkFromIntent(intent: Intent?): DeepLink? {
        intent ?: return null
        val name = intent.getStringExtra(MealReminderWorker.EXTRA_MEAL_NAME) ?: return null
        val icon = intent.getStringExtra(MealReminderWorker.EXTRA_MEAL_ICON) ?: return null
        val accent = intent.getStringExtra(MealReminderWorker.EXTRA_MEAL_ACCENT) ?: return null
        return DeepLink(name, icon, accent)
    }
}
