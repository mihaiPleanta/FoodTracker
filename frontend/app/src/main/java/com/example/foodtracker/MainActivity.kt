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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.example.foodtracker.data.journal.JournalProvider
import com.example.foodtracker.model.NutritionGoals
import com.example.foodtracker.model.StatsGoalsDto
import com.example.foodtracker.sync.WorkManagerSyncScheduler
import com.example.foodtracker.sync.cancelJournalSync
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.foodtracker.ui.customfood.CustomFoodMode
import com.example.foodtracker.ui.customfood.CustomFoodScreen
import com.example.foodtracker.ui.customfood.MyFoodsScreen
import com.example.foodtracker.ui.*
import com.example.foodtracker.ui.theme.FoodTrackerTheme
import com.example.foodtracker.ui.theme.GlassColors
import com.example.foodtracker.api.DaysApi
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.data.DataStoreSettingsRepository
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.data.MealLogTracker
import com.example.foodtracker.data.LocalDataWiper
import com.example.foodtracker.data.LogoutCoordinator
import com.example.foodtracker.data.ProfileSnapshotStore
import com.example.foodtracker.data.profileSnapshotDataStore
import com.example.foodtracker.data.StreakSnapshotStore
import com.example.foodtracker.data.streakSnapshotDataStore
import com.example.foodtracker.data.cache.FoodCacheDb
import com.example.foodtracker.data.cache.RoomFoodCache
import com.example.foodtracker.data.mealLogTrackerDataStore
import com.example.foodtracker.data.settingsDataStore
import com.example.foodtracker.util.notifications.MealReminderScheduler
import com.example.foodtracker.util.notifications.desiredSchedule
import com.example.foodtracker.util.notifications.MealReminderWorker
import com.example.foodtracker.util.notifications.DayCompleteReminderScheduler
import com.example.foodtracker.util.notifications.DayCompleteReminderWorker
import com.example.foodtracker.util.notifications.StreakReminderScheduler
import com.example.foodtracker.util.notifications.desiredDayCompleteMinute
import com.example.foodtracker.util.notifications.desiredStreakMinute
import com.example.foodtracker.util.notifications.NotificationChannels
import com.example.foodtracker.util.AppLocale
import com.example.foodtracker.viewmodel.AuthViewModel
import com.example.foodtracker.viewmodel.CopyViewModel
import com.example.foodtracker.viewmodel.FoodViewModel
import com.example.foodtracker.viewmodel.CustomFoodViewModel
import com.example.foodtracker.viewmodel.CustomFoodViewModelFactory
import com.example.foodtracker.viewmodel.FoodViewModelFactory
import com.example.foodtracker.viewmodel.PortionsViewModel
import com.example.foodtracker.viewmodel.QuickPicksViewModel
import com.example.foodtracker.viewmodel.RecipeViewModel
import com.example.foodtracker.viewmodel.StatsSource
import com.example.foodtracker.viewmodel.StatsViewModel
import com.example.foodtracker.viewmodel.StreakViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var pendingDeepLink by mutableStateOf<DeepLink?>(null)
    private var pendingOpenToday by mutableStateOf(false)

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
        pendingOpenToday = intent?.getBooleanExtra(DayCompleteReminderWorker.EXTRA_OPEN_TODAY, false) == true
        setContent {
            val foodRepository = remember {
                FoodRepository(
                    api = RetrofitInstance.api,
                    cache = RoomFoodCache(FoodCacheDb.get(applicationContext).foodCacheDao()),
                )
            }
            val profileSnapshotStore = remember { ProfileSnapshotStore(applicationContext.profileSnapshotDataStore) }
            val logoutCoordinator = remember {
                val journal = JournalProvider.repository(applicationContext)
                val processor = JournalProvider.processor(applicationContext)
                LogoutCoordinator(
                    sync = processor::syncNow,
                    pendingCount = { journal.pendingCount.first() },
                    clearLocal = LocalDataWiper(
                        listOf(
                            { foodRepository.clearAll() },
                            { profileSnapshotStore.clear() },
                            {
                                runCatching { cancelJournalSync(applicationContext) }
                            },
                            { journal.clearAll() },
                        )
                    )::wipe,
                )
            }
            val foodViewModelFactory = remember {
                FoodViewModelFactory(
                    settingsRepository = DataStoreSettingsRepository(applicationContext.settingsDataStore),
                    mealLogTracker = MealLogTracker(applicationContext.mealLogTrackerDataStore),
                    foodRepository = foodRepository,
                    journal = JournalProvider.repository(applicationContext),
                    profileSnapshots = profileSnapshotStore,
                )
            }
            val viewModel: FoodViewModel = viewModel(factory = foodViewModelFactory)
            val authViewModel: AuthViewModel = viewModel(
                factory = remember {
                    object : androidx.lifecycle.ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                            AuthViewModel(
                                snapshots = profileSnapshotStore,
                                bindJournal = { uid ->
                                    JournalProvider.repository(applicationContext).resetIfOwnedByOther(uid) {
                                        runCatching { cancelJournalSync(applicationContext) }
                                    }
                                },
                                onHome = {
                                    JournalProvider.repository(applicationContext).refreshWindow()
                                },
                                cancelSync = { cancelJournalSync(applicationContext) },
                            ) as T
                    }
                }
            )
            // Activity-scoped so logout can reset it (see resetUserState); otherwise the
            // bottom bar's saveState/restoreState keeps the previous account's recipes.
            val recipeViewModel: RecipeViewModel = viewModel()
            // Activity-scoped like recipeViewModel; reset on logout so lists never leak between accounts.
            val quickPicksViewModel: QuickPicksViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { QuickPicksViewModel(journal = JournalProvider.repository(applicationContext)) }
                }
            )
            val portionsViewModel: PortionsViewModel = viewModel()
            val copyViewModel: CopyViewModel = viewModel(
                factory = remember {
                    viewModelFactory {
                        initializer { CopyViewModel(journal = JournalProvider.repository(applicationContext)) }
                    }
                },
            )
            val statsDaysApi = remember { RetrofitInstance.retrofit.create(DaysApi::class.java) }
            val statsViewModel: StatsViewModel = viewModel(
                factory = remember {
                    viewModelFactory {
                        initializer {
                            val journal = JournalProvider.repository(applicationContext)
                            StatsViewModel(source = StatsSource { from, to ->
                                // Local mirror first (offline); the server covers ranges the mirror doesn't hold.
                                journal.statsDays(from, to, viewModel.nutritionGoals.value?.toProvisionalStatsGoals())
                                    ?: statsDaysApi.getStatsDays(from.toString(), to.toString())
                            })
                        }
                    }
                }
            )
            val streakViewModel: StreakViewModel = viewModel(
                factory = remember {
                    viewModelFactory {
                        initializer {
                            StreakViewModel(snapshots = StreakSnapshotStore(applicationContext.streakSnapshotDataStore))
                        }
                    }
                }
            )
            val customFoodViewModel: CustomFoodViewModel = viewModel(
                factory = remember {
                    CustomFoodViewModelFactory(
                        defaultServingUnit = getString(R.string.custom_food_default_serving_unit),
                        invalidate = foodRepository::invalidateCustomFoods,
                    )
                }
            )
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
                    loadedProfile?.let { viewModel.applyLoadedProfile(it) }
                }

                LaunchedEffect(Unit) {
                    authViewModel.profileUpdates.collect {
                        viewModel.loadGoals()
                    }
                }

                // Sync meal reminders whenever the desired schedule (master switch + per-meal
                // time/toggle) changes; also fires once on start to apply anything left pending.
                LaunchedEffect(Unit) {
                    viewModel.persistedSettings
                        .distinctUntilChanged { old, new -> desiredSchedule(old) == desiredSchedule(new) }
                        .collect { settings ->
                            MealReminderScheduler.sync(applicationContext, settings)
                        }
                }

                // Evening "done for today?" reminder — separate effect, same diff-based approach.
                LaunchedEffect(Unit) {
                    viewModel.persistedSettings
                        .distinctUntilChanged { a, b -> desiredDayCompleteMinute(a) == desiredDayCompleteMinute(b) }
                        .collect { settings ->
                            DayCompleteReminderScheduler.sync(applicationContext, settings)
                        }
                }

                // Evening streak reminder — its own effect, scheduler and store key.
                LaunchedEffect(Unit) {
                    viewModel.persistedSettings
                        .distinctUntilChanged { a, b -> desiredStreakMinute(a) == desiredStreakMinute(b) }
                        .collect { settings ->
                            StreakReminderScheduler.sync(applicationContext, settings)
                        }
                }

                // Streak: refresh on resume and after every food log change.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            streakViewModel.refresh()
                            // Push the offline queue and refresh the local mirror (best-effort).
                            runCatching { WorkManagerSyncScheduler(applicationContext).requestSync(replace = true) }
                            lifecycleOwner.lifecycleScope.launch {
                                runCatching { JournalProvider.repository(applicationContext).refreshWindow() }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(Unit) { viewModel.foodLogsChanged.collect { streakViewModel.refresh(); statsViewModel.invalidate() } }

                // Keep period stats in sync with day status changes, tolerance and hydration goal.
                LaunchedEffect(Unit) { viewModel.dayStatusChanged.collect { statsViewModel.invalidate() } }
                LaunchedEffect(Unit) {
                    viewModel.appSettings.map { it.inTargetTolerancePct }.distinctUntilChanged()
                        .collect { statsViewModel.setTolerance(it) }
                }
                LaunchedEffect(Unit) {
                    viewModel.hydrationGoalLiters.collect { statsViewModel.setHydrationGoal(it) }
                }

                // Open-today deep link (day-complete reminder): Home on today's date.
                LaunchedEffect(pendingOpenToday, currentRoute) {
                    if (!pendingOpenToday) return@LaunchedEffect
                    if (currentRoute !in setOf("home", "stats", "profile")) return@LaunchedEffect
                    viewModel.setSelectedHomeDate(java.util.Date())
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                    pendingOpenToday = false
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
                                    HomeScreen(navController, viewModel, portionsViewModel, copyViewModel, streakViewModel)
                                }
                                composable("stats") {
                                    StatsScreen(viewModel, statsViewModel)
                                }
                                composable("meals") {
                                    MealsScreen(foodViewModel = viewModel, viewModel = recipeViewModel)
                                }
                                composable("profile") {
                                    ProfileScreen(
                                        viewModel, authViewModel, customFoodViewModel,
                                        onOpenMyFoods = { navController.navigate("my-foods") },
                                    )
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        navController = navController,
                                        viewModel = viewModel,
                                        authViewModel = authViewModel,
                                        logoutCoordinator = logoutCoordinator,
                                        onLogout = {
                                            authViewModel.logout()
                                            viewModel.resetUserState()
                                            recipeViewModel.resetUserState()
                                            quickPicksViewModel.resetUserState()
                                            portionsViewModel.resetUserState()
                                            copyViewModel.resetUserState()
                                            statsViewModel.resetUserState()
                                            streakViewModel.resetUserState()
                                            customFoodViewModel.resetUserState()
                                            // Wipe before leaving, so a fast re-login can't race the clear.
                                            lifecycleScope.launch {
                                                withContext(NonCancellable) { logoutCoordinator.clearLocal() }
                                                navController.navigate("login") {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            }
                                        },
                                        onAccountDeleted = {
                                            viewModel.resetUserState()
                                            recipeViewModel.resetUserState()
                                            quickPicksViewModel.resetUserState()
                                            portionsViewModel.resetUserState()
                                            copyViewModel.resetUserState()
                                            statsViewModel.resetUserState()
                                            streakViewModel.resetUserState()
                                            customFoodViewModel.resetUserState()
                                            // Wipe before leaving, so a fast re-login can't race the clear.
                                            lifecycleScope.launch {
                                                withContext(NonCancellable) { logoutCoordinator.clearLocal() }
                                                navController.navigate("login") {
                                                    popUpTo(0) { inclusive = true }
                                                }
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
                                        viewModel     = viewModel,
                                        quickPicks    = quickPicksViewModel,
                                        portions      = portionsViewModel,
                                        copy          = copyViewModel,
                                        customFoods   = customFoodViewModel,
                                    )
                                }
                                composable("my-foods") {
                                    MyFoodsScreen(
                                        vm = customFoodViewModel,
                                        onBack = { navController.popBackStack() },
                                        onCreate = { navController.navigate("custom-food/new?forMeal=false") },
                                        onEdit = { id -> navController.navigate("custom-food/edit/$id") },
                                    )
                                }
                                composable(
                                    "custom-food/new?barcode={barcode}&name={name}&forMeal={forMeal}",
                                    arguments = listOf(
                                        navArgument("barcode") { type = NavType.StringType; nullable = true; defaultValue = null },
                                        navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null },
                                        navArgument("forMeal") { type = NavType.BoolType; defaultValue = false },
                                    ),
                                ) { entry ->
                                    val args = entry.arguments
                                    CustomFoodScreen(
                                        vm = customFoodViewModel,
                                        mode = CustomFoodMode.New(
                                            barcode = args?.getString("barcode"),
                                            name = args?.getString("name"),
                                            forMeal = args?.getBoolean("forMeal") ?: false,
                                        ),
                                        onDone = { navController.popBackStack() },
                                        onOpenExisting = { id ->
                                            navController.navigate("custom-food/edit/$id") {
                                                popUpTo(entry.destination.id) { inclusive = true }
                                            }
                                        },
                                    )
                                }
                                composable(
                                    "custom-food/edit/{id}",
                                    arguments = listOf(navArgument("id") { type = NavType.IntType }),
                                ) { entry ->
                                    CustomFoodScreen(
                                        vm = customFoodViewModel,
                                        mode = CustomFoodMode.Edit(entry.arguments?.getInt("id") ?: -1),
                                        onDone = { navController.popBackStack() },
                                        onOpenExisting = { id ->
                                            navController.navigate("custom-food/edit/$id") {
                                                popUpTo(entry.destination.id) { inclusive = true }
                                            }
                                        },
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
        pendingOpenToday = intent.getBooleanExtra(DayCompleteReminderWorker.EXTRA_OPEN_TODAY, false)
    }

    private fun readDeepLinkFromIntent(intent: Intent?): DeepLink? {
        intent ?: return null
        val name = intent.getStringExtra(MealReminderWorker.EXTRA_MEAL_NAME) ?: return null
        val icon = intent.getStringExtra(MealReminderWorker.EXTRA_MEAL_ICON) ?: return null
        val accent = intent.getStringExtra(MealReminderWorker.EXTRA_MEAL_ACCENT) ?: return null
        return DeepLink(name, icon, accent)
    }
}

/** Current goals as a stats snapshot for complete days that have none yet; null while the TDEE is unknown. */
private fun NutritionGoals.toProvisionalStatsGoals(): StatsGoalsDto? =
    tdee?.let { StatsGoalsDto(calorieGoal, proteinGoal, carbsGoal, fatGoal, it) }
