package com.example.foodtracker.model

/** Maps the UI/navigation meal name ("Breakfast") to the backend enum ("BREAKFAST"). */
fun mealKeyToBackend(meal: String): String = when (meal) {
    "Breakfast" -> "BREAKFAST"
    "Lunch"     -> "LUNCH"
    "Dinner"    -> "DINNER"
    "Snacks"    -> "SNACKS"
    else        -> "BREAKFAST"
}

/** Inverse of [mealKeyToBackend]: backend enum ("DINNER") to UI name ("Dinner"); unknown -> "Snacks". */
fun mealNameFromBackend(meal: String): String = when (meal) {
    "BREAKFAST" -> "Breakfast"
    "LUNCH"     -> "Lunch"
    "DINNER"    -> "Dinner"
    else        -> "Snacks"
}

val MEAL_ORDER = listOf("Breakfast", "Lunch", "Dinner", "Snacks")
