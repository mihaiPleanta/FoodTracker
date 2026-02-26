package com.example.foodtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.foodtracker.model.MealType
import com.example.foodtracker.viewmodel.FoodViewModel


@Composable
fun DailySummaryScreen(
    navController: NavController,
    viewModel: FoodViewModel
) {
    val foods by viewModel.foods.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate("addFood")
            }) {
                Text("+")
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(padding)
        ) {

            Text("Daily Summary", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Calories: ${viewModel.getTotalCalories()}")
            Text("Protein: ${viewModel.getTotalProtein()} g")
            Text("Carbs: ${viewModel.getTotalCarbs()} g")
            Text("Fat: ${viewModel.getTotalFat()} g")

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn {
                items(MealType.values().size) { index ->
                    val meal = MealType.values()[index]
                    Text(
                        text = meal.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    viewModel.getFoodsByMeal(meal).forEach {
                        Text("- ${it.name} (${it.calories} cal)")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}