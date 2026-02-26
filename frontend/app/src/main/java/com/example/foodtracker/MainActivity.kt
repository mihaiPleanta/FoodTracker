package com.example.foodtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.foodtracker.ui.AddFoodScreen
import com.example.foodtracker.ui.DailySummaryScreen
import com.example.foodtracker.ui.theme.FoodTrackerTheme
import com.example.foodtracker.viewmodel.FoodViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoodTrackerTheme {

                val navController = rememberNavController()
                val viewModel: FoodViewModel = viewModel()

                NavHost(
                    navController = navController,
                    startDestination = "summary"
                ) {
                    composable("summary") {
                        DailySummaryScreen(navController, viewModel)
                    }
                    composable("addFood") {
                        AddFoodScreen(navController, viewModel)
                    }
                }
            }
        }
    }
}