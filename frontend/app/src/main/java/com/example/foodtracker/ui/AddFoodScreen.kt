package com.example.foodtracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.foodtracker.model.Food
import com.example.foodtracker.model.MealType
import com.example.foodtracker.viewmodel.FoodViewModel

@Composable
fun AddFoodScreen(
    navController: NavController,
    viewModel: FoodViewModel
) {

    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var selectedMeal by remember { mutableStateOf(MealType.BREAKFAST) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text("Add Food", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        MealType.values().forEach { meal ->
            Row {
                RadioButton(
                    selected = selectedMeal == meal,
                    onClick = { selectedMeal = meal }
                )
                Text(meal.name)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        OutlinedTextField(value = calories, onValueChange = { calories = it }, label = { Text("Calories") })
        OutlinedTextField(value = protein, onValueChange = { protein = it }, label = { Text("Protein") })
        OutlinedTextField(value = carbs, onValueChange = { carbs = it }, label = { Text("Carbs") })
        OutlinedTextField(value = fat, onValueChange = { fat = it }, label = { Text("Fat") })

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            viewModel.addFood(
                Food(
                    name,
                    calories.toInt(),
                    protein.toInt(),
                    carbs.toInt(),
                    fat.toInt(),
                    selectedMeal
                )
            )
            navController.popBackStack()
        }) {
            Text("Save")
        }
    }
}